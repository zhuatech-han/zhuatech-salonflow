# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /work
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2,sharing=locked sh -c 'for attempt in 1 2 3; do mvn -B -q -Dmaven.wagon.http.retryHandler.count=5 dependency:go-offline && exit 0; sleep $((attempt * 5)); done; exit 1'
COPY src src
RUN --mount=type=cache,target=/root/.m2,sharing=locked sh -c 'for attempt in 1 2 3; do mvn -B -q -Dmaven.wagon.http.retryHandler.count=5 spotless:check clean package && exit 0; sleep $((attempt * 5)); done; exit 1'
FROM maven:3.9-eclipse-temurin-21
RUN groupadd --system salonflow && useradd --system --gid salonflow --home-dir /app salonflow
WORKDIR /app
COPY --from=build --chown=salonflow:salonflow /work/target/zhuatech-salonflow.jar app.jar
USER salonflow
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
