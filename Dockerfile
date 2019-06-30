ARG VERSION=8

FROM openjdk:${VERSION}-jdk as BUILD

COPY . /src
WORKDIR /src
RUN ./gradlew --no-daemon shadowJar

FROM openjdk:${VERSION}-jre-alpine

COPY --from=BUILD /src/build/libs/kweightly-all.jar /bin/runner/run.jar
WORKDIR /bin/runner

EXPOSE 9002 9003

CMD ["java","-jar","run.jar"]