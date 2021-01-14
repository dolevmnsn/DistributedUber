FROM gradle:6.7.1-jdk8

WORKDIR /app

COPY settings.gradle build.gradle ./

RUN gradle clean build --no-daemon --stacktrace || true

COPY . .

RUN gradle bootJar --no-daemon

ENTRYPOINT ["java", "-jar", "/app/build/libs/DistributedUber-0.0.1-SNAPSHOT.jar"]


# todo:
#FROM gradle:6.7.1-jdk8 as cache
#RUN mkdir -p /home/gradle/cache_home
#ENV GRADLE_USER_HOME /home/gradle/cache_home
#COPY build.gradle settings.gradle /home/gradle/java-code/
#WORKDIR /home/gradle/java-code
#RUN gradle clean build -i --stacktrace
#
#FROM gradle:6.7.1-jdk8 as builder
#COPY --from=cache /home/gradle/cache_home /home/gradle/.gradle
#WORKDIR /app
#COPY . /app
#RUN gradle bootJar -i --stacktrace

#FROM openjdk:8-jre-alpine
#EXPOSE 8080
#USER root
#WORKDIR /app
#COPY --from=builder /app/build/libs/*.jar ./app.jar
#ENTRYPOINT ["java", "-jar", "app.jar"]