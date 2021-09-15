FROM ubuntu:20.04

# Install dependencies
RUN apt update
RUN apt install maven

# Copy source files
RUN mkdir /app
COPY src /app/src
COPY pom.xml /app/pom.xml

# Build
WORKDIR /app
RUN mvn compile assembly:single
CMD java -jar /app/target/cadvisor-scrape-1.0-jar-with-dependencies.jar