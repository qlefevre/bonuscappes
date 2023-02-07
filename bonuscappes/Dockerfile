#
# Build stage
#
FROM maven:3.8.7-eclipse-temurin-17-alpine AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package

#
# Package stage
#
FROM eclipse-temurin:17-jre-alpine
COPY --from=build /home/app/target/bonuscappes-1.0-jar-with-dependencies.jar /usr/local/lib/bonuscappes.jar
# limit jvm to 48mb
ENTRYPOINT ["java","-Xmx48m","-jar","/usr/local/lib/bonuscappes.jar"]