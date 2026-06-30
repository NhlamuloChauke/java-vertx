package com.dvtsoftware.airline.booking.service;

import com.dvtsoftware.airline.booking.dto.request.BookingRequest;
import com.dvtsoftware.airline.booking.enums.BookingStatus;
import com.dvtsoftware.airline.booking.exception.BookingNotFoundException;
import com.dvtsoftware.airline.booking.exception.DuplicateBookingException;
import com.dvtsoftware.airline.booking.model.Booking;
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
import java.util.UUID;

public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private final Pool pool;

    private static final String INSERT_BOOKING_QUERY =
            "INSERT INTO bookings (booking_reference, passenger_id, flight_id, seat_number, status, total_amount, booking_date, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, (SELECT price FROM flights WHERE id = ?), ?, ?, ?)";

    private static final String SELECT_BOOKING_BY_ID_QUERY =
            "SELECT id, booking_reference, passenger_id, flight_id, booking_date, seat_number, status, total_amount, created_at, updated_at " +
                    "FROM bookings WHERE id = ?";

    private static final String SELECT_BY_PASSENGER_QUERY =
            "SELECT id, booking_reference, passenger_id, flight_id, booking_date, seat_number, status, total_amount, created_at, updated_at " +
                    "FROM bookings WHERE passenger_id = ?";

    private static final String UPDATE_STATUS_QUERY = "UPDATE bookings SET status = ?, updated_at = ? WHERE id = ?";

    private static final String CHECK_SEAT_AVAILABILITY_QUERY =
            "SELECT COUNT(*) FROM bookings WHERE flight_id = ? AND seat_number = ? AND status != 'CANCELLED'";

    public BookingService(Pool pool) {
        this.pool = pool;
    }

    /**
     * Creates a new booking.
     * Uses a sub-query to fetch the current flight price for the total_amount.
     */
    public Future<Booking> createBooking(BookingRequest request) {
        return pool.preparedQuery(CHECK_SEAT_AVAILABILITY_QUERY)
                .execute(Tuple.of(request.getFlightId(), request.getSeatNumber()))
                .compose(rows -> {
                    long count = rows.iterator().next().getLong(0);
                    if (count > 0) {
                        return Future.failedFuture(new DuplicateBookingException(request.getSeatNumber()));
                    }
                    LocalDateTime now = LocalDateTime.now();
                    String reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

                    return pool.preparedQuery(INSERT_BOOKING_QUERY)
                            .execute(Tuple.of(
                                    reference,
                                    request.getPassengerId(),
                                    request.getFlightId(),
                                    request.getSeatNumber(),
                                    BookingStatus.CONFIRMED.name(),
                                    request.getFlightId(),
                                    now,
                                    now,
                                    now))
                            .compose(insertRows -> {
                                Long generatedId = insertRows.property(JDBCPool.GENERATED_KEYS).getLong(0);
                                return findById(generatedId);
                            });
                })
                .onSuccess(b -> log.info("Booking created: {}", b.getBookingReference()))
                .onFailure(err -> log.error("Booking failed: {}", err.getMessage()));
    }

    /**
     * Retrieves a booking by ID.
     */
    public Future<Booking> findById(Long id) {
        return pool.preparedQuery(SELECT_BOOKING_BY_ID_QUERY)
                .execute(Tuple.of(id))
                .map(rows -> {
                    if (rows.iterator().hasNext()) {
                        return mapRowToBooking(rows.iterator().next());
                    }
                    throw new BookingNotFoundException(id);
                });
    }

    /**
     * Lists all bookings for a specific passenger.
     */
    public Future<List<Booking>> findByPassengerId(Long passengerId) {
        return pool.preparedQuery(SELECT_BY_PASSENGER_QUERY)
                .execute(Tuple.of(passengerId))
                .map(this::mapRowsToList);
    }

    /**
     * Cancels a booking by updating its status.
     */
    public Future<Void> cancelBooking(Long id) {
        return pool.preparedQuery(UPDATE_STATUS_QUERY)
                .execute(Tuple.of(BookingStatus.CANCELLED.name(), LocalDateTime.now(), id))
                .compose(rows -> {
                    if (rows.rowCount() > 0) return Future.succeededFuture();
                    return Future.failedFuture(new BookingNotFoundException(id));
                });
    }

    private List<Booking> mapRowsToList(RowSet<Row> rows) {
        List<Booking> list = new ArrayList<>();
        rows.forEach(row -> list.add(mapRowToBooking(row)));
        return list;
    }

    private Booking mapRowToBooking(Row row) {
        Booking booking = new Booking();
        booking.setId(row.getLong(0));
        booking.setBookingReference(row.getString(1));
        booking.setPassengerId(row.getLong(2));
        booking.setFlightId(row.getLong(3));
        booking.setBookingDate(row.getLocalDateTime(4));
        booking.setSeatNumber(row.getString(5));
        booking.setStatus(row.getString(6));
        booking.setTotalAmount(row.getBigDecimal(7));
        booking.setCreatedAt(row.getLocalDateTime(8));
        booking.setUpdatedAt(row.getLocalDateTime(9));
        return booking;
    }
}
