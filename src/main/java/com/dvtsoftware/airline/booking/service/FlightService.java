package com.dvtsoftware.airline.booking.service;

import com.dvtsoftware.airline.booking.dto.request.FlightRequest;
import com.dvtsoftware.airline.booking.enums.FlightStatus;
import com.dvtsoftware.airline.booking.exception.FlightNotFoundException;
import com.dvtsoftware.airline.booking.model.Flight;
import io.vertx.core.Future;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FlightService {

    private static final Logger log = LoggerFactory.getLogger(FlightService.class);
    private final Pool pool;

    private static final String CREATE_FLIGHT_QUERY =
            "INSERT INTO flights (flight_number, airline_id, departure_airport, arrival_airport, " +
                    "departure_time, arrival_time, available_seats, total_seats, price, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_FLIGHT_BY_ID_QUERY = "SELECT id, flight_number, airline_id, departure_airport, arrival_airport, departure_time, arrival_time, available_seats, total_seats, price, status, created_at, updated_at FROM flights WHERE id = ?";

    private static final String SEARCH_FLIGHTS_QUERY = "SELECT id, flight_number, airline_id, departure_airport, arrival_airport, departure_time, arrival_time, available_seats, total_seats, price, status, created_at, updated_at FROM flights WHERE departure_airport = ? AND arrival_airport = ?";


    public FlightService(Pool pool) {
        this.pool = pool;
    }

    /**
     * Persists a new flight. Available seats are initialized to total seats.
     */
    public Future<Flight> createFlight(FlightRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return pool.preparedQuery(CREATE_FLIGHT_QUERY)
                .execute(Tuple.of(
                        request.getFlightNumber(),
                        request.getAirlineId(),
                        request.getDepartureAirport().toUpperCase(),
                        request.getArrivalAirport().toUpperCase(),
                        LocalDateTime.parse(request.getDepartureTime()),
                        LocalDateTime.parse(request.getArrivalTime()),
                        request.getAvailableSeats(),
                        request.getTotalSeats(),
                        request.getPrice(),
                        FlightStatus.SCHEDULED.name(),
                        now,
                        now
                ))
                .compose(rows -> {
                    Long generatedId = rows.property(JDBCPool.GENERATED_KEYS).getLong(0);
                    return findFlightById(generatedId);
                })
                .onSuccess(f -> log.info("Flight created: {}", f.getFlightNumber()))
                .recover(err -> {
                    log.error("Failed to create flight: {}", err.getMessage());
                    return Future.failedFuture(err);
                });
    }

    /**
     * Retrieves a flight by ID.
     * * @param id The unique identifier of the flight.
     *
     * @return A Future containing the Flight object.
     * @throws FlightNotFoundException if no flight exists with the given ID.
     */
    public Future<Flight> findFlightById(Long id) {
        return pool.preparedQuery(SELECT_FLIGHT_BY_ID_QUERY)
                .execute(Tuple.of(id))
                .map(rows -> {
                    if (rows.iterator().hasNext()) {
                        return mapRowToFlight(rows.iterator().next());
                    }
                    throw new FlightNotFoundException(id);
                })
                .onFailure(err -> log.error("Lookup failed for flight ID {}: {}", id, err.getMessage()));
    }

    /**
     * Searches for flights based on origin and destination.
     */
    public Future<List<Flight>> searchFlights(String from, String to) {
        return pool.preparedQuery(SEARCH_FLIGHTS_QUERY)
                .execute(Tuple.of(from, to))
                .map(this::mapRowsToList);
    }

    private List<Flight> mapRowsToList(RowSet<Row> rows) {
        List<Flight> flights = new ArrayList<>();
        rows.forEach(row -> flights.add(mapRowToFlight(row)));
        return flights;
    }

    /**
     * Map Row to Flight using Indexing to avoid H2 case issues.
     */
    private Flight mapRowToFlight(Row row) {
        Flight flight = new Flight();
        flight.setId(row.getLong(0));
        flight.setFlightNumber(row.getString(1));
        flight.setAirlineId(row.getLong(2));
        flight.setDepartureAirport(row.getString(3));
        flight.setArrivalAirport(row.getString(4));
        flight.setDepartureTime(row.getLocalDateTime(5));
        flight.setArrivalTime(row.getLocalDateTime(6));
        flight.setAvailableSeats(row.getInteger(7));
        flight.setTotalSeats(row.getInteger(8));
        flight.setPrice(row.getBigDecimal(9));
        String statusStr = row.getString(10);
        if (statusStr != null) {
            flight.setStatus(FlightStatus.valueOf(statusStr));
        }
        flight.setCreatedAt(row.getLocalDateTime(11));
        flight.setUpdatedAt(row.getLocalDateTime(12));
        return flight;
    }
}
