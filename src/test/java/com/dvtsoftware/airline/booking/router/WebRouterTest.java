package com.dvtsoftware.airline.booking.router;

import com.dvtsoftware.airline.booking.handler.AirlineHandler;
import com.dvtsoftware.airline.booking.handler.BookingHandler;
import com.dvtsoftware.airline.booking.handler.FlightHandler;
import com.dvtsoftware.airline.booking.handler.PassengerHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import static com.dvtsoftware.airline.booking.utils.Constant.TEST_PORT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(VertxExtension.class)
public class WebRouterTest {

    private AirlineHandler airlineHandler;
    private FlightHandler flightHandler;
    private PassengerHandler passengerHandler;
    private BookingHandler bookingHandler;
    private WebClient client;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        client = WebClient.create(vertx);
        airlineHandler = Mockito.mock(AirlineHandler.class);
        flightHandler = Mockito.mock(FlightHandler.class);
        passengerHandler = Mockito.mock(PassengerHandler.class);
        bookingHandler = Mockito.mock(BookingHandler.class);

        Router mainRouter = WebRouter.create(vertx, airlineHandler, flightHandler, passengerHandler, bookingHandler);

        vertx.createHttpServer()
                .requestHandler(mainRouter)
                .listen(TEST_PORT)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldRouteToGetAllAirlines(VertxTestContext testContext) {
        doAnswer(invocation -> {
            RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(200).end();
            return null;
        }).when(airlineHandler).getAllAirlines(any());

        client.get(TEST_PORT, "localhost", "/airlines")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(200);
                            verify(airlineHandler).getAllAirlines(any());
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldRouteToSearchFlights(VertxTestContext testContext) {
        doAnswer(invocation -> {
            RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(200).end();
            return null;
        }).when(flightHandler).searchFlights(any());

        client.get(TEST_PORT, "localhost", "/flights/search")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(200);
                            verify(flightHandler).searchFlights(any());
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldRouteToCreateBooking(VertxTestContext testContext) {
        doAnswer(invocation -> {
            RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(201).end();
            return null;
        }).when(bookingHandler).createBooking(any());

        client.post(TEST_PORT, "localhost", "/bookings")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(201);
                            verify(bookingHandler).createBooking(any());
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldRouteToPassengerBookings(VertxTestContext testContext) {
        doAnswer(invocation -> {
            RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(200).end();
            return null;
        }).when(bookingHandler).getBookingsByPassenger(any());

        client.get(TEST_PORT, "localhost", "/passengers/1/bookings")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(200);
                            verify(bookingHandler).getBookingsByPassenger(any());
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldRouteToCancelBooking(VertxTestContext testContext) {
        doAnswer(invocation -> {
            RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(204).end();
            return null;
        }).when(bookingHandler).cancelBooking(any());

        client.delete(TEST_PORT, "localhost", "/bookings/1")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(204);
                            verify(bookingHandler).cancelBooking(any());
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn404ForUnknownPath(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/unknown")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(404);
                        });
                        testContext.completeNow();
                    });
                }));
    }
}