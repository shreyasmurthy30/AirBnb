package com.project.airBnbApp.dto;

import com.project.airBnbApp.entity.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingStatusResponseDto {
    // sent back to frontend to show the status of the booking
    private BookingStatus bookingStatus;
}
