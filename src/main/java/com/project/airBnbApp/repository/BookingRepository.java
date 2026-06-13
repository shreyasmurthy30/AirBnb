package com.project.airBnbApp.repository;

import com.project.airBnbApp.entity.Booking;
import com.project.airBnbApp.entity.Hotel;
import com.project.airBnbApp.entity.User;
import com.project.airBnbApp.entity.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByPaymentSessionId(String sessionId);

    List<Booking> findByHotel(Hotel hotel);

    List<Booking> findByHotelAndCreatedAtBetween(Hotel hotel, LocalDateTime startDateTime, LocalDateTime endDateTime);

    List<Booking> findByUser(User user);

    @Query("SELECT b FROM Booking b WHERE b.bookingStatus IN :statuses AND b.createdAt < :expiry")
    List<Booking> findExpiredBookings(@Param("statuses") List<BookingStatus> statuses,
                                      @Param("expiry") LocalDateTime expiry);
}
