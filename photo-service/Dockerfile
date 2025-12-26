FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Build spiffe-common first
COPY spiffe-common/pom.xml ./spiffe-common/
COPY spiffe-common/src ./spiffe-common/src
WORKDIR /app/spiffe-common
RUN mvn clean install -DskipTests

# Build photo-service
WORKDIR /app
COPY photo-service/pom.xml .
COPY photo-service/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:UseSVE=0", "-jar", "app.jar"]

