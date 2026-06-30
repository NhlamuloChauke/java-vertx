package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.dto.response.ErrorResponse;
import com.dvtsoftware.airline.booking.exception.*;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    public static void handleFailure(RoutingContext ctx, Throwable err) {
        log.error("Request failed [{}]: {}", ctx.request().path(), err.getMessage());

        int status;
        String errorLabel;

        if (err instanceof ValidationException || err instanceof IllegalArgumentException) {
            status = 400;
            errorLabel = "Bad Request";
        } else if (err instanceof DuplicateAirlineCodeException
                || err instanceof DuplicatePassportException
                || err instanceof DuplicateBookingException) {
            status = 409;
            errorLabel = "Conflict";
        } else if (err instanceof AirlineNotFoundException
                || err instanceof FlightNotFoundException
                || err instanceof PassengerNotFoundException
                || err instanceof BookingNotFoundException
                || (err.getMessage() != null && err.getMessage().toLowerCase().contains("not found"))) {
            status = 404;
            errorLabel = "Not Found";
        } else {
            log.error("Unhandled exception", err);
            status = 500;
            errorLabel = "Internal Server Error";
        }

        String message = (status == 500)
                ? "An unexpected error occurred. Please try again later."
                : err.getMessage();

        sendError(ctx, status, errorLabel, message);
    }

    public static void sendError(RoutingContext ctx, int status, String error, String message) {
        ErrorResponse body = new ErrorResponse(status, error, message, ctx.request().path());
        ctx.response()
                .setStatusCode(status)
                .putHeader("content-type", "application/json")
                .end(Json.encodePrettily(body));
    }
}
