# 투어딩 AI 경로 조정 기능 개발 기획서

## 1. 목적

투어딩에 AI 기반 경로 조정 기능을 추가한다.

사용자는 주행 중 음성 또는 텍스트로 불편사항을 말하고, 서버는 이를 경로 조정 의도로 변환한 뒤 OpenRouteService의 실제 경로 속성값을 기반으로 후보 경로를 채점한다. AI는 경로를 직접 생성하지 않고, 사용자의 자연어 요청을 채점 가중치와 처리 액션으로 변환하는 역할만 담당한다.

핵심 원칙:

- 공공데이터와 라우팅 엔진 응답값을 기반으로 판단한다.
- AI가 임의 장소나 임의 안전 정보를 생성하지 않는다.
- 결과는 `suitability`, `steepness`, `surface`, `waytype`, `detourfactor`, `avgspeed` 등 실제 반환값으로 설명 가능해야 한다.
- 현재 Spring Boot 단일 앱 구조를 최대한 유지한다.

## 2. 현재 프로젝트 구조 요약

현재 백엔드는 Spring Boot 단일 애플리케이션으로 구성되어 있다.

```text
src/main/java/com/example/tourding
  config/
  direction/
    controller/
    dto/
    entity/
    repository/
    service/
  external/
    kakao/
    naver/
    open_routes_service/
    open_weather_map/
    riding_course/
    tourAPI/
  kakaoSearch/
  tourApi/
  user/
  weather/
```

현재 외부 API 연동 패턴:

| 영역 | 현재 구조 |
|---|---|
| ORS 경로 계산 | `external/open_routes_service/ORSCilent.java` |
| TourAPI | `external/tourAPI`, `tourApi/service` |
| Kakao Local | `external/kakao`, `kakaoSearch` |
| 도메인 API | `direction/controller`, `direction/service`, `direction/dto` |
| DB 저장 | JPA Entity + Repository |

AI 기능도 같은 패턴을 따르는 것이 적절하다.

## 3. 추가 기능 범위

### 3-1. 기능 A: 음성 명령 STT

사용자가 주행 중 음성으로 말한 내용을 텍스트로 변환한다.

예시:

```text
오르막 너무 많아. 좀 덜 힘든 길로 가자.
```

처리 결과:

```json
{
  "transcript": "오르막 너무 많아. 좀 덜 힘든 길로 가자.",
  "latencyMs": 873
}
```

### 3-2. 기능 B: 의도분류

STT 결과 또는 텍스트 입력을 GPT-5 mini에 전달해 경로 조정 의도로 분류한다.

의도 라벨:

| 라벨 | 의미 | 처리 방향 |
|---|---|---|
| `LESS_HILLS` | 오르막/급경사 회피 | 평탄함 가중치 상향 |
| `BETTER_SURFACE` | 노면 좋은 길 선호 | 노면 가중치 상향 |
| `BIKE_FRIENDLY` | 자전거 친화 길 선호 | 편안함, 길 유형 가중치 상향 |
| `FASTER_ROUTE` | 빠른 경로 선호 | 효율 가중치 상향 |
| `SHORTER_ROUTE` | 짧은 경로 선호 | 거리/우회도 기준 강화 |
| `AVOID_ROAD` | 큰길/차도 회피 | 간선도로, 일반도로 감점 |
| `FIND_FACILITY` | 화장실, 편의점 등 탐색 | Kakao Local API로 분기 |
| `ADD_TOUR_SPOT` | 관광지 경유지 추가 | TourAPI로 분기 |
| `UNSUPPORTED` | 데이터로 보장할 수 없는 요청 | 거절 및 대안 제시 |

### 3-3. 기능 C: 후보 경로 채점

OpenRouteService Directions API 응답값으로 후보 경로를 채점한다.

사용할 ORS 요청 옵션:

```json
{
  "elevation": true,
  "geometry": true,
  "geometry_simplify": false,
  "extra_info": [
    "steepness",
    "suitability",
    "surface",
    "waytype"
  ],
  "attributes": [
    "avgspeed",
    "detourfactor",
    "percentage"
  ],
  "options": {
    "avoid_features": ["steps"],
    "profile_params": {
      "weightings": {
        "steepness_difficulty": 1
      }
    }
  }
}
```

