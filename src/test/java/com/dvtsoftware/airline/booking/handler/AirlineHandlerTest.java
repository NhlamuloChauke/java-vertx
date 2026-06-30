package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.dto.request.AirlineRequest;
import com.dvtsoftware.airline.booking.service.AirlineService;
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

import static com.dvtsoftware.airline.booking.utils.Constant.TEST_PORT;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class AirlineHandlerTest {

    private WebClient client;

    /**
     * This is the critical fix.
     * Since MainVerticle isn't deployed, we must manually configure the
     * global Vert.x Jackson mapper to handle Java 8 LocalDateTime.
     */
    @BeforeAll
    static void configureGlobalJson() {
        DatabindCodec.mapper().registerModule(new JavaTimeModule());
        DatabindCodec.mapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        client = WebClient.create(vertx);

        DatabaseProvisions.bootstrap(vertx).onSuccess(pool -> {
            AirlineService service = new AirlineService(pool);
            AirlineHandler handler = new AirlineHandler(service);

            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());

            // Airline Endpoints
            router.get("/airlines").handler(handler::getAllAirlines);
            router.get("/airlines/:id").handler(handler::getAirlineById);
            router.post("/airlines").handler(handler::createAirline);

            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(TEST_PORT)
                    .onComplete(testContext.succeedingThenComplete());
        }).onFailure(testContext::failNow);
    }

    @Test
    void shouldFetchAllAirlines(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/airlines")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        assertThat(response.statusCode()).isEqualTo(200);
                        JsonArray body = response.bodyAsJsonArray();
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(body.size()).isGreaterThan(0);
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldFetchOneAirline(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/airlines/1")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        assertThat(response.statusCode()).isEqualTo(200);
                        JsonObject body = response.bodyAsJsonObject();
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(body.getString("code")).isNotNull();
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldCreateAirline(VertxTestContext testContext) {
        AirlineRequest request = new AirlineRequest("FA", "FlySafair", "South Africa");

        client.post(TEST_PORT, "localhost", "/airlines")
                .sendJsonObject(JsonObject.mapFrom(request))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        assertThat(response.statusCode()).isEqualTo(201);
                        JsonObject body = response.bodyAsJsonObject();
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(body.getString("code")).isEqualTo("FA");
                            softly.assertThat(body.getString("createdAt")).isNotNull();
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturnConflictForDuplicate(VertxTestContext testContext) {
        AirlineRequest request = new AirlineRequest("EK", "Duplicate", "UAE");
        client.post(TEST_PORT, "localhost", "/airlines")
                .sendJsonObject(JsonObject.mapFrom(request))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(409);
                            softly.assertThat(response.bodyAsJsonObject().getString("message"))
                                    .isEqualTo("An airline with code 'EK' already exists.");
                            softly.assertThat(response.bodyAsJsonObject().getInteger("status")).isEqualTo(409);
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn404ForMissingId(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/airlines/99999")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        assertThat(response.statusCode()).isEqualTo(404);
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(404);
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenCodeIsBlank(VertxTestContext testContext) {
        JsonObject body = new JsonObject().put("code", "").put("name", "TestAir").put("country", "ZA");
        client.post(TEST_PORT, "localhost", "/airlines")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("code");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenCodeIsTooLong(VertxTestContext testContext) {
        JsonObject body = new JsonObject().put("code", "TOOLONG").put("name", "TestAir").put("country", "ZA");
        client.post(TEST_PORT, "localhost", "/airlines")
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
    void shouldReturn400WhenNameIsBlank(VertxTestContext testContext) {
        JsonObject body = new JsonObject().put("code", "TX").put("name", "").put("country", "ZA");
        client.post(TEST_PORT, "localhost", "/airlines")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("name");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400ForNonNumericId(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/airlines/abc")
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