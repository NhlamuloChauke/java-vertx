package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.dto.request.BookingRequest;
import com.dvtsoftware.airline.booking.dto.response.BookingResponse;
import com.dvtsoftware.airline.booking.exception.ValidationException;
import com.dvtsoftware.airline.booking.model.Booking;
import com.dvtsoftware.airline.booking.service.BookingService;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

public class BookingHandler {

    private static final Logger log = LoggerFactory.getLogger(BookingHandler.class);
    private final BookingService bookingService;

    public BookingHandler(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    public void createBooking(RoutingContext ctx) {
        try {
            BookingRequest request = ctx.body().asPojo(BookingRequest.class);
            validate(request);

            bookingService.createBooking(request)
                    .map(this::mapToResponse)
                    .onSuccess(response -> ctx.response()
                            .setStatusCode(201)
                            .putHeader("content-type", "application/json")
                            .end(Json.encodePrettily(response)))
                    .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
        } catch (ValidationException e) {
            ErrorHandler.handleFailure(ctx, e);
        } catch (Exception e) {
            log.error("Failed to parse booking request: {}", e.getMessage());
            ErrorHandler.sendError(ctx, 400, "Bad Request", "Malformed JSON body");
        }
    }

    public void getBookingById(RoutingContext ctx) {
        try {
            Long id = Long.valueOf(ctx.pathParam("id"));
            bookingService.findById(id)
                    .map(this::mapToResponse)
                    .onSuccess(response -> ctx.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json")
                            .end(Json.encodePrettily(response)))
                    .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
        } catch (NumberFormatException e) {
            ErrorHandler.sendError(ctx, 400, "Bad Request", "Invalid ID format — must be a numeric value");
        }
    }

    public void cancelBooking(RoutingContext ctx) {
        try {
            Long id = Long.valueOf(ctx.pathParam("id"));
            bookingService.cancelBooking(id)
                    .onSuccess(v -> ctx.response().setStatusCode(204).end())
                    .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
        } catch (NumberFormatException e) {
            ErrorHandler.sendError(ctx, 400, "Bad Request", "Invalid ID format — must be a numeric value");
        }
    }

    public void getBookingsByPassenger(RoutingContext ctx) {
        try {
            Long passengerId = Long.valueOf(ctx.pathParam("id"));
            bookingService.findByPassengerId(passengerId)
                    .map(list -> list.stream()
                            .map(this::mapToResponse)
                            .collect(Collectors.toList()))
                    .onSuccess(responses -> ctx.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json")
                            .end(Json.encodePrettily(responses)))
                    .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
        } catch (NumberFormatException e) {
            ErrorHandler.sendError(ctx, 400, "Bad Request", "Invalid passenger ID format — must be a numeric value");
        }
    }

    private void validate(BookingRequest request) {
        if (request == null) throw new ValidationException("Request body is required");
        if (request.getPassengerId() == null) throw new ValidationException("'passengerId' is required");
        if (request.getFlightId() == null) throw new ValidationException("'flightId' is required");
        if (request.getSeatNumber() == null || request.getSeatNumber().isBlank())
            throw new ValidationException("'seatNumber' is required");
    }

    private BookingResponse mapToResponse(Booking booking) {
        BookingResponse resp = new BookingResponse();
        resp.setId(booking.getId());
        resp.setBookingReference(booking.getBookingReference());
        resp.setPassengerId(booking.getPassengerId());
        resp.setFlightId(booking.getFlightId());
        resp.setBookingDate(booking.getBookingDate());
        resp.setSeatNumber(booking.getSeatNumber());
        resp.setStatus(booking.getStatus());
        resp.setTotalAmount(booking.getTotalAmount());
        return resp;
    }
}
