package com.dvtsoftware.airline.booking.exception;


public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(Long identifier) {
        super("Booking not found: " + identifier);
    }
}
