package com.dvtsoftware.airline.booking;

import com.dvtsoftware.airline.booking.handler.AirlineHandler;
import com.dvtsoftware.airline.booking.handler.BookingHandler;
import com.dvtsoftware.airline.booking.handler.FlightHandler;
import com.dvtsoftware.airline.booking.handler.PassengerHandler;
import com.dvtsoftware.airline.booking.router.WebRouter;
import com.dvtsoftware.airline.booking.service.*;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    private AirlineHandler airlineHandler;
    private FlightHandler flightHandler;
    private PassengerHandler passengerHandler;
    private BookingHandler bookingHandler;

    @Override
    public void start(Promise<Void> startPromise) {
        // Configure JSON serialization
        configureJsonMapper();

        // Initialize our helper service
        DatabaseService dbService = new DatabaseService(vertx);

        logger.info("Bootstrapping Airline Booking Service...");

        // 2. Load external configuration
        loadConfigs()
                // Step 1: Start H2 Console
                .compose(dbService::startH2Console)
                // Step 2: Migration
                .compose(dbService::runDatabaseMigrations)
                // Step 3: Connection Pool
                .compose(dbService::initSqlClient)
                .compose(this::initializeServices)
                // Step 4: Web Server
                .compose(this::setupRouters)
                .compose(this::startHttpServer)
                .onSuccess(v -> {
                    logger.info("System is fully operational.");
                    startPromise.complete();
                })
                .onFailure(err -> {
                    logger.error("Startup sequence aborted: {}", err.getMessage());
                    startPromise.fail(err);
                });
    }

    /**
     * Configures the global Jackson ObjectMapper used by Vert.x.
     * Registers the JavaTimeModule to handle Java 8 Date/Time types correctly.
     */
    private void configureJsonMapper() {
        DatabindCodec.mapper().registerModule(new JavaTimeModule());
        DatabindCodec.mapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Initializes and starts the HTTP server using the provided configuration.
     * * @param config The JsonObject containing server settings (e.g., port).
     *
     * @return A Future that completes when the server is listening.
     */
    private Future<Void> startHttpServer(JsonObject config) {
        int port = config.getJsonObject("server", new JsonObject()).getInteger("port", 8080);
        Router mainRouter = (Router) config.getValue("_mainRouter");

        logger.info("Starting HTTP server on port {}...", port);

        return vertx.createHttpServer()
                .requestHandler(mainRouter)
                .listen(port)
                .onSuccess(server -> logger.info("Server online at http://localhost:{}", port))
                .mapEmpty();
    }

    /**
     * Attempts to load the configuration from application.json.
     * If the file is missing or invalid, it returns an empty JsonObject
     * to allow the application to fall back on default values.
     * * @return A Future containing the loaded JsonObject.
     */
    private Future<JsonObject> loadConfigs() {
        ConfigStoreOptions store = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", "application.json"));

        ConfigRetriever retriever = ConfigRetriever.create(vertx,
                new ConfigRetrieverOptions().addStore(store));

        return retriever.getConfig()
                .onFailure(err -> logger.warn("Using defaults; could not load config: {}", err.getMessage()))
                .recover(err -> Future.succeededFuture(new JsonObject()));
    }

    /**
     * Initialize services and handlers
     */
    private Future<JsonObject> initializeServices(JsonObject config) {
        logger.info("Initializing services and handlers...");

        try {
            // Initialize Services & Handlers
            Pool client = (Pool) config.getValue("client");

            //services & handlers
            AirlineService airlineService = new AirlineService(client);
            this.airlineHandler = new AirlineHandler(airlineService);

            FlightService flightService = new FlightService(client);
            this.flightHandler = new FlightHandler(flightService);

            PassengerService passengerService = new PassengerService(client);
            this.passengerHandler = new PassengerHandler(passengerService);

            BookingService bookingService = new BookingService(client);
            this.bookingHandler = new BookingHandler(bookingService);

            logger.info("Services and handlers initialized successfully");
            return Future.succeededFuture(config);

        } catch (Exception e) {
            logger.error("Failed to initialize services: {}", e.getMessage(), e);
            return Future.failedFuture("Failed to initialize services: " + e.getMessage());
        }
    }

    private Future<JsonObject> setupRouters(JsonObject config) {
        Router mainRouter = Router.router(vertx);

        // Create the API router
        Router apiRouter = WebRouter.create(vertx, airlineHandler, flightHandler, passengerHandler, bookingHandler);

        // Mount the API router at /
        mainRouter.route().handler(BodyHandler.create());
        mainRouter.route("/*").subRouter(apiRouter);

        config.put("_mainRouter", mainRouter);
        return Future.succeededFuture(config);
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received. Closing Vert.x...");
            vertx.close();
        }));

        vertx.deployVerticle(new MainVerticle())
                .onFailure(t -> {
                    logger.error("Fatal startup error: {}", t.getMessage());
                    System.exit(1);
                });
    }
}