package com.project.airBnbApp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.airBnbApp.event.BookingCancelledEvent;
import com.project.airBnbApp.event.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Value("${spring.mail.username}")
    private String fromEmail;

    @KafkaListener(topics = "booking-confirmed", groupId = "airbnb-notifications")
    public void handleBookingConfirmed(String message) {
        try {
            BookingConfirmedEvent event = objectMapper.readValue(message, BookingConfirmedEvent.class);
            log.info("Received booking-confirmed event for booking ID: {}", event.getBookingId());

            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromEmail);
            mail.setTo(event.getGuestEmail());
            mail.setSubject("Booking Confirmed - " + event.getHotelName());
            mail.setText(buildConfirmationText(event));
            mailSender.send(mail);

            log.info("Confirmation email sent to {} for booking ID: {}", event.getGuestEmail(), event.getBookingId());
        } catch (Throwable t) {
            log.error("Failed to process booking-confirmed event: {}", t.getMessage(), t);
        }
    }

    @KafkaListener(topics = "booking-cancelled", groupId = "airbnb-notifications")
    public void handleBookingCancelled(String message) {
        try {
            BookingCancelledEvent event = objectMapper.readValue(message, BookingCancelledEvent.class);
            log.info("Received booking-cancelled event for booking ID: {}", event.getBookingId());

            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromEmail);
            mail.setTo(event.getGuestEmail());
            mail.setSubject("Booking Cancelled — " + event.getHotelName());
            mail.setText(buildCancellationText(event));
            mailSender.send(mail);

            log.info("Cancellation email sent to {} for booking ID: {}", event.getGuestEmail(), event.getBookingId());
        } catch (Exception e) {
            log.error("Failed to process booking-cancelled event: {}", e.getMessage(), e);
        }
    }

    private String buildConfirmationText(BookingConfirmedEvent event) {
        return """
                Hi %s,

                Your booking has been confirmed!

                Booking ID   : #%d
                Hotel        : %s
                Room         : %s
                Check-in     : %s
                Check-out    : %s
                Amount Paid  : ₹%s

                Enjoy your stay!

                — AirBnb Team
                """.formatted(
                event.getGuestName(),
                event.getBookingId(),
                event.getHotelName(),
                event.getRoomType(),
                event.getCheckInDate(),
                event.getCheckOutDate(),
                event.getAmount()
        );
    }

    private String buildCancellationText(BookingCancelledEvent event) {
        return """
                Hi %s,

                Your booking has been cancelled.

                Booking ID : #%d
                Hotel      : %s
                Room       : %s
                Check-in   : %s
                Check-out  : %s

                Your refund will be processed within 5-7 business days.

                — AirBnb Team
                """.formatted(
                event.getGuestName(),
                event.getBookingId(),
                event.getHotelName(),
                event.getRoomType(),
                event.getCheckInDate(),
                event.getCheckOutDate()
        );
    }
}
