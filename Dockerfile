# Multi-stage build for Local Development
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
# Ensure gradlew is executable and fix line endings (for Windows hosts)
RUN chmod +x gradlew && sed -i 's/\r$//' gradlew
# Build the application
RUN ./gradlew clean build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built jar from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application with optimized JVM settings for 4GB RAM
# -Xms: Initial heap size (1.5GB)
# -Xmx: Maximum heap size (2.5GB, 62.5% of total RAM - balanced setting)
# -XX:+UseG1GC: Use G1 Garbage Collector
# -XX:MaxGCPauseMillis: Target max GC pause time
# -XX:+UseContainerSupport: Detect container memory limits
# -XX:MaxMetaspaceSize: Limit Metaspace to 256MB
ENTRYPOINT ["java", \
    "-Xms1536m", \
    "-Xmx2560m", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxMetaspaceSize=256m", \
    "-jar", "app.jar"]
