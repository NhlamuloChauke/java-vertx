package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.dto.request.PassengerRequest;
import com.dvtsoftware.airline.booking.exception.ValidationException;
import com.dvtsoftware.airline.booking.service.PassengerService;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PassengerHandler {

    private static final Logger log = LoggerFactory.getLogger(PassengerHandler.class);
    private final PassengerService passengerService;

    public PassengerHandler(PassengerService passengerService) {
        this.passengerService = passengerService;
    }

    public void createPassenger(RoutingContext ctx) {
        try {
            PassengerRequest request = ctx.body().asPojo(PassengerRequest.class);
            validate(request);

            passengerService.createPassenger(request)
                    .onSuccess(passenger -> ctx.response()
                            .putHeader("content-type", "application/json")
                            .setStatusCode(201)
                            .end(Json.encodePrettily(passenger)))
                    .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
        } catch (ValidationException e) {
            ErrorHandler.handleFailure(ctx, e);
        } catch (Exception e) {
            log.error("Failed to parse passenger request: {}", e.getMessage());
            ErrorHandler.sendError(ctx, 400, "Bad Request", "Malformed JSON body");
        }
    }

    private void validate(PassengerRequest request) {
        if (request == null) throw new ValidationException("Request body is required");
        if (isBlank(request.getFirstName())) throw new ValidationException("'firstName' is required");
        if (isBlank(request.getLastName())) throw new ValidationException("'lastName' is required");
        if (isBlank(request.getEmail())) throw new ValidationException("'email' is required");
        if (isBlank(request.getPassportNumber())) throw new ValidationException("'passportNumber' is required");
        if (!request.getEmail().contains("@"))
            throw new ValidationException("'email' must be a valid email address");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
