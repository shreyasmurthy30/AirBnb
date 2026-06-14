package com.project.airBnbApp.service;

import com.project.airBnbApp.dto.BookingDto;
import com.project.airBnbApp.dto.BookingRequest;
import com.project.airBnbApp.dto.GuestDto;
import com.project.airBnbApp.dto.HotelReportDto;
import com.project.airBnbApp.entity.*;
import com.project.airBnbApp.entity.enums.BookingStatus;
import com.project.airBnbApp.event.BookingCancelledEvent;
import com.project.airBnbApp.event.BookingConfirmedEvent;
import com.project.airBnbApp.exception.ResourceNotFoundException;
import com.project.airBnbApp.exception.UnAuthorisedException;
import com.project.airBnbApp.repository.*;
import com.project.airBnbApp.strategy.PricingService;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.project.airBnbApp.util.AppUtils.getCurrentUser;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService{

    private final GuestRepository guestRepository;
    private final ModelMapper modelMapper;

    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final CheckoutService checkoutService;
    private final PricingService pricingService;

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${frontend.url}")
    private String frontendUrl;


    @Override
    @org.springframework.transaction.annotation.Transactional
    public BookingDto initializeBooking(BookingRequest bookingRequest) {

        log.info("Initialising booking for hotel : {}, room: {}, date {}-{}", bookingRequest.getHotelId(),
                bookingRequest.getRoomId(), bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate());

        Hotel hotel = hotelRepository.findById(bookingRequest.getHotelId()).orElseThrow(() ->
                new ResourceNotFoundException("Hotel not found with id: "+bookingRequest.getHotelId()));

        Room room = roomRepository.findById(bookingRequest.getRoomId()).orElseThrow(() ->
                new ResourceNotFoundException("Room not found with id: "+bookingRequest.getRoomId()));

        List<Inventory> inventoryList = inventoryRepository.findAndLockAvailableInventory(room.getId(),
                bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate(), bookingRequest.getRoomsCount());

        long daysCount = ChronoUnit.DAYS.between(bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate())+1;

        if (inventoryList.size() != daysCount) {
            throw new IllegalStateException("Room is not available anymore");
        }

        // Reserve the room/ update the reserved count of inventories
        inventoryRepository.initBooking(room.getId(), bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate(), bookingRequest.getRoomsCount());

        BigDecimal priceForOneRoom = pricingService.calculateTotalPrice(inventoryList);
        BigDecimal totalPrice = priceForOneRoom.multiply(BigDecimal.valueOf(bookingRequest.getRoomsCount()));

        Booking booking = Booking.builder()
                .bookingStatus(BookingStatus.RESERVED)
                .hotel(hotel)
                .room(room)
                .checkInDate(bookingRequest.getCheckInDate())
                .checkOutDate(bookingRequest.getCheckOutDate())
                .user(getCurrentUser())
                .roomsCount(bookingRequest.getRoomsCount())
                .amount(totalPrice)
                .build();

        booking = bookingRepository.save(booking);
        return modelMapper.map(booking, BookingDto.class);
    }

    @Override
    @Transactional
    public BookingDto addGuests(Long bookingId, List<GuestDto> guestDtoList) {

        log.info("Adding guests for booking with id: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() ->
                new ResourceNotFoundException("Booking not found with id: "+bookingId));

        User user = getCurrentUser();
        User bookingUser = booking.getUser();

        log.info("Current user -> id: {}, email: {}, class: {}",
                user != null ? user.getId() : null,
                user != null ? user.getEmail() : null,
                user != null ? user.getClass().getName() : null);

        if (bookingUser != null) {
            log.info("Booking user -> id: {}, email: {}, class: {}",
                    bookingUser.getId(),
                    bookingUser.getEmail(),
                    bookingUser.getClass().getName());
        } else {
            log.info("Booking user is null");
        }

        boolean sameUser = user != null && bookingUser != null
                && Objects.equals(user.getId(), bookingUser.getId());
        log.info("Result of id equality check (currentUser.id vs bookingUser.id): {}", sameUser);

        if (!sameUser) {
            throw new UnAuthorisedException("Booking does not belong to this user with id: "+ (user != null ? user.getId() : null));
        }

        if (hasBookingExpired(booking)) {
            throw new IllegalStateException("Booking has already expired");
        }

        if(booking.getBookingStatus() != BookingStatus.RESERVED) {
            throw new IllegalStateException("Booking is not under reserved state, cannot add guests");
        }

        for (GuestDto guestDto: guestDtoList) {
            Guest guest = modelMapper.map(guestDto, Guest.class);
            guest.setUser(getCurrentUser());
            guest = guestRepository.save(guest);
            booking.getGuests().add(guest);
        }

        booking.setBookingStatus(BookingStatus.GUESTS_ADDED);
        booking = bookingRepository.save(booking);
        return modelMapper.map(booking, BookingDto.class);
    }

    public boolean hasBookingExpired(Booking booking) {
        return booking.getCreatedAt().plusMinutes(5).isBefore(LocalDateTime.now());
    }


    @Override
    @Transactional
    public String initiatePayments(Long bookingId) {
        log.info("Initiating payments for booking with id: {}", bookingId);

        // first check if booking is valid
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id: "+bookingId)
        );

        log.info("initiatePayments: Loaded booking -> id: {}, status: {}, amount: {}",
                booking.getId(), booking.getBookingStatus(), booking.getAmount());

        // check if the user doing the init payment is same as the user who created the booking
        User user = getCurrentUser();
        Long currentUserId = user != null ? user.getId() : null;
        Long bookingUserId = booking.getUser() != null ? booking.getUser().getId() : null;

        log.info("initiatePayments: current user id: {}, booking user id: {}", currentUserId, bookingUserId);

        boolean sameUser = currentUserId != null && bookingUserId != null
                && Objects.equals(currentUserId, bookingUserId);
        log.info("initiatePayments: ownership id check result: {}", sameUser);

        if (!sameUser) {
            log.warn("initiatePayments: user {} does not own booking {}", currentUserId, booking.getId());
            throw new UnAuthorisedException("Booking does not belong to this user with id: "+ currentUserId);
        }
        if (hasBookingExpired(booking)) {
            log.warn("initiatePayments: booking {} has expired", booking.getId());
            throw new IllegalStateException("Booking has already expired");
        }

        log.info("initiatePayments: calling checkoutService.getCheckoutSession for booking {}", booking.getId());

        // call the checkout service which will return a session url from stripe
        String sessionUrl = checkoutService.getCheckoutSession(booking,
                frontendUrl+"/payments/" +bookingId +"/status",
                frontendUrl+"/payments/" +bookingId +"/status");

        // update booking status to payments pending
        booking.setBookingStatus(BookingStatus.PAYMENTS_PENDING);
        bookingRepository.save(booking);

        log.info("initiatePayments: successfully set status to PAYMENTS_PENDING for booking {}", booking.getId());

        return sessionUrl;
    }

    @Override
    @Transactional
    public void capturePayment(Event event) {
        log.info("capturePayment: received event type {} with event id {}", event.getType(), event.getId());

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);

            if (session == null) {
                log.warn("capturePayment: Session is null for checkout.session.completed event id {}. Skipping booking confirmation.",
                        event.getId());
                return;
            }

            String sessionId = session.getId();
            log.info("capturePayment: deserialized Session with id {} from event id {}", sessionId, event.getId());

            Booking booking = bookingRepository.findByPaymentSessionId(sessionId).orElseThrow(() ->
                            new ResourceNotFoundException("Booking not found for session ID: "+sessionId));

            log.info("capturePayment: found booking {} with status {} for session {}", booking.getId(),
                    booking.getBookingStatus(), sessionId);

            if (booking.getBookingStatus() == BookingStatus.EXPIRED) {
                log.warn("capturePayment: booking {} is EXPIRED — issuing automatic refund.", booking.getId());
                try {
                    RefundCreateParams refundParams = RefundCreateParams.builder()
                            .setPaymentIntent(session.getPaymentIntent())
                            .build();
                    Refund.create(refundParams);
                    log.info("capturePayment: refund issued for expired booking {}.", booking.getId());
                } catch (StripeException e) {
                    log.error("capturePayment: failed to refund expired booking {}: {}", booking.getId(), e.getMessage());
                }
                return;
            }

            booking.setBookingStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);

            // lock the inventory to prevent race condition while updating inventoryCount and bookedCount
            inventoryRepository.findAndLockReservedInventory(booking.getRoom().getId(), booking.getCheckInDate(),
                    booking.getCheckOutDate(), booking.getRoomsCount());

            // update inventoryCount and bookedCount
            inventoryRepository.confirmBooking(booking.getRoom().getId(), booking.getCheckInDate(),
                    booking.getCheckOutDate(), booking.getRoomsCount());

            log.info("capturePayment: successfully confirmed booking {} and updated inventory.", booking.getId());

            BookingConfirmedEvent confirmedEvent = new BookingConfirmedEvent(
                    booking.getId(),
                    booking.getUser().getName(),
                    booking.getUser().getEmail(),
                    booking.getHotel().getName(),
                    booking.getRoom().getType(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getAmount()
            );
            if (kafkaTemplate != null) {
                kafkaTemplate.send("booking-confirmed", confirmedEvent);
                log.info("Published booking-confirmed event for booking ID: {}", booking.getId());
            }
        } else {
            log.warn("Unhandled event type: {}", event.getType());
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public String cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id: "+bookingId)
        );
        User user = getCurrentUser();
        if (!Objects.equals(user.getId(), booking.getUser().getId())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id: "+user.getId());
        }

        if(booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be cancelled");
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // lock the inventory to prevent race condition while updating inventoryCount and bookedCount
        inventoryRepository.findAndLockReservedInventory(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomsCount());

        // update inventoryCount and bookedCount
        inventoryRepository.cancelBooking(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomsCount());

        // handle the refund
        try {
            Session session = Session.retrieve(booking.getPaymentSessionId());
            RefundCreateParams refundParams = RefundCreateParams.builder()
                    .setPaymentIntent(session.getPaymentIntent())
                    .build();

            Refund.create(refundParams);
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }

        BookingCancelledEvent event = new BookingCancelledEvent(
                booking.getId(),
                booking.getUser().getName(),
                booking.getUser().getEmail(),
                booking.getHotel().getName(),
                booking.getRoom().getType(),
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );
        if (kafkaTemplate != null) {
            kafkaTemplate.send("booking-cancelled", event);
            log.info("Published booking-cancelled event for booking ID: {}", booking.getId());
        }
        return "Booking with ID " + bookingId + " has been successfully cancelled. Refund will be processed within 5-7 business days.";
    }

    @Override
    public BookingStatus getBookingStatus(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id: "+bookingId)
        );
        User user = getCurrentUser();
        if (!Objects.equals(user.getId(), booking.getUser().getId())) {
            throw new UnAuthorisedException("Booking does not belong to this user with id: "+user.getId());
        }

        return booking.getBookingStatus();
    }

    @Override
    public List<BookingDto> getAllBookingsByHotelId(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() -> new ResourceNotFoundException("Hotel not " +
                "found with ID: "+hotelId));
        User user = getCurrentUser();

        log.info("Getting all booking for the hotel with ID: {}", hotelId);
        Long currentUserId = user != null ? user.getId() : null;
        Long ownerId = hotel.getOwner() != null ? hotel.getOwner().getId() : null;

        boolean ownerMatch = currentUserId != null && ownerId != null && Objects.equals(currentUserId, ownerId);

        log.info("getAllBookingsByHotelId: currentUserId={}, hotelOwnerId={}, ownerMatch={}, userClass={}, ownerClass={}",
                currentUserId,
                ownerId,
                ownerMatch,
                user != null ? user.getClass().getName() : null,
                hotel.getOwner() != null ? hotel.getOwner().getClass().getName() : null);

        if(!ownerMatch) throw new AccessDeniedException("You are not the owner of hotel with id: "+hotelId);

        List<Booking> bookings = bookingRepository.findByHotel(hotel);

        return bookings.stream()
                .map((element) -> modelMapper.map(element, BookingDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public HotelReportDto getHotelReport(Long hotelId, LocalDate startDate, LocalDate endDate) {

        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() -> new ResourceNotFoundException("Hotel not " +
                "found with ID: "+hotelId));
        User user = getCurrentUser();

        log.info("Generating report for hotel with ID: {}", hotelId);
        Long currentUserId = user != null ? user.getId() : null;
        Long ownerId = hotel.getOwner() != null ? hotel.getOwner().getId() : null;

        boolean ownerMatch = currentUserId != null && ownerId != null && Objects.equals(currentUserId, ownerId);

        log.info("getHotelReport: currentUserId={}, hotelOwnerId={}, ownerMatch={}, userClass={}, ownerClass={}",
                currentUserId,
                ownerId,
                ownerMatch,
                user != null ? user.getClass().getName() : null,
                hotel.getOwner() != null ? hotel.getOwner().getClass().getName() : null);

        if(!ownerMatch) throw new AccessDeniedException("You are not the owner of hotel with id: "+hotelId);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Booking> bookings = bookingRepository.findByHotelAndCreatedAtBetween(hotel, startDateTime, endDateTime);

        Long totalConfirmedBookings = bookings
                .stream()
                .filter(booking -> booking.getBookingStatus() == BookingStatus.CONFIRMED)
                .count();

        BigDecimal totalRevenueOfConfirmedBookings = bookings.stream()
                .filter(booking -> booking.getBookingStatus() == BookingStatus.CONFIRMED)
                .map(Booking::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgRevenue = totalConfirmedBookings == 0 ? BigDecimal.ZERO :
                totalRevenueOfConfirmedBookings.divide(BigDecimal.valueOf(totalConfirmedBookings), RoundingMode.HALF_UP);

        return new HotelReportDto(totalConfirmedBookings, totalRevenueOfConfirmedBookings, avgRevenue);
    }

    @Override
    public List<BookingDto> getMyBookings() {
        User user = getCurrentUser();

        return bookingRepository.findByUser(user)
                .stream().
                map((element) -> modelMapper.map(element, BookingDto.class))
                .collect(Collectors.toList());
    }
}
