package com.project.airBnbApp.service;

import com.project.airBnbApp.dto.HotelDto;
import com.project.airBnbApp.dto.HotelInfoDto;
import com.project.airBnbApp.dto.RoomDto;
import com.project.airBnbApp.entity.Hotel;
import com.project.airBnbApp.entity.Room;
import com.project.airBnbApp.exception.ResourceNotFoundException;
import com.project.airBnbApp.repository.HotelRepository;
import com.project.airBnbApp.repository.RoomRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService{
    private final RoomRepository roomRepository;

    // reqargconstr handles the injection as we have put final
    private final HotelRepository hotelRepository;
    private final ModelMapper modelMapper;
    private final InventoryService inventoryService;

    @Override
    public HotelDto createNewHotel(HotelDto hotelDto) {
        log.info("Creating new Hotel with name : {}", hotelDto.getName());

        // convert dto to entity
        Hotel hotel = modelMapper.map(hotelDto, Hotel.class);
        hotel.setActive(false);

        // save the hotel
        hotel = hotelRepository.save(hotel);
        log.info("Created a new Hotel with ID : {}", hotel.getId());


        return modelMapper.map(hotel,HotelDto.class);
    }

    @Override
    public HotelDto getHotelById(Long id) {

        log.info("Getting a Hotel with ID : {}", id);
        // returns an optional so throw a runtime exception
        Hotel hotel = hotelRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID : " + id));

        return modelMapper.map(hotel, HotelDto.class);
    }

    @Override
    public HotelDto updateHotelById(Long id, HotelDto hotelDto) {

        log.info("Updating the Hotel with ID : {}", id);
        // returns an optional so throw a runtime exception
        Hotel hotel = hotelRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID : " + id));

        // map everything from dto into the hotel entity and then save it
        modelMapper.map(hotelDto,hotel);
        hotel.setId(id);
        hotelRepository.save(hotel);
        return modelMapper.map(hotel, HotelDto.class);
    }

    @Override
    @Transactional
    public void deleteHotelById(Long id) {
        Hotel hotel = hotelRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID : " + id));

        // inventory dep on hotel and room so delete inventory first
        // room dep on hotel so delete room next
        // then delete hotel
        // This removes all dependencies
        for(Room room : hotel.getRooms()){
            inventoryService.deleteAllInventories(room);
            roomRepository.deleteById(room.getId());
        }

        hotelRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void activateHotel(Long id) {
        log.info("Activating the Hotel with ID : {}", id);
        // returns an optional so throw a runtime exception
        Hotel hotel = hotelRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID : " + id));
        hotel.setActive(true);

        // TODO : CREATE INVENTORY FOR ALL THE ROOMS - DONE ONLY ONCE
        for(Room room : hotel.getRooms()){
            inventoryService.initializeRoomForAYear(room);
        }
    }

    @Override
    public HotelInfoDto getHotelInfoById(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID : " + hotelId));

        List<RoomDto> rooms = hotel.getRooms()
                .stream().map((element) -> modelMapper.map(element, RoomDto.class)).toList();

        return new HotelInfoDto(modelMapper.map(hotel, HotelDto.class), rooms);
    }


}
