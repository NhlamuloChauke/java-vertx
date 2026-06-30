package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.dto.request.AirlineRequest;
import com.dvtsoftware.airline.booking.exception.ValidationException;
import com.dvtsoftware.airline.booking.service.AirlineService;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AirlineHandler {

    private static final Logger log = LoggerFactory.getLogger(AirlineHandler.class);
    private final AirlineService airlineService;

    public AirlineHandler(AirlineService airlineService) {
        this.airlineService = airlineService;
    }

    public void getAllAirlines(RoutingContext ctx) {
        airlineService.findAllAirlines()
                .onSuccess(airlines -> ctx.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(200)
                        .end(Json.encodePrettily(airlines)))
                .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
    }

    public void getAirlineById(RoutingContext ctx) {
        try {
            Long id = Long.valueOf(ctx.pathParam("id"));
            airlineService.findAirlineById(id)
                    .onSuccess(airline -> ctx.response()
                            .putHeader("content-type", "application/json")
                            .setStatusCode(200)
                            .end(Json.encodePrettily(airline)))
                    .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
        } catch (NumberFormatException e) {
            ErrorHandler.sendError(ctx, 400, "Bad Request", "Invalid ID format — must be a numeric value");
        }
    }

    public void createAirline(RoutingContext ctx) {
        try {
            AirlineRequest request = ctx.body().asPojo(AirlineRequest.class);
            validate(request);

            airlineService.createAirline(request)
                    .onSuccess(airline -> ctx.response()
                            .putHeader("content-type", "application/json")
                            .setStatusCode(201)
                            .end(Json.encodePrettily(airline)))
                    .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
        } catch (ValidationException e) {
            ErrorHandler.handleFailure(ctx, e);
        } catch (Exception e) {
            log.error("Failed to parse airline request: {}", e.getMessage());
            ErrorHandler.sendError(ctx, 400, "Bad Request", "Malformed JSON body");
        }
    }

    private void validate(AirlineRequest request) {
        if (request == null) throw new ValidationException("Request body is required");
        if (isBlank(request.getCode())) throw new ValidationException("'code' is required");
        if (isBlank(request.getName())) throw new ValidationException("'name' is required");
        if (isBlank(request.getCountry())) throw new ValidationException("'country' is required");
        if (request.getCode().trim().length() > 3)
            throw new ValidationException("'code' must be 2-3 characters (IATA standard)");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