채점 항목:

| 점수 항목 | 산출 근거 |
|---|---|
| 편안함 | `suitability` 구간비율 가중평균 |
| 평탄함 | `steepness` 중 급경사/오르막 구간 비율의 역수 |
| 노면 | `surface` 중 포장 구간 비율 |
| 길 유형 | `waytype` 중 자전거도로/길 비율에서 간선도로/일반도로 비율 감점 |
| 효율 | `detourfactor`, `avgspeed` |

기본 공식:

```text
종합점수 =
w1 * 편안함
+ w2 * 평탄함
+ w3 * 노면
+ w4 * 길유형
+ w5 * 효율
```

## 4. 추천 아키텍처

### 4-1. MVP 단계 추천: Spring Boot 앱에 AI 기능 포함

초기 구현은 현재 Spring Boot 앱 안에 AI 기능을 포함하는 방식이 적합하다.

이유:

- 현재 프로젝트가 단일 Spring Boot 앱으로 배포되고 있다.
- AI 기능은 자체 모델 서빙이 아니라 OpenAI API 호출이다.
- GPU, 별도 모델 런타임, 대용량 추론 서버가 필요하지 않다.
- DB 트랜잭션, 사용자 정보, 기존 경로 정보와 강하게 연결된다.
- 공모전/MVP에서는 배포 복잡도를 줄이는 것이 더 중요하다.

구조:

```text
Mobile App
  -> Spring Boot app
       -> OpenAI STT / GPT-5 mini
       -> OpenRouteService
       -> Kakao Local
       -> TourAPI
       -> MySQL
```

### 4-2. 중장기 추천: AI Service 별도 분리

운영 규모가 커지면 AI 기능을 별도 컨테이너로 분리한다.

구조:

```text
Mobile App
  -> Spring Boot app
       -> ai-service
            -> OpenAI API
       -> OpenRouteService
       -> Kakao Local
       -> TourAPI
       -> MySQL
```

분리 기준:

| 기준 | 단일 앱 유지 | AI 서비스 분리 |
|---|---|---|
| MVP 개발 속도 | 유리 | 불리 |
| 배포 난이도 | 낮음 | 높음 |
| 장애 격리 | 낮음 | 높음 |
| AI 프롬프트/모델 실험 | 보통 | 유리 |
| 비동기 작업/큐 도입 | 보통 | 유리 |
| 비용/Rate Limit 제어 | 보통 | 유리 |
| 현재 프로젝트 적합성 | 높음 | 아직 과함 |

판단:

```text
현재는 단일 Spring Boot 앱에 포함한다.
다만 ai 도메인 경계를 명확히 나누고, 추후 ai-service로 분리할 수 있도록 인터페이스를 분리한다.
```

## 5. 패키지 추가 계획

현재 구조를 유지해 아래 패키지를 추가한다.

```text
src/main/java/com/example/tourding
  ai/
    controller/
      AiRouteController.java
    dto/
      AiRouteAdjustReqDto.java
      AiRouteAdjustRespDto.java
      AiIntentClassifyReqDto.java
      AiIntentClassifyRespDto.java
      RouteCandidateRespDto.java
      RouteScoreDetailDto.java
    entity/
      AiRouteRequest.java
      AiRouteCandidate.java
      UserRidingProfile.java
    repository/
      AiRouteRequestRepository.java
      AiRouteCandidateRepository.java
      UserRidingProfileRepository.java
    service/
      AiRouteAdjustmentService.java
      IntentClassifierService.java
      RouteCandidateService.java
      RouteScoringService.java
      SpeechToTextService.java
  external/
    openai/
      OpenAiClient.java
      OpenAiTranscriptionResponse.java
      OpenAiIntentResponse.java
```

기존 `direction` 도메인은 경로 조회/저장을 계속 담당하고, `ai` 도메인은 경로 조정 요청, 의도분류, 후보 채점, 추천 사유 생성을 담당한다.

## 6. API 설계

### 6-1. 음성 기반 경로 조정

```http
POST /ai/routes/adjustments/voice
Content-Type: multipart/form-data
```

Request:

