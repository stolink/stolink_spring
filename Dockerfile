# Runtime stage - GitHub Actions에서 빌드된 JAR만 복사
# Multi-stage 빌드 제거: Maven Central rate limiting 회피 및 빌드 캐시 활용
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy pre-built jar file from context (built by GitHub Actions)
COPY --chown=spring:spring build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
