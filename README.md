# Tourding Backend

Tourding는 관광지 검색, 위치 기반 탐색, 경로 조회, 날씨 조회, 사용자 관리를 제공하는 Spring Boot 백엔드입니다. 외부 관광 API, 지도 API, 날씨 API를 조합해 여행/라이딩 시나리오에 필요한 데이터를 제공합니다.

## 프로젝트 개요

- Framework: Spring Boot 3.5 / Java 17
- Build: Gradle
- Database: MySQL
- Container: Docker
- Registry: GitHub Container Registry (`GHCR`)
- Runtime domain: `https://tourding.walab.info`
- Swagger UI: `https://tourding.walab.info/swagger-ui/index.html`

주요 기능:

- 관광지 검색
- 위치 기반 관광지 탐색
- 경로/가이드/추천 코스 조회
- 날씨 조회
- 사용자 생성, 조회, 수정, 삭제

## 로컬 실행

```bash
./gradlew bootRun
```

또는 Docker 기반으로 실행할 수 있습니다.

```bash
docker compose up -d
```

운영 배포는 로컬 `docker-compose.yml`이 아니라 `docker-compose.prod.yml`을 사용합니다.

## 배포 환경

현재 운영 배포는 학교 서버에 구성되어 있습니다.

- App host: `contest.walab.info`
- App directory: `/home/tourding`
- Reverse proxy: Nginx on host machine
- TLS: Certbot
- Container port binding: `127.0.0.1:18080 -> 8080`
- Public domain: `https://tourding.walab.info`

배포 구조:

```text
Internet
  -> Nginx (host)
  -> 127.0.0.1:18080
  -> tourding-prod-app container:8080
  -> remote MySQL
```

운영 Nginx 설정은 서버에서 수동 관리합니다. 이 레포의 GitHub Actions는 Nginx 설정을 수정하지 않습니다.

## CI/CD 파이프라인

배포 워크플로우는 [`/.github/workflows/deploy.yml`](.github/workflows/deploy.yml) 에 있습니다.

트리거:

- `main` 브랜치 push
- `workflow_dispatch`

배포 순서:

1. GitHub Actions가 레포를 checkout 합니다.
2. `DOCKER_IMAGE` 변수를 읽고 이미지 태그를 계산합니다.
3. Docker Buildx로 이미지를 빌드합니다.
4. GHCR에 아래 두 태그로 push 합니다.
   - `latest`
   - `sha-<commit>`
5. GitHub Secret `ENV_FILE` 내용을 런타임 env 파일로 준비합니다.
6. 서버 `/home/tourding/incoming`에 아래 파일만 업로드합니다.
   - `runtime.env`
   - `docker-compose.prod.yml`
7. 서버에서 GHCR 로그인 후 `docker compose --env-file ... pull` 을 수행합니다.
8. 서버에서 `docker compose --env-file ... up -d --remove-orphans` 를 수행합니다.
9. 컨테이너 실행 상태를 확인합니다.
10. 임시 env 파일을 삭제합니다.

중요한 운영 원칙:

- 소스 코드를 서버에 복사해서 빌드하지 않습니다.
- 이미지는 GHCR에서 pull 합니다.
- `.env`는 서버에 영구 저장하지 않습니다.
- Nginx와 Certbot은 서버에서 수동 관리합니다.

## GitHub Actions 설정

### Repository or Environment Secrets

아래 값들은 `Secrets`에 넣습니다.

| Name | 설명 |
|---|---|
| `SERVER_HOST` | SSH 접속 대상 호스트 |
| `SERVER_USER` | SSH 사용자 |
| `SERVER_PASSWORD` | SSH 로그인 비밀번호 |
| `SUDO_PASSWORD` | `sudo` 비밀번호 |
| `GHCR_USERNAME` | GHCR 토큰을 발급한 GitHub 계정명 |
| `GHCR_TOKEN` | GHCR pull 용 토큰 |
| `ENV_FILE` | 운영 환경변수 전체 내용을 담은 멀티라인 시크릿 |

### Repository or Environment Variables

아래 값들은 `Variables`에 넣습니다.

| Name | 예시 | 설명 |
|---|---|---|
| `DOCKER_IMAGE` | `ghcr.io/giljjang/tourding` | 배포할 이미지 경로 |
| `DEPLOY_PATH` | `/home/tourding` | 서버 배포 디렉터리 |
| `APP_BIND_PORT` | `18080` | 호스트에 바인딩할 로컬 포트 |

## ENV_FILE 구성

`ENV_FILE`은 GitHub Secret에 저장하는 운영용 멀티라인 env 텍스트입니다. 이 값은 배포 시 임시 파일로만 생성되고, `docker compose --env-file`로 사용된 뒤 삭제됩니다.

예시 형식:

