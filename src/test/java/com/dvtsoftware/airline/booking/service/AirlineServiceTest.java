package com.dvtsoftware.airline.booking.service;

import com.dvtsoftware.airline.booking.dto.request.AirlineRequest;
import com.dvtsoftware.airline.booking.exception.AirlineNotFoundException;
import com.dvtsoftware.airline.booking.exception.DuplicateAirlineCodeException;
import com.dvtsoftware.airline.booking.model.Airline;
import com.dvtsoftware.airline.booking.utils.DatabaseProvisions;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(VertxExtension.class)
public class AirlineServiceTest {

    private AirlineService airlineService;
    private Pool pool;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        DatabaseProvisions.bootstrap(vertx)
                .onSuccess(p -> {
                    this.pool = p;
                    this.airlineService = new AirlineService(pool);
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
    void testFindAllAirlines(VertxTestContext testContext) {
        // Act
        airlineService.findAllAirlines()
                .onComplete(testContext.succeeding(airlines -> {
                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(airlines).hasSize(21);
                            softly.assertThat(airlines.getFirst().getName()).isEqualTo("Air France");
                            softly.assertThat(airlines.getFirst().getCode()).isEqualTo("AF");
                            softly.assertThat(airlines).extracting(Airline::getName)
                                    .contains("Emirates", "Ethiopian Airlines", "Singapore Airlines", "British Airways");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testMapRowToAirline(VertxTestContext testContext) {
        // Arrange
        pool.query("SELECT * FROM airlines WHERE code = 'EK'").execute()
                .onComplete(testContext.succeeding(rows -> {
                    // Act
                    Row singleRow = rows.iterator().next();
                    Airline result = airlineService.mapRowToAirline(singleRow);

                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(result.getCode()).isEqualTo("EK");
                            softly.assertThat(result.getName()).isEqualTo("Emirates");
                            softly.assertThat(result.getCountry()).isEqualTo("United Arab Emirates");
                            softly.assertThat(result.getCreatedAt()).isNotNull();
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testMapRowsToList(VertxTestContext testContext) {
        // Arrange
        pool.query("SELECT * FROM airlines").execute()
                .onComplete(testContext.succeeding(rows -> {
                    // Act
                    List<Airline> airlines = airlineService.mapRowsToList(rows);

                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(airlines).isNotEmpty();
                            softly.assertThat(airlines).hasSize(21);
                            // Verify
                            softly.assertThat(airlines.getFirst()).isInstanceOf(Airline.class);
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testCreateAirline(VertxTestContext testContext) {
        // Arrange
        AirlineRequest request = new AirlineRequest("FA", "FlySafair", "South Africa");

        // Act
        airlineService.createAirline(request)
                .onComplete(testContext.succeeding(airline -> {
                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(airline.getId()).isNotNull();
                            softly.assertThat(airline.getCode()).isEqualTo("FA");
                            softly.assertThat(airline.getName()).isEqualTo("FlySafair");
                            softly.assertThat(airline.getCreatedAt()).isNotNull();
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldFailOnDuplicateAirlineCode(VertxTestContext testContext) {
        AirlineRequest duplicateRequest = new AirlineRequest("EK", "Emirates Copy", "UAE");
        // Act
        airlineService.createAirline(duplicateRequest)
                .onComplete(testContext.failing(throwable -> {
                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(throwable).isInstanceOf(DuplicateAirlineCodeException.class);
                            softly.assertThat(throwable.getMessage()).contains("EK");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testFindAirlineById(VertxTestContext testContext) {
        // Arrange
        Long targetId = 1L;

        // Act
        airlineService.findAirlineById(targetId)
                .onComplete(testContext.succeeding(airline -> {
                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(airline).isNotNull();
                            softly.assertThat(airline.getId()).isEqualTo(targetId);
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldFailWhenAirlineIdDoesNotExist(VertxTestContext testContext) {
        // Arrange
        Long nonExistentId = 9999L;

        // Act
        airlineService.findAirlineById(nonExistentId)
                .onComplete(testContext.failing(throwable -> {
                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(throwable).isInstanceOf(AirlineNotFoundException.class);
                            softly.assertThat(throwable.getMessage()).isEqualTo("Airline not found with ID: 9999");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldHandleGenericSqlError(VertxTestContext testContext) {
        String longName = "A".repeat(500);
        AirlineRequest request = new AirlineRequest("LONG", longName, "Country");
        airlineService.createAirline(request)
                .onComplete(testContext.failing(err -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(err).isNotInstanceOf(DuplicateAirlineCodeException.class);
                            softly.assertThat(err.getMessage()).doesNotContain("23505");

                        });
                        testContext.completeNow();
                    });
                }));
    }
}