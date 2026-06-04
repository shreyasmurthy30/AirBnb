package com.project.airBnbApp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingPaymentInitResponseDto {
    // what is returned when we do init payment to stripe
    // they send back a session url
    private String sessionUrl;
}
