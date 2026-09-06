# ===== 构建阶段 =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
# 镜像内不携带含 API key 明文的 application.yml，运行时全部走环境变量（见 docker-compose.yaml）
RUN rm -f src/main/resources/application.yml
RUN mvn -q package -DskipTests

# ===== 运行阶段 =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
