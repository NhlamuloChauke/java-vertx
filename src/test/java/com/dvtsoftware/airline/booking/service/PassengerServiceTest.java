package com.dvtsoftware.airline.booking.service;

import com.dvtsoftware.airline.booking.dto.request.PassengerRequest;
import com.dvtsoftware.airline.booking.exception.DuplicatePassportException;
import com.dvtsoftware.airline.booking.exception.PassengerNotFoundException;
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

import java.time.LocalDate;

@ExtendWith(VertxExtension.class)
public class PassengerServiceTest {

    private PassengerService passengerService;
    private Pool pool;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        DatabaseProvisions.bootstrap(vertx)
                .onSuccess(p -> {
                    this.pool = p;
                    this.passengerService = new PassengerService(pool);
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
    void testCreateAndFindPassenger(VertxTestContext testContext) {
        PassengerRequest request = new PassengerRequest();
        request.setFirstName("Joe");
        request.setLastName("Doe");
        request.setEmail("joe.d@example.com");
        request.setPhone("+27821112233");
        request.setPassportNumber("P12345678");
        request.setDateOfBirth("1995-06-15");

        passengerService.createPassenger(request)
                .compose(saved -> passengerService.findById(saved.getId()))
                .onComplete(testContext.succeeding(passenger -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(passenger.getFirstName()).isEqualTo("Joe");
                            softly.assertThat(passenger.getLastName()).isEqualTo("Doe");
                            softly.assertThat(passenger.getPassportNumber()).isEqualTo("P12345678");
                            softly.assertThat(passenger.getDateOfBirth()).isEqualTo(LocalDate.of(1995, 6, 15));
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldFailOnDuplicatePassport(VertxTestContext testContext) {
        PassengerRequest request = new PassengerRequest();
        request.setFirstName("Jane");
        request.setLastName("Smith");
        request.setEmail("jane.s@example.com");
        request.setPhone("012345");
        request.setPassportNumber("PASS789");
        request.setDateOfBirth("1985-05-05");

        // Execute sequentially: First succeeds, second with same passport must fail
        passengerService.createPassenger(request)
                .compose(v -> passengerService.createPassenger(request))
                .onComplete(testContext.failing(throwable -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(throwable).isInstanceOf(DuplicatePassportException.class);
                            softly.assertThat(throwable.getMessage()).contains("already exists");
                            softly.assertThat(throwable.getMessage()).contains("PASS789");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldFailWhenPassengerNotFound(VertxTestContext testContext) {
        Long nonExistentId = 9999L;

        passengerService.findById(nonExistentId)
                .onComplete(testContext.failing(throwable -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(throwable).isInstanceOf(PassengerNotFoundException.class);
                            softly.assertThat(throwable.getMessage()).contains("ID 9999");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldHandleUniqueConstraintViolation(VertxTestContext testContext) {
        // Arrange
        PassengerRequest request = new PassengerRequest();
        request.setFirstName("First");
        request.setLastName("Last");
        request.setEmail("test@test.com");
        request.setPhone("123");
        request.setPassportNumber("UNIQUE_PASS_99");
        request.setDateOfBirth("1990-01-01");

        // Act
        passengerService.createPassenger(request)
                .compose(v -> passengerService.createPassenger(request))
                .onComplete(testContext.failing(throwable -> {
                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            // Verifies Branch 1 of handleCreateError
                            softly.assertThat(throwable).isInstanceOf(DuplicatePassportException.class);
                            softly.assertThat(throwable.getMessage()).contains("UNIQUE_PASS_99");
                            softly.assertThat(throwable.getMessage()).contains("already exists");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldHandleGenericSqlErrorInCreate(VertxTestContext testContext) {
        // Arrange
        PassengerRequest badRequest = new PassengerRequest();
        badRequest.setFirstName("NoEmail");
        badRequest.setLastName("User");
        badRequest.setPassportNumber("PASS_NULL_EMAIL");
        badRequest.setDateOfBirth("1990-01-01");
        // Act
        passengerService.createPassenger(badRequest)
                .onComplete(testContext.failing(throwable -> {
                    // Assert
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(throwable).isNotInstanceOf(DuplicatePassportException.class);
                            softly.assertThat(throwable.getMessage()).containsIgnoringCase("null");
                        });
                        testContext.completeNow();
                    });
                }));
    }
}