# syntax=docker/dockerfile:1

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom 并预热依赖，源码变更时这一层仍能命中缓存
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

# config/ 必须一起拷贝：mvn package 会执行 validate -> checkstyle，
# 缺少 config/checkstyle/checkstyle.xml 会导致构建失败
COPY src ./src
COPY config ./config
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 非 root 运行，降低容器逃逸后的影响面
RUN addgroup -S app && adduser -S -G app app

COPY --from=build /build/target/*.jar app.jar
# 日志目录需在切换用户前建好并授权，否则 logback 无法写滚动日志
RUN mkdir -p /app/logs && chown -R app:app /app

USER app
EXPOSE 8080

# 容器内按 cgroup 限额而非宿主机内存计算堆大小；OOM 时退出让编排层重启
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# alpine 自带 busybox wget，无需额外安装 curl
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
