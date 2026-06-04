package com.project.airBnbApp.service;

import com.project.airBnbApp.dto.BookingDto;
import com.project.airBnbApp.dto.BookingRequest;
import com.project.airBnbApp.dto.GuestDto;
import com.project.airBnbApp.entity.enums.BookingStatus;
import com.stripe.model.Event;

import java.util.List;

public interface BookingService {

    BookingDto initializeBooking(BookingRequest bookingRequest);
    BookingDto addGuests(Long bookingId, List<GuestDto> guestDtoList);

    String initiatePayments(Long bookingId);
    void capturePayment(Event event);

    void cancelBooking(Long bookingId);

    BookingStatus getBookingStatus(Long bookingId);
}
