package com.project.airBnbApp.service;

import com.project.airBnbApp.dto.RoomDto;
import com.project.airBnbApp.entity.Room;

import java.util.List;

public interface RoomService {
    RoomDto createNewRoom(Long hotelId, RoomDto roomDto);
    List<RoomDto> getAllRoomsInHotel(Long Id);
    RoomDto getRoomById(Long roomId);
    void deleteRoomById(Long roomId);
}
