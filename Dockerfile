FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system appgroup \
    && useradd --system --gid appgroup appuser

COPY --from=build --chown=appuser:appgroup \
    /app/target/orderservice-0.0.1-SNAPSHOT.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]