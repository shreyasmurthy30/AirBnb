package com.project.airBnbApp.dto;

import com.project.airBnbApp.entity.Hotel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HotelPriceDto {
    // What's sent back when user is searching for hotel and we want to display min price for that hotel to lure customers
    private Hotel hotel;
    private Double price;
}
