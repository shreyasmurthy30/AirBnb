package com.project.airBnbApp.service;

import com.project.airBnbApp.dto.RoomDto;
import com.project.airBnbApp.entity.Hotel;
import com.project.airBnbApp.entity.Room;
import com.project.airBnbApp.entity.User;
import com.project.airBnbApp.exception.ResourceNotFoundException;
import com.project.airBnbApp.exception.UnAuthorisedException;
import com.project.airBnbApp.repository.HotelRepository;
import com.project.airBnbApp.repository.RoomRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.project.airBnbApp.util.AppUtils.getCurrentUser;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomServiceImpl implements RoomService{

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final ModelMapper modelMapper;
    private final InventoryService inventoryService;

    @Override
    public RoomDto createNewRoom(Long hotelId, RoomDto roomDto) {
        log.info("Creating a new room in hotel with ID : {}", hotelId);

        // returns an optional so throw a runtime exception
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID : " + hotelId));
        User user = getCurrentUser();

        Long currentUserId = user != null ? user.getId() : null;
        Long ownerId = hotel.getOwner() != null ? hotel.getOwner().getId() : null;

        boolean ownerMatch = currentUserId != null && ownerId != null && Objects.equals(currentUserId, ownerId);
        if(!ownerMatch){
            throw new UnAuthorisedException("This user does not own this hotel with id: "+ hotelId);
        }

        Room room = modelMapper.map(roomDto, Room.class);
        room.setHotel(hotel);
        room = roomRepository.save(room);

        if(hotel.getActive()) {
            inventoryService.initializeRoomForAYear(room);
        }

        return modelMapper.map(room, RoomDto.class);
    }

    @Override
    public List<RoomDto> getAllRoomsInHotel(Long Id) {
        log.info("Getting all rooms in hotel with ID : {}", Id);
        Hotel hotel = hotelRepository.findById(Id).orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID : " + Id));

        User user = getCurrentUser();

        Long currentUserId = user != null ? user.getId() : null;
        Long ownerId = hotel.getOwner() != null ? hotel.getOwner().getId() : null;

        boolean ownerMatch = currentUserId != null && ownerId != null && Objects.equals(currentUserId, ownerId);
        if(!ownerMatch){
            throw new UnAuthorisedException("This user does not own this hotel with id: "+ Id);
        }

        return hotel.getRooms().stream().map((element) -> modelMapper.map(element, RoomDto.class)).collect(Collectors.toList());
    }

    @Override
    public RoomDto getRoomById(Long roomId) {
        log.info("Getting room with ID : {}", roomId);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("Room not found with ID : " + roomId));
        return modelMapper.map(room,RoomDto.class);
    }

    @Override
    @Transactional
    public void deleteRoomById(Long roomId) {
        log.info("Getting room with ID : {}", roomId);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("Room not found with ID : " + roomId));

        User user = getCurrentUser();

        Long currentUserId = user != null ? user.getId() : null;
        Long ownerId = room.getHotel() != null && room.getHotel().getOwner() != null
                ? room.getHotel().getOwner().getId()
                : null;

        boolean ownerMatch = currentUserId != null && ownerId != null && Objects.equals(currentUserId, ownerId);
        if(!ownerMatch){
            throw new UnAuthorisedException("This user does not own this room with id: "+ roomId);
        }

        inventoryService.deleteFutureInventories(room);
        roomRepository.deleteById(roomId);
    }

    @Override
    @Transactional
    public RoomDto updateRoomById(Long hotelId, Long roomId, RoomDto roomDto) {
        log.info("Updating the room with ID: {}", roomId);
        Hotel hotel = hotelRepository
                .findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: "+hotelId));

        User user = getCurrentUser();
        Long currentUserId = user != null ? user.getId() : null;
        Long ownerId = hotel.getOwner() != null ? hotel.getOwner().getId() : null;

        boolean ownerMatch = currentUserId != null && ownerId != null && Objects.equals(currentUserId, ownerId);
        if(!ownerMatch) {
            throw new UnAuthorisedException("This user does not own this hotel with id: "+hotelId);
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: "+roomId));

        modelMapper.map(roomDto, room);
        room.setId(roomId);

//        TODO: if price or inventory is updated, then update the inventory for this room
        room = roomRepository.save(room);

        return modelMapper.map(room, RoomDto.class);
    }
}