| 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | Long | 사용자 ID |
| `routeSummaryId` | Long | 현재 주행 중인 경로 ID |
| `currentLon` | Double | 현재 경도 |
| `currentLat` | Double | 현재 위도 |
| `audio` | File | 음성 파일 |

Response:

```json
{
  "requestId": 1,
  "transcript": "오르막이 너무 많아. 덜 힘든 길로 가자.",
  "intent": "LESS_HILLS",
  "action": "RECALCULATE_REMAINING_ROUTE",
  "selectedCandidateId": 10,
  "candidates": [
    {
      "candidateId": 10,
      "rank": 1,
      "score": 0.81,
      "distance": 7444.5,
      "duration": 1520.2,
      "ascent": 172.5,
      "scoreDetail": {
        "comfort": 0.72,
        "flatness": 0.94,
        "surface": 0.28,
        "waytype": 0.25,
        "efficiency": 0.73
      },
      "reason": "급경사 구간 비율이 낮아 현재 요청에 가장 적합합니다."
    }
  ]
}
```

### 6-2. 텍스트 기반 경로 조정

```http
POST /ai/routes/adjustments/text
Content-Type: application/json
```

Request:

```json
{
  "userId": 1,
  "routeSummaryId": 3,
  "currentLon": 127.0276,
  "currentLat": 37.4979,
  "message": "비포장도로는 피해서 가자"
}
```

용도:

- 앱 개발 중 테스트
- 음성 실패 시 fallback
- 기획/시연용 API

### 6-3. 사용자 라이딩 프로필 저장

```http
POST /ai/riding-profile
Content-Type: application/json
```

Request:

```json
{
  "userId": 1,
  "cyclingProfile": "cycling-regular",
  "skillLevel": "BEGINNER",
  "avoidHills": true,
  "preferPaved": true,
  "preferBikeRoad": true,
  "avoidMainRoad": false
}
```

## 7. ORS 연동 변경 계획

현재 `ORSCilent`는 `cycling-regular/geojson`만 호출하고, 응답 DTO도 기본 경로 정보 중심이다.

변경 방향:

1. 기존 `getORSDirection(start, goal, wayPoints)`는 유지한다.
2. AI 채점용 메서드를 추가한다.

```java
public ORSRouteAnalysisResponse getRouteAnalysis(ORSRouteAnalysisRequest request)
```

3. AI 채점용 호출은 `/v2/directions/{profile}/json` 또는 `/geojson` 중 하나로 통일한다.
4. 응답 DTO에 아래 필드를 추가한다.

필요 응답 필드:

| 필드 | 목적 |
|---|---|
| `summary.distance` | 전체 거리 |
| `summary.duration` | 전체 예상 시간 |
| `summary.ascent` | 누적 상승고도 |
| `summary.descent` | 누적 하강고도 |
| `segments[*].avgspeed` | 구간 평균 속도 |
| `segments[*].detourfactor` | 우회 정도 |
| `segments[*].percentage` | 구간 비율 |
| `extras.steepness.summary` | 경사도 요약 |
| `extras.surface.summary` | 노면 요약 |
| `extras.waytype.summary` | 길 유형 요약 |
| `extras.suitability.summary` | 자전거 적합도 요약 |
| `extras.*.values` | 지도 구간별 시각화 |

주의:

- 현재 클래스명이 `ORSCilent`로 오타가 있다. 기존 참조가 있으므로 즉시 변경하지 말고, 새 구현 시 `ORSClient`로 새 클래스를 만들거나 리팩터링 작업을 별도 이슈로 분리한다.
- 기존 경로 API의 응답 호환성을 깨지 않는다.

## 8. 후보 경로 생성 전략

Public ORS API는 `surface=asphalt만 선호`, `자전거도로만 우선` 같은 조건을 직접 최적화하기 어렵다. 따라서 후보 경로를 여러 개 만들고, 응답값으로 채점한다.

후보 생성 방식:

| 후보 유형 | 생성 방식 |
|---|---|
| 기존 경로 유지 | 현재 위치에서 기존 목적지까지 재탐색 |
| 경유지 유지 | 남은 경유지를 유지하고 재탐색 |
| TourAPI 경유 후보 | 현재 위치와 목적지 사이 관광지를 후보 경유지로 추가 |
| 회피 지역 반영 | 사용자가 특정 구간을 싫어하면 `avoid_polygons` 적용 |
| 다른 경로 후보 | ORS `alternative_routes` 활용 가능 시 적용 |

