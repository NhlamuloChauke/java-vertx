package com.dvtsoftware.airline.booking.exception;

public class DuplicateAirlineCodeException extends RuntimeException {
    public DuplicateAirlineCodeException(String code) {
        super("An airline with code '" + code + "' already exists.");
    }
}
