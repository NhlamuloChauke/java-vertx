package com.dvtsoftware.airline.booking.exception;

public class DuplicateBookingException extends RuntimeException {
    public DuplicateBookingException(String seat) {
        super("Seat '" + seat + "' is already booked");
    }
}
