package com.dvtsoftware.airline.booking.service;

import com.dvtsoftware.airline.booking.dto.request.BookingRequest;
import com.dvtsoftware.airline.booking.enums.BookingStatus;
import com.dvtsoftware.airline.booking.exception.BookingNotFoundException;
import com.dvtsoftware.airline.booking.exception.DuplicateBookingException;
import com.dvtsoftware.airline.booking.utils.DatabaseProvisions;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Pool;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class BookingServiceTest {

    private BookingService bookingService;
    private Pool pool;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        DatabaseProvisions.bootstrap(vertx)
                .onSuccess(p -> {
                    this.pool = p;
                    this.bookingService = new BookingService(pool);
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        DatabaseProvisions.terminate(pool)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldCreateBooking(VertxTestContext testContext) {
        BookingRequest request = new BookingRequest();
        request.setPassengerId(1L);
        request.setFlightId(1L);
        request.setSeatNumber("14C");

        bookingService.createBooking(request)
                .onComplete(testContext.succeeding(booking -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(booking.getId()).isNotNull();
                            softly.assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED.name());
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldFailOnDuplicateSeat(VertxTestContext testContext) {
        BookingRequest request = new BookingRequest();
        request.setPassengerId(1L);
        request.setFlightId(1L);
        request.setSeatNumber("12A");

        // First creation succeeds, second with same details must fail
        bookingService.createBooking(request)
                .compose(v -> bookingService.createBooking(request))
                .onComplete(testContext.failing(throwable -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(throwable).isInstanceOf(DuplicateBookingException.class);
                            softly.assertThat(throwable.getMessage()).contains("already booked");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldThrowNotFoundOnInvalidId(VertxTestContext testContext) {
        bookingService.findById(999L)
                .onComplete(testContext.failing(err -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(err).isInstanceOf(BookingNotFoundException.class);
                        });
                        testContext.completeNow();
                    });
                }));
    }
}