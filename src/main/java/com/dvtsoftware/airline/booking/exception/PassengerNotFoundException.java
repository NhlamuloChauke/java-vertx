package com.dvtsoftware.airline.booking.exception;

public class PassengerNotFoundException extends RuntimeException {
    public PassengerNotFoundException(String identifier) {
        super("Passenger not found: " + identifier);
    }
}
