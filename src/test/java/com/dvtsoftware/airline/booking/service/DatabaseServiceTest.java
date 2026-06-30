package com.dvtsoftware.airline.booking.service;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Pool;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class DatabaseServiceTest {

    private DatabaseService databaseService;
    private JsonObject testConfig;

    @BeforeEach
    void setUp(Vertx vertx) {
        databaseService = new DatabaseService(vertx);

        testConfig = new JsonObject()
                .put("server", new JsonObject()
                        .put("host", "localhost")
                        .put("port", 8080))
                .put("database", new JsonObject()
                        .put("url", "jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1")
                        .put("user", "sa")
                        .put("password", "")
                        .put("poolSize", "3"))
                .put("flyway", new JsonObject()
                        .put("migration-path", "db/migration")
                        .put("baseline-on-migrate", "true"))
                .put("h2", new JsonObject()
                        .put("console-port", "8083"));
    }

    @Test
    void testRunDatabaseMigrations(VertxTestContext checkpoint) {
        databaseService.runDatabaseMigrations(testConfig)
                .onComplete(checkpoint.succeeding(config -> {
                    SoftAssertions.assertSoftly(softly -> {
                        softly.assertThat(config).isNotNull();
                        softly.assertThat(config.getJsonObject("database").getString("user")).isEqualTo("sa");
                    });
                    checkpoint.completeNow();
                }));
    }

    @Test
    void testRunDatabaseMigrationsMissingDb(VertxTestContext checkpoint) {
        JsonObject config = new JsonObject().put("flyway", new JsonObject());

        databaseService.runDatabaseMigrations(config)
                .onComplete(checkpoint.failing(throwable -> {
                    assertThat(throwable).isInstanceOf(IllegalStateException.class);
                    checkpoint.completeNow();
                }));
    }

    @Test
    void testRunDatabaseMigrationsMissingFlyway(VertxTestContext checkpoint) {
        JsonObject config = new JsonObject().put("database", new JsonObject());

        databaseService.runDatabaseMigrations(config)
                .onComplete(checkpoint.failing(throwable -> {
                    assertThat(throwable).isInstanceOf(IllegalStateException.class);
                    checkpoint.completeNow();
                }));
    }

    @Test
    void testInitSqlClient(VertxTestContext checkpoint) {
        databaseService.initSqlClient(testConfig)
                .onComplete(checkpoint.succeeding(config -> {
                    Object client = config.getValue("client");
                    SoftAssertions.assertSoftly(softly -> {
                        softly.assertThat(config.containsKey("client")).isTrue();
                        softly.assertThat(client).isInstanceOf(Pool.class);
                    });
                    checkpoint.completeNow();
                }));
    }

    @Test
    void testStartH2Console(VertxTestContext checkpoint) {
        databaseService.startH2Console(testConfig)
                .onComplete(checkpoint.succeeding(config -> {
                    SoftAssertions.assertSoftly(softly -> softly.assertThat(config).isNotNull());
                    checkpoint.completeNow();
                }));
    }
}