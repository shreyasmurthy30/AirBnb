package com.project.airBnbApp.service;

import com.project.airBnbApp.entity.Booking;
import com.project.airBnbApp.entity.enums.BookingStatus;
import com.project.airBnbApp.repository.BookingRepository;
import com.project.airBnbApp.repository.InventoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupService {

    private final BookingRepository bookingRepository;
    private final InventoryRepository inventoryRepository;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cleanupExpiredBookings() {
        LocalDateTime expiry = LocalDateTime.now().minusMinutes(3);

        List<Booking> expiredBookings = bookingRepository.findExpiredBookings(
                List.of(BookingStatus.RESERVED, BookingStatus.GUESTS_ADDED, BookingStatus.PAYMENTS_PENDING),
                expiry
        );

        for (Booking booking : expiredBookings) {
            booking.setBookingStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);

            inventoryRepository.releaseReservedInventory(
                    booking.getRoom().getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getRoomsCount()
            );

            log.info("Booking ID {} expired — reserved inventory released.", booking.getId());
        }

        if (!expiredBookings.isEmpty()) {
            log.info("Cleanup complete: {} expired booking(s) processed.", expiredBookings.size());
        }
    }
}
