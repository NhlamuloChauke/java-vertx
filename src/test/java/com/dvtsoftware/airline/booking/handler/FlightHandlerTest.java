package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.dto.request.FlightRequest;
import com.dvtsoftware.airline.booking.service.FlightService;
import com.dvtsoftware.airline.booking.utils.DatabaseProvisions;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;

import static com.dvtsoftware.airline.booking.utils.Constant.TEST_PORT;

@ExtendWith(VertxExtension.class)
public class FlightHandlerTest {

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
            FlightService service = new FlightService(pool);
            FlightHandler handler = new FlightHandler(service);

            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());

            // Flights Endpoints
            router.get("/flights/search").handler(handler::searchFlights);
            router.get("/flights/:id").handler(handler::getFlightById);
            router.post("/flights").handler(handler::createFlight);

            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(TEST_PORT)
                    .onComplete(testContext.succeedingThenComplete());
        }).onFailure(testContext::failNow);
    }

    @Test
    void shouldCreateFlight(VertxTestContext testContext) {
        FlightRequest request = new FlightRequest();
        request.setFlightNumber("SA302");
        request.setAirlineId(1L);
        request.setDepartureAirport("JNB");
        request.setArrivalAirport("CPT");
        request.setDepartureTime("2026-05-10T08:00:00");
        request.setArrivalTime("2026-05-10T10:15:00");
        request.setAvailableSeats(150);
        request.setTotalSeats(150);
        request.setPrice(new BigDecimal("1250.50"));

        client.post(TEST_PORT, "localhost", "/flights")
                .sendJsonObject(JsonObject.mapFrom(request))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        JsonObject body = response.bodyAsJsonObject();
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(201);
                            softly.assertThat(body.getString("flightNumber")).isEqualTo("SA302");
                            softly.assertThat(body.getString("status")).isEqualTo("SCHEDULED");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldSearchFlights(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/flights/search")
                .addQueryParam("from", "JNB")
                .addQueryParam("to", "CPT")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        JsonArray body = response.bodyAsJsonArray();
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(200);
                            softly.assertThat(body.size()).isGreaterThanOrEqualTo(0);
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn404ForMissingFlight(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/flights/99999")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(404);
                            softly.assertThat(response.bodyAsString()).contains("not found");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400ForMalformedJson(VertxTestContext testContext) {
        client.post(TEST_PORT, "localhost", "/flights")
                .sendBuffer(io.vertx.core.buffer.Buffer.buffer("{ invalid json }"))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> softly.assertThat(response.statusCode()).isEqualTo(400));
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenFlightNumberMissing(VertxTestContext testContext) {
        JsonObject body = new JsonObject()
                .put("airlineId", 1)
                .put("departureAirport", "JNB")
                .put("arrivalAirport", "CPT")
                .put("departureTime", "2026-05-10T08:00:00")
                .put("arrivalTime", "2026-05-10T10:00:00")
                .put("availableSeats", 100)
                .put("totalSeats", 100)
                .put("price", 500);

        client.post(TEST_PORT, "localhost", "/flights")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("flightNumber");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenDatetimeFormatIsInvalid(VertxTestContext testContext) {
        JsonObject body = new JsonObject()
                .put("flightNumber", "ZA101")
                .put("airlineId", 1)
                .put("departureAirport", "JNB")
                .put("arrivalAirport", "CPT")
                .put("departureTime", "not-a-date")
                .put("arrivalTime", "also-not-a-date")
                .put("availableSeats", 100)
                .put("totalSeats", 100)
                .put("price", 500);

        client.post(TEST_PORT, "localhost", "/flights")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("ISO-8601");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenSearchParamsMissing(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/flights/search")
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
    void shouldReturn400ForNonNumericFlightId(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/flights/abc")
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
    void shouldReturn400WhenAirlineIdMissing(VertxTestContext testContext) {
        JsonObject body = new JsonObject()
                .put("flightNumber", "ZA201")
                .put("departureAirport", "JNB").put("arrivalAirport", "CPT")
                .put("departureTime", "2026-05-10T08:00:00").put("arrivalTime", "2026-05-10T10:00:00")
                .put("availableSeats", 100).put("totalSeats", 100).put("price", 500);
        client.post(TEST_PORT, "localhost", "/flights")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("airlineId");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenDepartureAirportIsBlank(VertxTestContext testContext) {
        JsonObject body = new JsonObject()
                .put("flightNumber", "ZA202").put("airlineId", 1)
                .put("departureAirport", "").put("arrivalAirport", "CPT")
                .put("departureTime", "2026-05-10T08:00:00").put("arrivalTime", "2026-05-10T10:00:00")
                .put("availableSeats", 100).put("totalSeats", 100).put("price", 500);
        client.post(TEST_PORT, "localhost", "/flights")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("departureAirport");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenPriceIsMissing(VertxTestContext testContext) {
        JsonObject body = new JsonObject()
                .put("flightNumber", "ZA203").put("airlineId", 1)
                .put("departureAirport", "JNB").put("arrivalAirport", "CPT")
                .put("departureTime", "2026-05-10T08:00:00").put("arrivalTime", "2026-05-10T10:00:00")
                .put("availableSeats", 100).put("totalSeats", 100);
        client.post(TEST_PORT, "localhost", "/flights")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("price");
                        });
                        testContext.completeNow();
                    });
                }));
    }
}
