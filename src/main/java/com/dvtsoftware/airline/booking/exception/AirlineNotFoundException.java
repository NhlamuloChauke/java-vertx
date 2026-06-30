package com.dvtsoftware.airline.booking.exception;

public class AirlineNotFoundException extends RuntimeException {
    public AirlineNotFoundException(Long id) {
        super("Airline not found with ID: " + id);
    }
}
