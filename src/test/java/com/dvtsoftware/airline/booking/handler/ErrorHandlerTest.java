package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.exception.*;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErrorHandlerTest {

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerResponse response;

    @Mock
    private HttpServerRequest request;

    @BeforeEach
    void setUp() {
        when(routingContext.response()).thenReturn(response);
        when(routingContext.request()).thenReturn(request);
        when(request.path()).thenReturn("/test");
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);
    }

    @Test
    void testHandleDuplicateAirlineCodeException() {
        ErrorHandler.handleFailure(routingContext, new DuplicateAirlineCodeException("SA"));
        verify(response).setStatusCode(409);
        verify(response).end(anyString());
    }

    @Test
    void testHandleDuplicatePassportException() {
        ErrorHandler.handleFailure(routingContext, new DuplicatePassportException("P1234567"));
        verify(response).setStatusCode(409);
        verify(response).end(anyString());
    }

    @Test
    void testHandleDuplicateBookingException() {
        ErrorHandler.handleFailure(routingContext, new DuplicateBookingException("12A"));
        verify(response).setStatusCode(409);
        verify(response).end(anyString());
    }

    @Test
    void testHandleAirlineNotFoundException() {
        ErrorHandler.handleFailure(routingContext, new AirlineNotFoundException(1L));
        verify(response).setStatusCode(404);
        verify(response).end(anyString());
    }

    @Test
    void testHandleFlightNotFoundException() {
        ErrorHandler.handleFailure(routingContext, new FlightNotFoundException(101L));
        verify(response).setStatusCode(404);
        verify(response).end(anyString());
    }

    @Test
    void testHandleBookingNotFoundException() {
        ErrorHandler.handleFailure(routingContext, new BookingNotFoundException(101L));
        verify(response).setStatusCode(404);
        verify(response).end(anyString());
    }

    @Test
    void testHandlePassengerNotFoundException() {
        ErrorHandler.handleFailure(routingContext, new PassengerNotFoundException("ID 1"));
        verify(response).setStatusCode(404);
        verify(response).end(anyString());
    }

    @Test
    void testHandleGenericNotFoundMessage() {
        ErrorHandler.handleFailure(routingContext, new RuntimeException("The requested resource was not found in the system"));
        verify(response).setStatusCode(404);
        verify(response).end(anyString());
    }

    @Test
    void testHandleUnexpectedError() {
        ErrorHandler.handleFailure(routingContext, new NullPointerException("Database connection lost"));
        verify(response).setStatusCode(500);
        verify(response).end(anyString());
    }

    @Test
    void testHandleValidationException() {
        ErrorHandler.handleFailure(routingContext, new com.dvtsoftware.airline.booking.exception.ValidationException("'code' is required"));
        verify(response).setStatusCode(400);
        verify(response).end(anyString());
    }

    @Test
    void testHandleIllegalArgumentException() {
        ErrorHandler.handleFailure(routingContext, new IllegalArgumentException("Invalid input"));
        verify(response).setStatusCode(400);
        verify(response).end(anyString());
    }

    @Test
    void testSendErrorDirectly() {
        ErrorHandler.sendError(routingContext, 422, "Unprocessable Entity", "Validation failed");
        verify(response).setStatusCode(422);
        verify(response).end(anyString());
    }
}
