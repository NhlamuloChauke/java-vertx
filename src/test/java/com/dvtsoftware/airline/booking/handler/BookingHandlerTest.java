package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.dto.request.BookingRequest;
import com.dvtsoftware.airline.booking.service.BookingService;
import com.dvtsoftware.airline.booking.utils.DatabaseProvisions;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.dvtsoftware.airline.booking.utils.Constant.TEST_PORT;

@ExtendWith(VertxExtension.class)
public class BookingHandlerTest {

    private WebClient client;

    @BeforeAll
    static void configureGlobalJson() {
        DatabindCodec.mapper().registerModule(new JavaTimeModule());
        DatabindCodec.mapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        client = WebClient.create(vertx);

        DatabaseProvisions.bootstrap(vertx).onSuccess(pool -> {
            BookingService service = new BookingService(pool);
            BookingHandler handler = new BookingHandler(service);

            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());

            router.post("/bookings").handler(handler::createBooking);
            router.get("/bookings/:id").handler(handler::getBookingById);
            router.delete("/bookings/:id").handler(handler::cancelBooking);

            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(TEST_PORT)
                    .onComplete(testContext.succeedingThenComplete());
        }).onFailure(testContext::failNow);
    }

    @Test
    void shouldCreateBooking(VertxTestContext testContext) {
        BookingRequest request = new BookingRequest();
        request.setPassengerId(1L);
        request.setFlightId(1L);
        request.setSeatNumber("15B");

        client.post(TEST_PORT, "localhost", "/bookings")
                .sendJsonObject(JsonObject.mapFrom(request))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(201);
                            // Safety check to avoid Decode Failed error
                            if (response.statusCode() == 201) {
                                JsonObject body = response.bodyAsJsonObject();
                                softly.assertThat(body.getString("bookingReference")).isNotNull();
                            }
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn409ForDuplicate(VertxTestContext testContext) {
        BookingRequest request = new BookingRequest();
        request.setPassengerId(1L);
        request.setFlightId(1L);
        request.setSeatNumber("12A");

        client.post(TEST_PORT, "localhost", "/bookings")
                .sendJsonObject(JsonObject.mapFrom(request))
                .compose(v -> client.post(TEST_PORT, "localhost", "/bookings")
                        .sendJsonObject(JsonObject.mapFrom(request)))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(409);
                            softly.assertThat(response.bodyAsString()).contains("already booked");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn404ForMissingBooking(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/bookings/999")
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

    @Test
    void shouldReturn400WhenPassengerIdMissing(VertxTestContext testContext) {
        JsonObject body = new JsonObject().put("flightId", 1).put("seatNumber", "10A");
        client.post(TEST_PORT, "localhost", "/bookings")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("passengerId");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenSeatNumberIsBlank(VertxTestContext testContext) {
        JsonObject body = new JsonObject().put("passengerId", 1).put("flightId", 1).put("seatNumber", "");
        client.post(TEST_PORT, "localhost", "/bookings")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly ->
                                softly.assertThat(response.statusCode()).isEqualTo(400));
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400ForNonNumericBookingId(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/bookings/xyz")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly ->
                                softly.assertThat(response.statusCode()).isEqualTo(400));
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenFlightIdMissing(VertxTestContext testContext) {
        JsonObject body = new JsonObject().put("passengerId", 1).put("seatNumber", "10B");
        client.post(TEST_PORT, "localhost", "/bookings")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("flightId");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn404WhenCancellingNonExistentBooking(VertxTestContext testContext) {
        client.delete(TEST_PORT, "localhost", "/bookings/99999")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly ->
                                softly.assertThat(response.statusCode()).isEqualTo(404));
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400ForNonNumericIdOnCancel(VertxTestContext testContext) {
        client.delete(TEST_PORT, "localhost", "/bookings/xyz")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly ->
                                softly.assertThat(response.statusCode()).isEqualTo(400));
                        testContext.completeNow();
                    });
                }));
    }
}