ARG VERSION=11

FROM azul/zulu-openjdk-alpine:${VERSION} as BUILD

COPY . /src
WORKDIR /src
RUN ./gradlew test
RUN ./gradlew --no-daemon shadowJar

FROM azul/zulu-openjdk-alpine:${VERSION}-jre

COPY --from=BUILD /src/build/libs/kweightly.jar /bin/runner/run.jar
WORKDIR /bin/runner

EXPOSE 9002 9003

CMD ["java", "-XX:MaxRAMPercentage=100", "-jar","run.jar"]