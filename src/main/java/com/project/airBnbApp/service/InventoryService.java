package com.project.airBnbApp.service;

import com.project.airBnbApp.dto.HotelSearchRequest;
import com.project.airBnbApp.dto.HotelPriceDto;
import com.project.airBnbApp.dto.InventoryDto;
import com.project.airBnbApp.dto.UpdateInventoryRequestDto;
import com.project.airBnbApp.entity.Room;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;

import java.util.List;

public interface InventoryService {
    void initializeRoomForAYear(Room room);
    void deleteAllInventories(Room room);
    void deleteFutureInventories(Room room);

    Page<HotelPriceDto> searchHotels(HotelSearchRequest hotelSearchRequest);

    List<InventoryDto> getAllInventoryByRoom(Long roomId);

    void updateInventory(Long roomId, UpdateInventoryRequestDto updateInventoryRequestDto);
}
