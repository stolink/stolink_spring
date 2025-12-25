# Multi-stage build for Spring Boot application
# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy everything needed for the build
COPY . .

# Run Gradle Wrapper directly using Java (avoids shell script issues)
RUN java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain dependencies --no-daemon

# Build the application
RUN java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Create storage directory
RUN mkdir -p /app/storage/uploads && chown -R spring:spring /app

# Copy the built jar
COPY --from=builder /app/build/libs/*.jar app.jar

# Switch to non-root user
USER spring:spring

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