MVP에서는 2~3개 후보만 생성한다.

추천:

```text
1차 MVP: 기존 목적지 기준 후보 3개
2차 확장: TourAPI 관광지 경유 후보 추가
3차 확장: 주행 이력 기반 개인화 후보 생성
```

## 9. DB 스키마 변경 계획

### 9-1. `user_riding_profile`

사용자의 기본 라이딩 성향을 저장한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | 프로필 ID |
| `user_id` | BIGINT FK | 사용자 ID |
| `cycling_profile` | VARCHAR(50) | `cycling-regular`, `cycling-road`, `cycling-mountain`, `cycling-electric` |
| `skill_level` | VARCHAR(30) | `BEGINNER`, `NORMAL`, `ADVANCED`, `PRO` |
| `avoid_hills` | BOOLEAN | 오르막 회피 성향 |
| `prefer_paved` | BOOLEAN | 포장도로 선호 |
| `prefer_bike_road` | BOOLEAN | 자전거도로 선호 |
| `avoid_main_road` | BOOLEAN | 큰길/간선도로 회피 |
| `created_at` | DATETIME | 생성일 |
| `updated_at` | DATETIME | 수정일 |

### 9-2. `ai_route_request`

AI 경로 조정 요청 단위 로그를 저장한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | 요청 ID |
| `user_id` | BIGINT FK | 사용자 ID |
| `route_summary_id` | BIGINT FK | 기준 경로 ID |
| `input_type` | VARCHAR(20) | `VOICE`, `TEXT` |
| `audio_file_path` | VARCHAR(500) | 음성 파일 저장 경로. 저장하지 않으면 null |
| `transcript` | TEXT | STT 결과 |
| `intent` | VARCHAR(50) | 분류된 의도 |
| `action` | VARCHAR(50) | 처리 액션 |
| `status` | VARCHAR(30) | `SUCCESS`, `REJECTED`, `FAILED` |
| `rejection_reason` | TEXT | 거절 사유 |
| `current_lon` | DECIMAL(11, 7) | 요청 시점 현재 경도 |
| `current_lat` | DECIMAL(10, 7) | 요청 시점 현재 위도 |
| `stt_latency_ms` | INT | STT 지연시간 |
| `llm_latency_ms` | INT | 의도분류 지연시간 |
| `ors_latency_ms` | INT | ORS 후보 조회 지연시간 |
| `created_at` | DATETIME | 요청 시각 |

### 9-3. `ai_route_candidate`

AI 요청에 대해 생성된 후보 경로와 점수를 저장한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | 후보 ID |
| `ai_route_request_id` | BIGINT FK | AI 요청 ID |
| `rank_no` | INT | 추천 순위 |
| `score` | DECIMAL(6, 4) | 종합 점수 |
| `distance` | DOUBLE | 거리 m |
| `duration` | DOUBLE | 예상 시간 초 |
| `ascent` | DOUBLE | 상승고도 m |
| `descent` | DOUBLE | 하강고도 m |
| `comfort_score` | DECIMAL(6, 4) | 편안함 점수 |
| `flatness_score` | DECIMAL(6, 4) | 평탄함 점수 |
| `surface_score` | DECIMAL(6, 4) | 노면 점수 |
| `waytype_score` | DECIMAL(6, 4) | 길 유형 점수 |
| `efficiency_score` | DECIMAL(6, 4) | 효율 점수 |
| `weight_json` | JSON | 적용 가중치 |
| `extra_summary_json` | JSON | ORS extras summary |
| `geometry_json` | MEDIUMTEXT | 경로 좌표 또는 polyline |
| `selected` | BOOLEAN | 최종 선택 여부 |
| `created_at` | DATETIME | 생성 시각 |

### 9-4. `route_summary` 확장

