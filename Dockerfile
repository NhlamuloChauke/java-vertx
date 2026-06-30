# Build stage with JDK
FROM eclipse-temurin:21-jdk-jammy AS build

# Install Maven if not using wrapper
RUN apt-get update && apt-get install -y maven

WORKDIR /workspace/app

# Copy the entire project including mvnw if it exists
COPY . .

# Run Maven build - try mvn first, fallback to ./mvnw
RUN if [ -f "pom.xml" ]; then \
        if command -v mvn >/dev/null 2>&1; then \
            mvn clean package -DskipTests; \
        elif [ -f "./mvnw" ]; then \
            chmod +x ./mvnw && ./mvnw clean package -DskipTests; \
        else \
            echo "No Maven found. Please install Maven or add mvnw to project." && exit 1; \
        fi \
    else \
        echo "No pom.xml found. Not a Maven project." && exit 1; \
    fi

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/app/target/*.jar app.jar
EXPOSE 8080 8082
ENTRYPOINT ["java", "-jar", "app.jar"]