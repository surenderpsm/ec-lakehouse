# cart-service image. Build the jar first, from the api/ folder:
#   ./mvnw -pl cart-service -am package -DskipTests
# then:  docker build -t ec-lakehouse/cart-service cart-service
FROM eclipse-temurin:21-jre AS extract
WORKDIR /build
COPY target/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
WORKDIR /app
# dependency layers change least often, so they come first and stay cached
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER app
ENV PORT=8084 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8084
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:${PORT}/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
