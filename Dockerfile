# -------- Stage 1: Build --------
ARG BACKEND_BUILD_IMAGE=eclipse-temurin:17-jdk-jammy
ARG BACKEND_RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy
FROM ${BACKEND_BUILD_IMAGE} AS build
WORKDIR /workspace

# 先复制构建脚本与 wrapper，利用 Docker 层缓存
COPY gradlew settings.gradle build.gradle gradle.properties* ./
COPY gradle gradle
RUN chmod +x gradlew

# 复制源码并构建可执行 jar（依赖下载与编译一步完成，失败立即可见）
COPY src src
RUN ./gradlew --no-daemon --info clean bootJar -x test \
    && cp build/libs/*.jar app.jar

# -------- Stage 2: Runtime --------
FROM ${BACKEND_RUNTIME_IMAGE}
WORKDIR /app

# 非 root 用户运行
RUN groupadd -r spring && useradd -r -g spring spring
COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R spring:spring /app
USER spring

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai"
EXPOSE 8080

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