```env
MYSQL_URL=jdbc:mysql://db-host:3306/tourding?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
MYSQL_USERNAME=...
MYSQL_PASSWORD=...
MYSQL_DRIVER=com.mysql.cj.jdbc.Driver
MYSQL_DIALECT=org.hibernate.dialect.MySQL8Dialect

SWAGGER_SERVER_URL=https://tourding.walab.info
SWAGGER_USER=...
SWAGGER_PASS=...

SPRING_PROFILES_ACTIVE=prod
SPRING_APPLICATION_NAME=tourding
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=false

NAVER_CLIENT_ID=...
NAVER_CLIENT_SECRET=...
TOUR_CLIENT_SERVICEKEY=...
KAKAO_CLIENT_KAKAOAK=...
OPEN_ROUTE_SERVICE_KEY=...
OPEN_WEATHER_MAP_KEY=...

APPLE_KEY_ID=...
APPLE_ISS=...
APPLE_BUNDLE_ID=...
APPLE_PRIVATE_KEY=...

JAVA_OPTS=-Xms256m -Xmx512m
```

### 필수 환경변수 설명

#### Database

| Name | 설명 |
|---|---|
| `MYSQL_URL` | MySQL JDBC URL. 반드시 DB 이름까지 포함해야 합니다. |
| `MYSQL_USERNAME` | DB 사용자명 |
| `MYSQL_PASSWORD` | DB 비밀번호 |
| `MYSQL_DRIVER` | JDBC 드라이버 클래스명 |
| `MYSQL_DIALECT` | Hibernate dialect |

`MYSQL_URL` 예시:

```text
jdbc:mysql://db-host:3306/tourding?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
```

DB 이름이 빠지면 `No database selected` 오류가 발생합니다.

#### Spring / Runtime

| Name | 설명 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 |
| `SPRING_APPLICATION_NAME` | 애플리케이션 이름 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Hibernate DDL 전략 |
| `SPRING_JPA_SHOW_SQL` | SQL 로그 출력 여부 |
| `JAVA_OPTS` | JVM 메모리 옵션 |

#### Swagger

| Name | 설명 |
|---|---|
| `SWAGGER_SERVER_URL` | OpenAPI 문서에 노출할 운영 서버 URL |
| `SWAGGER_USER` | Swagger UI Basic Auth 아이디 |
| `SWAGGER_PASS` | Swagger UI Basic Auth 비밀번호 |

주의:

- `SWAGGER_SERVER_URL`은 반드시 `https://tourding.walab.info` 형태로 넣어야 합니다.
- 도메인 오타가 있으면 Swagger "Try it out" 요청이 잘못된 origin으로 나가며 CORS처럼 보이는 오류가 발생합니다.

#### External API Keys

| Name | 설명 |
|---|---|
| `NAVER_CLIENT_ID` | Naver API client id |
| `NAVER_CLIENT_SECRET` | Naver API secret |
| `TOUR_CLIENT_SERVICEKEY` | Tour API 인증키 |
| `KAKAO_CLIENT_KAKAOAK` | Kakao API 키 |
| `OPEN_ROUTE_SERVICE_KEY` | OpenRouteService API 키 |
| `OPEN_WEATHER_MAP_KEY` | OpenWeatherMap API 키 |

#### Apple Login

| Name | 설명 |
|---|---|
| `APPLE_KEY_ID` | Apple key id |
| `APPLE_ISS` | Apple issuer id |
| `APPLE_BUNDLE_ID` | Apple bundle id |
| `APPLE_PRIVATE_KEY` | Apple private key |

## 운영용 Docker Compose

운영 compose 파일은 [`docker-compose.prod.yml`](docker-compose.prod.yml) 입니다.

특징:

- 앱 컨테이너만 실행
- DB 컨테이너는 띄우지 않음
- `127.0.0.1:${APP_BIND_PORT}:8080` 로만 바인딩
- 환경변수는 `--env-file`로만 주입
- 로그 로테이션 설정 포함

## Swagger 및 보안 설정

- Swagger UI: `https://tourding.walab.info/swagger-ui/index.html`
- API Docs: `https://tourding.walab.info/v3/api-docs`
- Swagger UI는 Basic Auth 보호 대상입니다.
- `/v3/api-docs`는 UI 렌더링을 위해 공개됩니다.
- CORS 허용 origin은 [`SecurityConfig.java`](src/main/java/com/example/tourding/security/SecurityConfig.java) 에서 관리합니다.

## 서버 수동 작업

이 레포의 자동 배포 범위에 포함되지 않는 작업:

- Nginx server block 수정
- Certbot 인증서 발급 및 갱신 확인
- DNS 설정

초기 세팅 이후 점검 명령:

```bash
sudo docker ps
curl -I http://127.0.0.1:18080
curl -I https://tourding.walab.info
sudo nginx -t
sudo certbot certificates
```

## 주의사항

- 실제 비밀값은 절대 README, 코드, Git에 커밋하지 마세요.
- GitHub Actions 로그에 토큰이나 비밀번호를 출력하지 마세요.
- GHCR 이미지 pull 계정은 `GHCR_TOKEN`을 발급한 계정명과 일치해야 합니다.
- `SERVER_PASSWORD`와 `SUDO_PASSWORD`는 현재 배포 구조에서 모두 필요합니다.
- 운영 도메인은 `tourding.walab.info` 하나만 사용합니다.
