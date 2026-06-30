package com.dvtsoftware.airline.booking.utils;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the lifecycle of the volatile H2 data layer.
 * Specifically handles schema provisioning and client connectivity.
 */
public class DatabaseProvisions {

    private static final Logger log = LoggerFactory.getLogger(DatabaseProvisions.class);

    private static final String MEM_JDBC_URL = "jdbc:h2:mem:airline_system_db;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";
    private static final String AUTH_USER = "sa";
    private static final String AUTH_PWD = "";
    private static final String SCHEMA_PATH = "db/migration";

    /**
     * Provisions a fresh database instance.
     * Triggers a 'clean-migrate' cycle before establishing the connection pool.
     */
    public static Future<Pool> bootstrap(Vertx vertx) {
        log.info("Provisioning localized H2 data source...");

        return executeSchemaRefresh(vertx)
                .compose(v -> initializeClient(vertx))
                .onSuccess(p -> log.info("Data layer bootstrap finalized."))
                .onFailure(cause -> log.error("Bootstrap sequence interrupted: {}", cause.getMessage()));
    }

    /**
     * Resets and migrates the schema on a worker thread to avoid event-loop blocking.
     */
    private static Future<Void> executeSchemaRefresh(Vertx vertx) {
        return vertx.executeBlocking(() -> {
            log.debug("Applying Flyway migrations from {}...", SCHEMA_PATH);

            Flyway.configure()
                    .dataSource(MEM_JDBC_URL, AUTH_USER, AUTH_PWD)
                    .locations(SCHEMA_PATH)
                    .cleanDisabled(false)
                    .load()
                    .clean();

            Flyway.configure()
                    .dataSource(MEM_JDBC_URL, AUTH_USER, AUTH_PWD)
                    .locations(SCHEMA_PATH)
                    .load()
                    .migrate();

            return null;
        });
    }

    /**
     * Configures the reactive JDBC client with standard pooling parameters.
     */
    private static Future<Pool> initializeClient(Vertx vertx) {
        JDBCConnectOptions connectOptions = new JDBCConnectOptions()
                .setJdbcUrl(MEM_JDBC_URL)
                .setUser(AUTH_USER)
                .setPassword(AUTH_PWD);

        PoolOptions capacity = new PoolOptions().setMaxSize(5);

        try {
            return Future.succeededFuture(JDBCPool.pool(vertx, connectOptions, capacity));
        } catch (Exception ex) {
            return Future.failedFuture(ex);
        }
    }

    /**
     * Safely releases database resources.
     */
    public static Future<Void> terminate(Pool pool) {
        if (pool == null) {
            return Future.succeededFuture();
        }
        log.debug("Decommissioning database pool...");
        return pool.close();
    }
}