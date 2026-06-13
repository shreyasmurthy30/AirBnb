package com.project.airBnbApp.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotelInfoDto {
    // What's sent back when user requests for a particular hotel info by id
    private HotelDto hotel;
    private List<RoomDto> rooms;

}
