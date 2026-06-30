package com.dvtsoftware.airline.booking.service;

import com.dvtsoftware.airline.booking.dto.request.AirlineRequest;
import com.dvtsoftware.airline.booking.exception.AirlineNotFoundException;
import com.dvtsoftware.airline.booking.exception.DuplicateAirlineCodeException;
import com.dvtsoftware.airline.booking.model.Airline;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AirlineService {

    private static final Logger log = LoggerFactory.getLogger(AirlineService.class);
    private final Pool pool;

    private static final String GET_ALL_AIRLINES_QUERY = "SELECT id, code, name, country, created_at, updated_at FROM airlines ORDER BY name ASC";

    private static final String CREATE_AIRLINE_QUERY =
            "INSERT INTO airlines (code, name, country, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";

    private static final String FIND_AIRLINE_BY_QUERY = "SELECT * FROM airlines WHERE id = ?";

    public AirlineService(Pool pool) {
        this.pool = pool;
    }

    /**
     * Persists a new airline.
     *
     * @throws DuplicateAirlineCodeException if the code violates the unique constraint.
     */
    public Future<Airline> createAirline(AirlineRequest request) {
        LocalDateTime now = LocalDateTime.now();

        return pool.preparedQuery(CREATE_AIRLINE_QUERY)
                .execute(Tuple.of(
                        request.getCode().toUpperCase(),
                        request.getName(),
                        request.getCountry(),
                        now,
                        now))
                .compose(rows -> {
                    Long generatedId = rows.property(io.vertx.jdbcclient.JDBCPool.GENERATED_KEYS).getLong(0);
                    return findAirlineById(generatedId);
                })
                .onSuccess(airline -> log.info("Successfully created airline: {}", airline.getCode()))
                .recover(err -> handleCreateError(err, request.getCode()));

    }

    /**
     * Retrieves a specific airline by its unique database identifier.
     *
     * @throws RuntimeException if no airline exists with the provided ID.
     */
    public Future<Airline> findAirlineById(Long id) {
        return pool.preparedQuery(FIND_AIRLINE_BY_QUERY)
                .execute(Tuple.of(id))
                .map(rows -> {
                    if (rows.iterator().hasNext()) {
                        return mapRowToAirline(rows.iterator().next());
                    }
                    throw new AirlineNotFoundException(id);
                }).onFailure(err -> log.error("Lookup failed for airline ID {}: {}", id, err.getMessage()));
    }

    /**
     * Fetches all airlines. Manually maps each Row to an Airline object.
     * * @return A Future containing a list of Airline models.
     */
    public Future<List<Airline>> findAllAirlines() {
        return pool.query(GET_ALL_AIRLINES_QUERY)
                .execute()
                .map(this::mapRowsToList)
                .onSuccess(list -> log.info("Fetched {} airlines from database.", list.size()))
                .onFailure(err -> log.error("Query failed: {}", err.getMessage()));
    }

    /**
     * Helper method to transform the RowSet into a List of Airline objects.
     */
    public List<Airline> mapRowsToList(RowSet<Row> rowSet) {
        List<Airline> airlines = new ArrayList<>();
        for (Row row : rowSet) {
            airlines.add(mapRowToAirline(row));
        }
        return airlines;
    }

    /**
     * Maps a single database Row to the Airline model.
     */
    public Airline mapRowToAirline(Row row) {
        Airline airline = new Airline();
        airline.setId(row.getLong(0));
        airline.setCode(row.getString(1));
        airline.setName(row.getString(2));
        airline.setCountry(row.getString(3));
        airline.setCreatedAt(row.getLocalDateTime(4));
        airline.setUpdatedAt(row.getLocalDateTime(5));
        return airline;
    }

    /**
     * Specifically handles SQL errors during creation, mapping database-specific
     * constraints to domain-friendly exceptions.
     */
    private Future<Airline> handleCreateError(Throwable err, String code) {
        String message = err.getMessage();
        if (message != null && (message.contains("23505") || message.contains("Unique index"))) {
            return Future.failedFuture(new DuplicateAirlineCodeException(code));
        }
        return Future.failedFuture(err);
    }
}