현재 `route_summary`는 출발/도착/경유지 문자열 저장 중심이다. AI 적용 후 선택된 옵션을 추적하려면 아래 컬럼을 추가하는 것이 좋다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `cycling_profile` | VARCHAR(50) | 사용한 ORS profile |
| `preference` | VARCHAR(30) | ORS preference |
| `route_options_json` | JSON | avoid_features, steepness_difficulty 등 |
| `source_type` | VARCHAR(30) | `MANUAL`, `RECOMMENDED`, `AI_ADJUSTED` |
| `parent_route_summary_id` | BIGINT NULL | AI 재조정 전 기준 경로 |

## 10. Entity 설계

### 10-1. `UserRidingProfile`

```java
@Entity
@Table(name = "user_riding_profile")
public class UserRidingProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String cyclingProfile;
    private String skillLevel;
    private Boolean avoidHills;
    private Boolean preferPaved;
    private Boolean preferBikeRoad;
    private Boolean avoidMainRoad;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 10-2. `AiRouteRequest`

```java
@Entity
@Table(name = "ai_route_request")
public class AiRouteRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    private RouteSummary routeSummary;

    private String inputType;
    private String intent;
    private String action;
    private String status;

    @Lob
    private String transcript;

    @Lob
    private String rejectionReason;

    private Double currentLon;
    private Double currentLat;
    private Integer sttLatencyMs;
    private Integer llmLatencyMs;
    private Integer orsLatencyMs;
    private LocalDateTime createdAt;
}
```

### 10-3. `AiRouteCandidate`

```java
@Entity
@Table(name = "ai_route_candidate")
public class AiRouteCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private AiRouteRequest aiRouteRequest;

    private Integer rankNo;
    private Double score;
    private Double distance;
    private Double duration;
    private Double ascent;
    private Double descent;

    private Double comfortScore;
    private Double flatnessScore;
    private Double surfaceScore;
    private Double waytypeScore;
    private Double efficiencyScore;

    @Lob
    private String weightJson;

    @Lob
    private String extraSummaryJson;

    @Lob
    private String geometryJson;

    private Boolean selected;
    private LocalDateTime createdAt;
}
```

## 11. AI 처리 흐름

### 11-1. 음성 경로 조정

```text
1. 앱에서 음성 파일 + 현재 위치 + routeSummaryId 전송
2. SpeechToTextService가 OpenAI STT 호출
3. IntentClassifierService가 GPT-5 mini로 의도분류
4. UNSUPPORTED면 거절 응답 저장 후 종료
5. RouteCandidateService가 후보 경로 2~3개 생성
6. ORSClient가 후보별 Directions API 호출
7. RouteScoringService가 extras/attributes로 점수 계산
8. 1순위 후보를 selected=true로 저장
9. 앱에 후보 경로, 점수, 추천 이유 반환
```

### 11-2. 텍스트 경로 조정

```text
1. 앱에서 텍스트 + 현재 위치 + routeSummaryId 전송
2. STT 단계 생략
3. 이후 의도분류/후보생성/채점은 동일
```

## 12. 비용 절감 전략

실제 검증에서 30개 합성 음성 기준 STT 정확도 80.0%, GPT-5 mini 의도분류 정확도 86.7%, OpenAI 비용 약 $0.02 수준이 관측됐다.

운영에서는 매 요청마다 GPT를 호출하지 않도록 한다.

추천 구조:

```text
STT
  -> 규칙 기반 1차 의도분류
      -> 확신 높음: GPT 호출 생략
      -> 확신 낮음: GPT-5 mini 호출
```

규칙 기반으로 바로 처리할 수 있는 예:

| 키워드 | 의도 |
|---|---|
| 오르막, 업힐, 경사, 평지 | `LESS_HILLS` |
| 비포장, 자갈, 흙길, 아스팔트, 노면 | `BETTER_SURFACE` |
| 자전거도로, 편한 길 | `BIKE_FRIENDLY` |
| 빠른 길, 빨리 | `FASTER_ROUTE` |
| 짧은 길, 가까운 길, 우회 | `SHORTER_ROUTE` |
| 화장실, 편의점, 물 | `FIND_FACILITY` |

## 13. Docker 운영 판단

### 13-1. 단일 컨테이너 운영

현재 운영 구조:

```text
Nginx
  -> tourding-prod-app
  -> remote MySQL
