package com.project.airBnbApp.service;

import com.project.airBnbApp.dto.HotelSearchCacheData;
import com.project.airBnbApp.dto.HotelSearchRequest;
import com.project.airBnbApp.dto.HotelPriceDto;
import com.project.airBnbApp.dto.InventoryDto;
import com.project.airBnbApp.dto.UpdateInventoryRequestDto;
import com.project.airBnbApp.entity.Inventory;
import com.project.airBnbApp.entity.Room;
import com.project.airBnbApp.entity.User;
import com.project.airBnbApp.exception.ResourceNotFoundException;
import com.project.airBnbApp.repository.HotelMinPriceRepository;
import com.project.airBnbApp.repository.InventoryRepository;
import com.project.airBnbApp.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Objects;

import static com.project.airBnbApp.util.AppUtils.getCurrentUser;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService{

    private final InventoryRepository inventoryRepository;
    private final HotelMinPriceRepository hotelMinPriceRepository;
    private final RoomRepository roomRepository;
    private final ModelMapper modelMapper;
    private final CacheManager cacheManager;

    @Override
    public void initializeRoomForAYear(Room room) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusYears(1);
        for(LocalDate currentDate = today; !currentDate.isAfter(endDate); currentDate = currentDate.plusDays(1)){
            long daysAhead = ChronoUnit.DAYS.between(today, currentDate);
            BigDecimal surgeFactor = calculateSurgeFactor(daysAhead);
            
            Inventory inventory = Inventory.builder()
                            .hotel(room.getHotel())
                                .room(room)
                                    .bookedCount(0)
                                    .reservedCount(0)
                                            .city(room.getHotel().getCity())
                                                    .date(currentDate)
                                                            .price(room.getBasePrice())
                                                                    .surgeFactor(surgeFactor)
                                                                            .totalCount(room.getTotalCount())
                                                                                    .closed(false)

                    .build();
            inventoryRepository.save(inventory);
        }

    }

    private BigDecimal calculateSurgeFactor(long daysAhead) {
        if (daysAhead <= 7) {
            return BigDecimal.valueOf(1.5);
        } else if (daysAhead <= 30) {
            return BigDecimal.valueOf(1.3);
        } else if (daysAhead <= 90) {
            return BigDecimal.valueOf(1.1);
        } else {
            return BigDecimal.ONE;
        }
    }

    @Override
    public void deleteAllInventories(Room room) {
        inventoryRepository.deleteByRoom(room);
    }

    @Override
    public void deleteFutureInventories(Room room) {
        LocalDate today = LocalDate.now();
        inventoryRepository.deleteByDateAfterAndRoom(today, room);
    }

    @Override
    public Page<HotelPriceDto> searchHotels(HotelSearchRequest hotelSearchRequest) {
        String cacheKey = hotelSearchRequest.getCity() + "_" + hotelSearchRequest.getStartDate() + "_"
                + hotelSearchRequest.getEndDate() + "_" + hotelSearchRequest.getRoomsCount() + "_"
                + hotelSearchRequest.getPage() + "_" + hotelSearchRequest.getSize();

        Cache cache = cacheManager.getCache("hotels-search");
        HotelSearchCacheData cached = (cache != null) ? cache.get(cacheKey, HotelSearchCacheData.class) : null;

        if (cached != null) {
            log.info("Cache hit for hotel search: {}", cacheKey);
            return new PageImpl<>(cached.getContent(),
                    PageRequest.of(cached.getPageNumber(), cached.getPageSize()),
                    cached.getTotalElements());
        }

        log.info("Searching hotels for {} city, from {} to {}", hotelSearchRequest.getCity(), hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate());
        Pageable pageable = PageRequest.of(hotelSearchRequest.getPage(), hotelSearchRequest.getSize());
        long dateCount = ChronoUnit.DAYS.between(hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate()) + 1;

        Page<HotelPriceDto> hotelPage =
                hotelMinPriceRepository.findHotelsWithAvailableInventory(hotelSearchRequest.getCity(),
                        hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate(), hotelSearchRequest.getRoomsCount(),
                        dateCount, pageable);

        if (cache != null) {
            cache.put(cacheKey, new HotelSearchCacheData(hotelPage));
        }

        return hotelPage;
    }

    @Override
    public List<InventoryDto> getAllInventoryByRoom(Long roomId) {
        log.info("Getting All inventory by room for room with id: {}", roomId);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: "+roomId));

        User user = getCurrentUser();
        Long currentUserId = user != null ? user.getId() : null;
        Long ownerId = room.getHotel() != null && room.getHotel().getOwner() != null
                ? room.getHotel().getOwner().getId()
                : null;

        boolean ownerMatch = currentUserId != null && ownerId != null && Objects.equals(currentUserId, ownerId);
        if(!ownerMatch) throw new AccessDeniedException("You are not the owner of room with id: "+roomId);

        return inventoryRepository.findByRoomOrderByDate(room)
                .stream()
                .map((element) -> modelMapper.map(element, InventoryDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "hotels-search", allEntries = true)
    public void updateInventory(Long roomId, UpdateInventoryRequestDto updateInventoryRequestDto) {
        log.info("Updating All inventory by room for room with id: {} between date range: {} - {}", roomId,
                updateInventoryRequestDto.getStartDate(), updateInventoryRequestDto.getEndDate());

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: "+roomId));

        User user = getCurrentUser();
        Long currentUserId = user != null ? user.getId() : null;
        Long ownerId = room.getHotel() != null && room.getHotel().getOwner() != null
                ? room.getHotel().getOwner().getId()
                : null;

        boolean ownerMatch = currentUserId != null && ownerId != null && Objects.equals(currentUserId, ownerId);
        if(!ownerMatch) throw new AccessDeniedException("You are not the owner of room with id: "+roomId);

        inventoryRepository.getInventoryAndLockBeforeUpdate(roomId, updateInventoryRequestDto.getStartDate(),
                updateInventoryRequestDto.getEndDate());

        inventoryRepository.updateInventory(roomId, updateInventoryRequestDto.getStartDate(),
                updateInventoryRequestDto.getEndDate(), updateInventoryRequestDto.getClosed(),
                updateInventoryRequestDto.getSurgeFactor());
    }
}
