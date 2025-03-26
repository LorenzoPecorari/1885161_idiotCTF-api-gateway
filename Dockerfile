FROM openjdk:17-jdk-alpine

ARG SUBMISSIONS_MS_HOST_PORT
ARG AUTH_MS_HOST_PORT
ARG CONTESTS_MS_HOST_PORT
ARG CHALLENGES_MS_HOST_PORT

RUN apk add maven

RUN mkdir api-gateway
COPY pom.xml api-gateway/pom.xml 
COPY src/ api-gateway/src/
WORKDIR /api-gateway
RUN sed -i "s/#SUBMISSIONS_MS_HOST_PORT#/${SUBMISSIONS_MS_HOST_PORT}/g" src/main/java/project/labadvancedprogramming/ApiGateway/config/FeignClientConfig.java
RUN sed -i "s/#AUTH_MS_HOST_PORT#/${AUTH_MS_HOST_PORT}/g" src/main/java/project/labadvancedprogramming/ApiGateway/config/FeignClientConfig.java
RUN sed -i "s/#CONTESTS_MS_HOST_PORT#/${CONTESTS_MS_HOST_PORT}/g" src/main/java/project/labadvancedprogramming/ApiGateway/config/FeignClientConfig.java
RUN sed -i "s/#CHALLENGES_MS_HOST_PORT#/${CHALLENGES_MS_HOST_PORT}/g" src/main/java/project/labadvancedprogramming/ApiGateway/config/FeignClientConfig.java
RUN mvn clean package
EXPOSE 8765
ENTRYPOINT [ "java", "-jar", "target/ApiGateway-0.0.1-SNAPSHOT.jar"]
