FROM gradle:7.0.0-jdk16

COPY . /honeydome-broker
WORKDIR /honeydome-broker
ENTRYPOINT ["./gradlew","run"]