```

AI 기능 포함 후:

```text
Nginx
  -> tourding-prod-app
       -> OpenAI API
       -> OpenRouteService
       -> TourAPI
       -> Kakao Local
       -> remote MySQL
```

장점:

- 현재 `docker-compose.prod.yml` 변경이 작다.
- GitHub Actions 배포 흐름을 거의 유지할 수 있다.
- DB 트랜잭션과 기존 route/user 서비스 재사용이 쉽다.
- 공모전 제출과 MVP 구현에 가장 빠르다.

단점:

- OpenAI 지연이 앱 API 지연으로 직접 연결된다.
- AI 호출 장애가 앱 기능 일부에 영향을 줄 수 있다.
- 향후 비동기 큐나 AI 실험이 늘면 코드가 무거워진다.

### 13-2. AI 컨테이너 분리 운영

예상 구조:

```yaml
services:
  app:
    image: tourding
    environment:
      AI_SERVICE_BASE_URL: http://ai-service:8081

  ai-service:
    image: tourding-ai
    environment:
      OPEN_AI_SECRET_KEY: ...
```

장점:

- AI 장애를 앱 서버와 분리할 수 있다.
- Python/FastAPI로 프롬프트, STT, 평가 로직 실험이 쉽다.
- 별도 스케일링과 rate limit 제어가 가능하다.
- 추후 큐 기반 비동기 처리로 확장하기 좋다.

단점:

- 새 이미지, 새 배포 파이프라인, 내부 네트워크 설정이 필요하다.
- 앱과 AI 서비스 간 DTO 계약 관리가 필요하다.
- MySQL 접근 권한과 로그 추적 구조가 복잡해진다.
- 현재 기능 규모에는 운영 부담이 더 크다.

### 13-3. 최종 판단

```text
초기 개발/MVP: 단일 Spring Boot 앱에 포함
운영 확장/트래픽 증가 후: AI service 분리
```

분리 전제 조건:

- AI 요청이 전체 API 요청의 20% 이상을 차지한다.
- STT/GPT 지연 때문에 앱 API p95 지연이 의미 있게 증가한다.
- 프롬프트/모델 실험 주기가 일반 백엔드 배포보다 빨라진다.
- 비동기 작업, 재시도 큐, 비용 제한 정책이 필요해진다.

## 14. 환경변수 추가

`docker-compose.yml`, `docker-compose.prod.yml`, GitHub Secret `ENV_FILE`에 아래 값을 추가한다.

| 환경변수 | 설명 |
|---|---|
| `OPEN_AI_SECRET_KEY` | OpenAI API key |
| `OPENAI_STT_MODEL` | 기본값 `gpt-4o-mini-transcribe` |
| `OPENAI_INTENT_MODEL` | 기본값 `gpt-5-mini` |
| `AI_INTENT_RULE_FIRST` | 규칙 기반 우선 적용 여부 |
| `AI_AUDIO_MAX_SECONDS` | 음성 최대 길이 |
| `AI_REQUEST_TIMEOUT_MS` | AI API timeout |
| `AI_CANDIDATE_COUNT` | 후보 경로 생성 개수 |

Spring property 예:

```properties
openai.secret.key=${OPEN_AI_SECRET_KEY}
openai.stt.model=${OPENAI_STT_MODEL:gpt-4o-mini-transcribe}
openai.intent.model=${OPENAI_INTENT_MODEL:gpt-5-mini}
ai.intent.rule-first=${AI_INTENT_RULE_FIRST:true}
ai.audio.max-seconds=${AI_AUDIO_MAX_SECONDS:8}
ai.request.timeout-ms=${AI_REQUEST_TIMEOUT_MS:15000}
ai.candidate-count=${AI_CANDIDATE_COUNT:3}
```

## 15. 보안 및 개인정보

주의 사항:

- 음성 파일은 가능하면 장기 저장하지 않는다.
- 검증/장애 분석 목적이면 짧은 기간만 보관하고 만료 정책을 둔다.
- STT 원문에는 개인정보가 포함될 수 있으므로 로그에 전문을 남기지 않는다.
- OpenAI API key는 `application.properties`에 직접 넣지 않고 env로만 주입한다.
- 현재 `application.properties`에 외부 API key가 직접 들어가 있으므로 운영 보안 측면에서 제거가 필요하다.

권장 로그:

| 저장 가능 | 저장 지양 |
|---|---|
| requestId | OpenAI API key |
| intent | 음성 원본 장기 보관 |
| latency | 사용자 발화 전문 debug log |
| score summary | 개인정보 포함 가능 문장 |

## 16. 예외 처리

| 상황 | 처리 |
|---|---|
| OpenAI STT 실패 | 텍스트 입력 fallback 안내 |
| GPT 분류 실패 | 규칙 기반 분류 fallback |
| `UNSUPPORTED` | 거절 사유와 가능한 대안 반환 |
| ORS 후보 생성 실패 | 기존 경로 유지 |
| 후보 점수 동률 | 거리/효율 우선 tie-break |
| API quota 초과 | 사용자에게 잠시 후 재시도 안내 |

## 17. 개발 단계

### 1단계: 기반 DTO/Entity 추가

- `ai` 패키지 생성
- `UserRidingProfile`, `AiRouteRequest`, `AiRouteCandidate` 추가
- Repository 추가
- 환경변수 추가

### 2단계: OpenAI Client 추가

- STT multipart 호출 구현
- GPT-5 mini JSON 분류 호출 구현
- timeout/retry/에러 매핑 구현

### 3단계: ORS 분석 응답 확장

- `extra_info`, `attributes`, `elevation` 포함 요청 구현
- `extras.summary`, `segments.avgspeed`, `detourfactor`, `percentage` 파싱
- 기존 `/routes` API와 호환성 유지

### 4단계: 채점 엔진 구현

- `RouteScoringService` 구현
- 의도별 가중치 매핑
- 후보별 score 계산
- 추천 사유 생성

### 5단계: AI 경로 조정 API 구현

- `/ai/routes/adjustments/text`
- `/ai/routes/adjustments/voice`
- Swagger 문서화

### 6단계: 검증 및 운영화

- 합성 음성 30건 검증
- 실제 주행/야외 음성 추가 검증
- STT 실패/오분류 케이스 보강
- 비용/지연 로그 모니터링

## 18. 테스트 계획

| 테스트 | 내용 |
|---|---|
| 단위 테스트 | 의도별 가중치 매핑, 점수 계산 |
| 통합 테스트 | OpenAI mock, ORS mock 기반 API 테스트 |
| 라이브 검증 | OpenAI STT/GPT-5 mini, ORS 실제 호출 |
| 실패 테스트 | quota 초과, timeout, unsupported intent |
| 회귀 테스트 | 기존 `/routes`, `/tour`, `/weather`, `/users` API 영향 없음 |

실측 지표:

| 지표 | 목표 |
|---|---|
| STT 평균 지연 | 1.5초 이하 |
| 의도분류 평균 지연 | 2.0초 이하 |
| 의도분류 정확도 | 85% 이상 |
| ORS 평균 지연 | 2.0초 이하 |
| 전체 경로 조정 응답 | 5초 이하 |

## 19. 기획서 반영 문구

```text
투어딩의 AI 기능은 경로를 임의 생성하지 않는다. 사용자의 음성 발화를 STT로 텍스트화한 뒤, GPT-5 mini가 이를 경로 조정 의도와 채점 가중치로 변환한다. 이후 후보 경로는 OpenRouteService가 반환한 실제 구간 속성값으로 결정론적으로 채점된다. 따라서 추천 결과는 자전거 적합도, 경사도, 노면 상태, 길 유형, 평균 속도, 우회 정도 등 실제 데이터로 설명 가능하다.
```

## 20. 최종 결론

현재 프로젝트 구조에서는 AI 기능을 별도 서비스로 바로 분리하기보다, Spring Boot 앱 내부에 `ai` 도메인으로 추가하는 것이 가장 적절하다.

단, 코드 경계는 처음부터 분리한다.

```text
controller -> ai service -> external/openai
                    -> direction/ORS analysis
                    -> route scoring
                    -> repository
```

이렇게 구현하면 MVP는 빠르게 완성할 수 있고, 추후 트래픽 증가나 AI 실험 필요성이 커졌을 때 `ai` 패키지를 별도 `ai-service` 컨테이너로 분리하기 쉽다.
