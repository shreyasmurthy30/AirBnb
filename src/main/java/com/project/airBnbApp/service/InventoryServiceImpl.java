package com.project.airBnbApp.service;

import com.project.airBnbApp.dto.HotelSearchRequest;
import com.project.airBnbApp.dto.HotelPriceDto;
import com.project.airBnbApp.entity.Inventory;
import com.project.airBnbApp.entity.Room;
import com.project.airBnbApp.repository.HotelMinPriceRepository;
import com.project.airBnbApp.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService{

    private final InventoryRepository inventoryRepository;
    private final HotelMinPriceRepository hotelMinPriceRepository;
    private final ModelMapper modelMapper;

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
        log.info("Searching hotels for {} city, from {} to {}", hotelSearchRequest.getCity(), hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate());
        Pageable pageable = PageRequest.of(hotelSearchRequest.getPage(), hotelSearchRequest.getSize());
        long dateCount =
                ChronoUnit.DAYS.between(hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate()) + 1;

        // business logic - 90 days
        Page<HotelPriceDto> hotelPage =
                hotelMinPriceRepository.findHotelsWithAvailableInventory(hotelSearchRequest.getCity(),
                        hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate(), hotelSearchRequest.getRoomsCount(),
                        dateCount, pageable);

        return hotelPage;
    }
}
