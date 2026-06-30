package com.dvtsoftware.airline.booking.service;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.flywaydb.core.Flyway;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    private final Vertx vertx;

    public DatabaseService(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Executes database schema migrations using Flyway.
     * <p>
     * This method runs as a blocking operation within a worker thread to ensure the
     * database schema is fully updated before the application starts accepting traffic.
     * It validates the presence of mandatory database settings and utilizes the
     * migration path defined in the configuration.
     * </p>
     *
     * @param config The global {@link JsonObject} containing "database" and "flyway" keys.
     * @return A {@link Future} that resolves to the original config object upon successful migration.
     * @throws IllegalStateException if the "database" or "flyway" configurations section are missing or empty.
     */
    public Future<JsonObject> runDatabaseMigrations(JsonObject config) {
        return vertx.executeBlocking(() -> {

            JsonObject dbSettings = config.getJsonObject("database");
            JsonObject flywaySettings = config.getJsonObject("flyway");

            if (dbSettings == null || flywaySettings == null) {
                throw new IllegalStateException("Database or Flyway configuration is missing");
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(
                            dbSettings.getString("url"),
                            dbSettings.getString("user"),
                            dbSettings.getString("password"))
                    .locations(flywaySettings.getString("migration-path"))
                    .baselineOnMigrate(Boolean.parseBoolean(flywaySettings.getString("baseline-on-migrate")))
                    .load();

            flyway.migrate();
            log.info("Schema is up to date.");

            return config;
        }, true);
    }

    /**
     * Initializes the Reactive JDBC Client Pool.
     * <p>
     * Creates a {@link Pool} using the JDBC connection options and pool settings
     * defined in the configuration. The resulting client is attached to the
     * configuration object under the key "dbClient" for use in subsequent services.
     * </p>
     *
     * @param config The global {@link JsonObject} containing database credentials and pool constraints.
     * @return A {@link Future} containing the configuration object with the active SQL client.
     */
    public Future<JsonObject> initSqlClient(JsonObject config) {
        JsonObject dbSettings = config.getJsonObject("database");

        JDBCConnectOptions connectOptions = new JDBCConnectOptions()
                .setJdbcUrl(dbSettings.getString("url"))
                .setUser(dbSettings.getString("user"))
                .setPassword(dbSettings.getString("password"));

        PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(Integer.parseInt(dbSettings.getString("poolSize")));

        Pool sqlClient = JDBCPool.pool(vertx, connectOptions, poolOptions);
        config.put("client", sqlClient);

        log.info("Reactive SQL Pool initialized with max size {}", poolOptions.getMaxSize());
        return Future.succeededFuture(config);
    }

    /**
     * Starts the H2 Web Console server.
     * Returns the config in a succeeded future regardless of console success
     * to ensure the application startup sequence is not blocked.
     * * @param config The application configuration.
     *
     * @return A Future containing the configuration.
     */
    public Future<JsonObject> startH2Console(JsonObject config) {
        return vertx.executeBlocking(() -> {

            JsonObject h2ConsoleSettings = config.getJsonObject("h2");
            JsonObject serverSettings = config.getJsonObject("server");

            String h2ConsolePort = h2ConsoleSettings.getString("console-port");
            try {
                log.info("Attempting to launch H2 Web Console...");

                Server.createWebServer(
                                "-web",
                                "-webAllowOthers",
                                "-webPort", h2ConsolePort)
                        .start();

                log.info("H2 Console is now active at http://{}:{}", serverSettings.getString("host"), h2ConsolePort);
            } catch (Exception e) {
                log.warn("H2 Console failed to start (Port {} might be busy): {}", serverSettings.getString("port"), e.getMessage());
            }
            return config;
        }, false);
    }
}