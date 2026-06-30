package com.dvtsoftware.airline.booking.service;


import com.dvtsoftware.airline.booking.dto.request.PassengerRequest;
import com.dvtsoftware.airline.booking.exception.DuplicateAirlineCodeException;
import com.dvtsoftware.airline.booking.exception.DuplicatePassportException;
import com.dvtsoftware.airline.booking.exception.PassengerNotFoundException;
import com.dvtsoftware.airline.booking.model.Airline;
import com.dvtsoftware.airline.booking.model.Passenger;
import io.vertx.core.Future;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PassengerService {

    private static final Logger log = LoggerFactory.getLogger(PassengerService.class);
    private final Pool pool;

    private static final String INSERT_PASSENGER_QUERY =
            "INSERT INTO passengers (first_name, last_name, email, phone, passport_number, date_of_birth, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_PASSENGER_BY_ID_QUERY =
            "SELECT id, first_name, last_name, email, phone, passport_number, date_of_birth, created_at, updated_at " +
                    "FROM passengers WHERE id = ?";

    public PassengerService(Pool pool) {
        this.pool = pool;
    }

    /**
     * Persists a new passenger using data from a PassengerRequest.
     */
    public Future<Passenger> createPassenger(PassengerRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate dateOfBirth = LocalDate.parse(request.getDateOfBirth());
        return pool.preparedQuery(INSERT_PASSENGER_QUERY)
                .execute(Tuple.of(
                        request.getFirstName(),
                        request.getLastName(),
                        request.getEmail(),
                        request.getPhone(),
                        request.getPassportNumber(),
                        dateOfBirth,
                        now,
                        now
                ))
                .compose(rows -> {
                    // Get generated ID from JDBC metadata
                    Long generatedId = rows.property(JDBCPool.GENERATED_KEYS).getLong(0);
                    return findById(generatedId);
                })
                .onSuccess(p -> log.info("Passenger {} created successfully", p.getFirstName()))
                .recover(err -> handleCreateError(err, request.getPassportNumber()));
    }

    /**
     * Finds a passenger by ID.
     */
    public Future<Passenger> findById(Long id) {
        return pool.preparedQuery(SELECT_PASSENGER_BY_ID_QUERY)
                .execute(Tuple.of(id))
                .map(rows -> {
                    if (rows.iterator().hasNext()) {
                        return mapRowToPassenger(rows.iterator().next());
                    }
                    throw new PassengerNotFoundException("ID " + id);
                });
    }

    private Passenger mapRowToPassenger(Row row) {
        Passenger passenger = new Passenger();
        passenger.setId(row.getLong(0));
        passenger.setFirstName(row.getString(1));
        passenger.setLastName(row.getString(2));
        passenger.setEmail(row.getString(3));
        passenger.setPhone(row.getString(4));
        passenger.setPassportNumber(row.getString(5));
        passenger.setDateOfBirth(row.getLocalDate(6));
        passenger.setCreatedAt(row.getLocalDateTime(7));
        passenger.setUpdatedAt(row.getLocalDateTime(8));
        return passenger;
    }

    /**
     * Specifically handles SQL errors during creation, mapping database-specific
     * constraints to domain-friendly exceptions.
     */
    private Future<Passenger> handleCreateError(Throwable err, String code) {
        String message = err.getMessage();
        if (message != null && (message.contains("23505") || message.contains("Unique index"))) {
            return Future.failedFuture(new DuplicatePassportException(code));
        }
        return Future.failedFuture(err);
    }
}
