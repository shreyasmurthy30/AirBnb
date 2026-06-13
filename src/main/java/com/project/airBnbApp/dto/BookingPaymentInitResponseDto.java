package com.project.airBnbApp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingPaymentInitResponseDto {
    // what is returned when we do init payment to stripe
    // they send back a session url which we will redirect the user to payment page
    private String sessionUrl;
}
