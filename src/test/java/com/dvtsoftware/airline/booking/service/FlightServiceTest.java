package com.dvtsoftware.airline.booking.service;

import com.dvtsoftware.airline.booking.dto.request.FlightRequest;
import com.dvtsoftware.airline.booking.exception.FlightNotFoundException;
import com.dvtsoftware.airline.booking.model.Flight;
import com.dvtsoftware.airline.booking.utils.DatabaseProvisions;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class FlightServiceTest {

    private FlightService flightService;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        DatabaseProvisions.bootstrap(vertx)
                .onSuccess(pool -> {
                    flightService = new FlightService(pool);
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
    }

    @Test
    void testCreateFlight(VertxTestContext testContext) {
        // Arrange
        FlightRequest request = new FlightRequest();
        request.setFlightNumber("SA123");
        request.setAirlineId(1L);
        request.setDepartureAirport("JNB");
        request.setArrivalAirport("CPT");
        request.setDepartureTime("2026-01-10T10:00:00");
        request.setArrivalTime("2026-01-10T12:00:00");
        request.setAvailableSeats(100);
        request.setTotalSeats(100);
        request.setPrice(new BigDecimal("1500.00"));

        // Act
        flightService.createFlight(request)
                .onComplete(testContext.succeeding(flight -> {
                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(flight.getId()).isNotNull();
                            softly.assertThat(flight.getFlightNumber()).isEqualTo("SA123");
                            softly.assertThat(flight.getAvailableSeats()).isEqualTo(100);
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testFindFlightById(VertxTestContext testContext) {
        FlightRequest request = new FlightRequest();
        request.setFlightNumber("BA456");
        request.setAirlineId(1L);
        request.setDepartureAirport("LHR");
        request.setArrivalAirport("JFK");
        request.setDepartureTime("2026-02-10T20:00:00");
        request.setArrivalTime("2026-02-11T08:00:00");
        request.setAvailableSeats(200);
        request.setTotalSeats(200);
        request.setPrice(new BigDecimal("8000.00"));

        // Act
        flightService.createFlight(request)
                .compose(created -> {
                    assertThat(created.getId()).isNotNull();
                    return flightService.findFlightById(created.getId());
                })
                .onComplete(testContext.succeeding(flight -> {
                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(flight.getFlightNumber()).isEqualTo("BA456");
                            softly.assertThat(flight.getDepartureAirport()).isEqualTo("LHR");
                            softly.assertThat(flight.getArrivalAirport()).isEqualTo("JFK");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testFindFlightByIdNotFound(VertxTestContext testContext) {
        Long invalidId = 9999L;
        flightService.findFlightById(invalidId)
                .onComplete(testContext.failing(err -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(err).isInstanceOf(FlightNotFoundException.class);
                            softly.assertThat(err.getMessage()).contains(invalidId.toString());
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testSearchFlights(VertxTestContext testContext) {
        // Arrange
        FlightRequest req = new FlightRequest();
        req.setFlightNumber("SQ001");
        req.setAirlineId(1L);
        req.setDepartureAirport("SIN");
        req.setArrivalAirport("SFO");
        req.setDepartureTime("2026-03-10T10:00:00");
        req.setArrivalTime("2026-03-10T22:00:00");
        req.setAvailableSeats(300);
        req.setTotalSeats(300);
        req.setPrice(new BigDecimal("12000.00"));

        // Act
        flightService.createFlight(req)
                .compose(v -> flightService.searchFlights("SIN", "SFO"))
                .onComplete(testContext.succeeding(list -> {
                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(list).isNotEmpty();
                            softly.assertThat(list)
                                    .extracting(Flight::getFlightNumber)
                                    .contains("SQ001");
                            softly.assertThat(list.getFirst().getDepartureAirport()).isEqualTo("SIN");
                            softly.assertThat(list.getFirst().getArrivalAirport()).isEqualTo("SFO");
                        });
                        testContext.completeNow();
                    });
                }));
    }
}
