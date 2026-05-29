package com.project.airBnbApp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HotelSearchRequest {

    // what user will send when they are searching for hotel/room
    private String city;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer roomsCount;

    // for pagination
    private Integer page = 0;
    private Integer size = 10;
}
