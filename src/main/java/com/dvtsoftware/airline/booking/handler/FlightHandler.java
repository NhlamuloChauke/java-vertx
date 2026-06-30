package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.dto.request.FlightRequest;
import com.dvtsoftware.airline.booking.dto.response.FlightResponse;
import com.dvtsoftware.airline.booking.exception.ValidationException;
import com.dvtsoftware.airline.booking.model.Flight;
import com.dvtsoftware.airline.booking.service.FlightService;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

public class FlightHandler {

    private static final Logger log = LoggerFactory.getLogger(FlightHandler.class);
    private final FlightService flightService;

    public FlightHandler(FlightService flightService) {
        this.flightService = flightService;
    }

    public void createFlight(RoutingContext ctx) {
        try {
            FlightRequest request = ctx.body().asPojo(FlightRequest.class);
            validate(request);

            flightService.createFlight(request)
                    .map(this::mapToResponse)
                    .onSuccess(response -> ctx.response()
                            .putHeader("content-type", "application/json")
                            .setStatusCode(201)
                            .end(Json.encodePrettily(response)))
                    .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
        } catch (ValidationException e) {
            ErrorHandler.handleFailure(ctx, e);
        } catch (Exception e) {
            log.error("Failed to parse flight request: {}", e.getMessage());
            ErrorHandler.sendError(ctx, 400, "Bad Request", "Malformed JSON body");
        }
    }

    public void getFlightById(RoutingContext ctx) {
        try {
            Long id = Long.valueOf(ctx.pathParam("id"));
            flightService.findFlightById(id)
                    .map(this::mapToResponse)
                    .onSuccess(response -> ctx.response()
                            .putHeader("content-type", "application/json")
                            .setStatusCode(200)
                            .end(Json.encodePrettily(response)))
                    .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
        } catch (NumberFormatException e) {
            ErrorHandler.sendError(ctx, 400, "Bad Request", "Invalid ID format — must be a numeric value");
        }
    }

    public void searchFlights(RoutingContext ctx) {
        String from = ctx.queryParams().get("from");
        String to = ctx.queryParams().get("to");

        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            ErrorHandler.sendError(ctx, 400, "Bad Request", "Query parameters 'from' and 'to' are required");
            return;
        }

        flightService.searchFlights(from.toUpperCase(), to.toUpperCase())
                .map(flights -> flights.stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList()))
                .onSuccess(responseList -> ctx.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(200)
                        .end(Json.encodePrettily(responseList)))
                .onFailure(err -> ErrorHandler.handleFailure(ctx, err));
    }

    private void validate(FlightRequest request) {
        if (request == null) throw new ValidationException("Request body is required");
        if (isBlank(request.getFlightNumber())) throw new ValidationException("'flightNumber' is required");
        if (request.getAirlineId() == null) throw new ValidationException("'airlineId' is required");
        if (isBlank(request.getDepartureAirport())) throw new ValidationException("'departureAirport' is required");
        if (isBlank(request.getArrivalAirport())) throw new ValidationException("'arrivalAirport' is required");
        if (request.getPrice() == null) throw new ValidationException("'price' is required");
        if (request.getTotalSeats() == null) throw new ValidationException("'totalSeats' is required");
        if (request.getAvailableSeats() == null) throw new ValidationException("'availableSeats' is required");
        parseDateTime(request.getDepartureTime(), "departureTime");
        parseDateTime(request.getArrivalTime(), "arrivalTime");
    }

    private void parseDateTime(String value, String fieldName) {
        if (isBlank(value)) throw new ValidationException("'" + fieldName + "' is required");
        try {
            LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException("'" + fieldName + "' must be in ISO-8601 format: yyyy-MM-ddTHH:mm:ss");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private FlightResponse mapToResponse(Flight flight) {
        FlightResponse resp = new FlightResponse();
        resp.setId(flight.getId());
        resp.setFlightNumber(flight.getFlightNumber());
        resp.setAirlineId(flight.getAirlineId());
        resp.setDepartureAirport(flight.getDepartureAirport());
        resp.setArrivalAirport(flight.getArrivalAirport());
        resp.setDepartureTime(flight.getDepartureTime());
        resp.setArrivalTime(flight.getArrivalTime());
        resp.setAvailableSeats(flight.getAvailableSeats());
        resp.setTotalSeats(flight.getTotalSeats());
        resp.setPrice(flight.getPrice());
        resp.setStatus(flight.getStatus().toString());
        return resp;
    }
}
