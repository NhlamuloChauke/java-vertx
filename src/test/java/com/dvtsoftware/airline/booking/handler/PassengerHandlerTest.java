package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.dto.request.PassengerRequest;
import com.dvtsoftware.airline.booking.service.PassengerService;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.dvtsoftware.airline.booking.utils.Constant.TEST_PORT;

@ExtendWith(VertxExtension.class)
public class PassengerHandlerTest {

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
            PassengerService service = new PassengerService(pool);
            PassengerHandler handler = new PassengerHandler(service);

            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());

            // Passenger Endpoints
            router.post("/passengers").handler(handler::createPassenger);

            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(TEST_PORT)
                    .onComplete(testContext.succeedingThenComplete());
        }).onFailure(testContext::failNow);
    }

    @Test
    void shouldCreatePassenger(VertxTestContext testContext) {
        PassengerRequest request = new PassengerRequest();
        request.setFirstName("Alice");
        request.setLastName("Wonderland");
        request.setEmail("alice@example.com");
        request.setPhone("123456");
        request.setPassportNumber("ALICE789");
        request.setDateOfBirth("1992-12-12");

        client.post(TEST_PORT, "localhost", "/passengers")
                .sendJsonObject(JsonObject.mapFrom(request))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(201);
                            JsonObject body = response.bodyAsJsonObject();
                            softly.assertThat(body.getString("passportNumber")).isEqualTo("ALICE789");
                            softly.assertThat(body.getString("createdAt")).isNotNull();
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturnConflictForDuplicatePassport(VertxTestContext testContext) {
        PassengerRequest request = new PassengerRequest();
        request.setFirstName("Bob");
        request.setLastName("Builder");
        request.setEmail("bob@example.com");
        request.setPassportNumber("BOB123");
        request.setDateOfBirth("1980-08-08");

        // Create first, then attempt second
        client.post(TEST_PORT, "localhost", "/passengers")
                .sendJsonObject(JsonObject.mapFrom(request))
                .compose(v -> client.post(TEST_PORT, "localhost", "/passengers")
                        .sendJsonObject(JsonObject.mapFrom(request)))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(409);
                            softly.assertThat(response.bodyAsString()).contains("already exists");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn404ForMissingPassenger(VertxTestContext testContext) {
        client.get(TEST_PORT, "localhost", "/passengers/99999")
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
    void shouldReturn400WhenFirstNameIsBlank(VertxTestContext testContext) {
        JsonObject body = new JsonObject()
                .put("firstName", "")
                .put("lastName", "Doe")
                .put("email", "test@example.com")
                .put("passportNumber", "P999888");

        client.post(TEST_PORT, "localhost", "/passengers")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("firstName");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenPassportNumberIsBlank(VertxTestContext testContext) {
        JsonObject body = new JsonObject()
                .put("firstName", "John").put("lastName", "Doe")
                .put("email", "john@example.com").put("passportNumber", "");
        client.post(TEST_PORT, "localhost", "/passengers")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("passportNumber");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldReturn400WhenEmailIsInvalid(VertxTestContext testContext) {
        JsonObject body = new JsonObject()
                .put("firstName", "John")
                .put("lastName", "Doe")
                .put("email", "not-an-email")
                .put("passportNumber", "P777666");

        client.post(TEST_PORT, "localhost", "/passengers")
                .sendJsonObject(body)
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(400);
                            softly.assertThat(response.bodyAsJsonObject().getString("message")).contains("email");
                        });
                        testContext.completeNow();
                    });
                }));
    }
}