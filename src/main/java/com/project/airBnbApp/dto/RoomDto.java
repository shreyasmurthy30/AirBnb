package com.project.airBnbApp.dto;

import com.project.airBnbApp.entity.Hotel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class RoomDto {
    // What's sent back in HotelInfoDto when a particular hotel is requested, we send all the rooms of that hotel as well
    private Long id;
    private String type;
    private BigDecimal basePrice;
    private String[] photos;
    private String[] amenities;
    private Integer totalCount;
    private Integer capacity;
}
