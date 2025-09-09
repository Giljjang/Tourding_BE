# 멀티스테이지 빌드를 위한 베이스 이미지
FROM eclipse-temurin:17-jdk AS build

# 작업 디렉토리 설정
WORKDIR /app

# Gradle 래퍼와 빌드 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 의존성 다운로드 (캐시 레이어 최적화)
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사
COPY src src

# 애플리케이션 빌드
RUN ./gradlew build -x test --no-daemon

# 런타임 이미지
FROM eclipse-temurin:17-jre

# 메타데이터 설정
LABEL maintainer="tourding-team"
LABEL version="1.0"
LABEL description="Tourding Spring Boot Application"

# 작업 디렉토리 설정
WORKDIR /app

# 포트 노출
EXPOSE 8080

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# JVM 옵션 설정
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# 헬스체크 추가 (curl이 없을 수 있으므로 wget 사용)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
