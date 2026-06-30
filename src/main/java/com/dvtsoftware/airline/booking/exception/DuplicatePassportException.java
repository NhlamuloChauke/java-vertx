package com.dvtsoftware.airline.booking.exception;

public class DuplicatePassportException extends RuntimeException {
    public DuplicatePassportException(String passportNumber) {
        super("A passenger with passport '" + passportNumber + "' already exists.");
    }
}
