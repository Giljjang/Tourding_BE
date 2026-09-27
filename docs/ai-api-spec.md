# AI 경로 조정 기능 API 명세서

## 1. 개요

AI 경로 조정 기능은 사용자의 텍스트/음성 요청을 경로 조정 의도로 분류하고, OpenRouteService의 실제 경로 속성값으로 후보 경로를 채점해 추천한다.

현재 추가된 API:

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/ai/routes/adjustments/text` | 텍스트 명령으로 남은 경로 재조정 |
| `POST` | `/ai/routes/adjustments/voice` | 음성 명령으로 남은 경로 재조정 |
| `POST` | `/ai/riding-profile` | AI 경로 조정용 사용자 라이딩 프로필 저장 |

내부적으로 수정/추가된 외부 연동:

| 구분 | 내용 |
|---|---|
| OpenAI STT | 음성 파일을 한국어 텍스트로 전사 |
| GPT-5 mini 의도분류 | 사용자 발화를 의도 라벨과 가중치로 변환 |
| ORS 분석 호출 | `extra_info`, `attributes`, `elevation` 포함 경로 분석 |

---

## 2. 공통 규칙

### 2-1. 좌표 형식

좌표는 OpenRouteService 기준으로 사용한다.

```text
[경도, 위도]
```

예시:

```text
127.0276, 37.4979
```

### 2-2. 응답 상태값

| 값 | 의미 |
|---|---|
| `SUCCESS` | 요청 처리 성공 |
| `REJECTED` | 데이터로 처리할 수 없어 거절 |
| `FAILED` | 후보 경로 생성 또는 외부 API 처리 실패 |
| `PROCESSING` | 내부 처리 중 상태. 최종 응답에는 보통 노출되지 않음 |

### 2-3. 의도 라벨

| 라벨 | 의미 | 처리 방향 |
|---|---|---|
| `LESS_HILLS` | 오르막/경사 회피 | 평탄함 가중치 상향 |
| `BETTER_SURFACE` | 노면 좋은 길 선호 | 노면 가중치 상향 |
| `BIKE_FRIENDLY` | 자전거 친화 길 선호 | 편안함, 길 유형 가중치 상향 |
| `FASTER_ROUTE` | 빠른 경로 선호 | 효율 가중치 상향 |
| `SHORTER_ROUTE` | 짧은 경로 선호 | 효율, 우회도 기준 강화 |
| `AVOID_ROAD` | 큰길/차도 회피 | 길 유형 가중치 상향 |
| `FIND_FACILITY` | 화장실, 편의점 등 탐색 | 편의시설 탐색 액션 |
| `ADD_TOUR_SPOT` | 관광지 경유지 추가 | 관광 경유지 후보 탐색 액션 |
| `UNSUPPORTED` | 데이터로 보장할 수 없는 요청 | 거절 및 대안 제시 |

### 2-4. 액션값

| 값 | 의미 |
|---|---|
| `RECALCULATE_REMAINING_ROUTE` | 남은 경로 후보를 재탐색하고 채점 |
| `SEARCH_FACILITY` | 주변 편의시설 탐색으로 분기 |
| `ADD_WAYPOINT_CANDIDATE` | 관광지/스팟 경유지 후보 탐색으로 분기 |
| `REJECT_WITH_ALTERNATIVE` | 요청 거절 후 가능한 대안 제시 |

---

## 3. 텍스트 명령 경로 조정

### 3-1. 기본 정보

```http
POST /ai/routes/adjustments/text
Content-Type: application/json
```

사용자가 텍스트로 입력한 요청을 기반으로 남은 경로를 조정한다.

### 3-2. Request Body

```json
{
  "userId": 1,
  "routeSummaryId": 3,
  "currentLon": 127.0276,
  "currentLat": 37.4979,
  "message": "오르막이 너무 많아. 좀 덜 힘든 길로 가자"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `userId` | Long | Y | 사용자 ID |
| `routeSummaryId` | Long | Y | 현재 기준 경로의 `route_summary.id` |
| `currentLon` | Double | Y | 현재 위치 경도 |
| `currentLat` | Double | Y | 현재 위치 위도 |
| `message` | String | Y | 사용자 텍스트 명령 |

### 3-3. 처리 흐름

```text
1. routeSummaryId로 기존 경로 조회
2. 요청 userId와 경로 소유자 검증
3. 규칙 기반 1차 의도분류
4. 규칙으로 분류되지 않으면 GPT-5 mini 호출
5. 의도에 따라 후보 경로 생성
6. ORS 분석 API 호출
7. 후보 경로 채점
8. 요청/후보 결과 DB 저장
9. 1순위 후보 반환
```

### 3-4. Success Response

```json
{
  "requestId": 1,
  "transcript": "오르막이 너무 많아. 좀 덜 힘든 길로 가자",
  "intent": "LESS_HILLS",
  "action": "RECALCULATE_REMAINING_ROUTE",
  "status": "SUCCESS",
  "rejectionReason": null,
  "weightUpdate": {
    "comfort": 0.2,
    "flatness": 0.45,
    "surface": 0.15,
    "waytype": 0.1,
    "efficiency": 0.1
  },
  "selectedCandidateId": 10,
  "candidates": [
    {
      "candidateId": 10,
      "rank": 1,
      "score": 0.8123,
      "distance": 7444.5,
      "duration": 1520.2,
      "ascent": 172.5,
      "descent": 176.7,
      "scoreDetail": {
        "comfort": 0.7271,
        "flatness": 0.9454,
        "surface": 0.2833,
        "waytype": 0.2506,
        "efficiency": 0.7358
      },
      "reason": "현재 위치에서 목적지까지 직접 재탐색한 후보입니다."
    }
  ]
}
```

### 3-5. Response Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `requestId` | Long | AI 경로 조정 요청 ID |
| `transcript` | String | 처리에 사용된 텍스트. 텍스트 API에서는 `message`와 동일 |
| `intent` | String | 분류된 의도 라벨 |
| `action` | String | 서버 처리 액션 |
| `status` | String | 처리 상태 |
| `rejectionReason` | String | 거절/실패 사유 |
| `weightUpdate` | Object | 채점 가중치 |
| `selectedCandidateId` | Long | 1순위 후보 ID |
| `candidates` | Array | 후보 경로 목록 |

### 3-6. Candidate Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `candidateId` | Long | 후보 경로 ID |
| `rank` | Integer | 추천 순위 |
| `score` | Double | 종합 점수 |
| `distance` | Double | 거리, meter |
| `duration` | Double | 예상 시간, second |
| `ascent` | Double | 누적 상승고도, meter |
| `descent` | Double | 누적 하강고도, meter |
| `scoreDetail.comfort` | Double | 자전거 적합도 기반 편안함 점수 |
| `scoreDetail.flatness` | Double | 경사도 기반 평탄함 점수 |
| `scoreDetail.surface` | Double | 포장/노면 점수 |
| `scoreDetail.waytype` | Double | 자전거도로/길 유형 점수 |
| `scoreDetail.efficiency` | Double | 평균속도/우회도 기반 효율 점수 |
| `reason` | String | 후보 생성 또는 추천 사유 |

### 3-7. Rejected Response

```json
{
  "requestId": 2,
  "transcript": "사고가 절대 안 나는 길로 가줘",
  "intent": "UNSUPPORTED",
  "action": "REJECT_WITH_ALTERNATIVE",
  "status": "REJECTED",
  "rejectionReason": "현재 데이터로 사고가 절대 나지 않는 경로는 보장할 수 없습니다.",
  "weightUpdate": null,
  "selectedCandidateId": null,
  "candidates": []
}
```

---

## 4. 음성 명령 경로 조정

### 4-1. 기본 정보

```http
POST /ai/routes/adjustments/voice
Content-Type: multipart/form-data
```

음성 파일을 STT로 전사한 뒤, 텍스트 경로 조정과 같은 흐름으로 처리한다.

### 4-2. Request Form Data

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `userId` | Long | Y | 사용자 ID |
| `routeSummaryId` | Long | Y | 현재 기준 경로의 `route_summary.id` |
| `currentLon` | Double | Y | 현재 위치 경도 |
| `currentLat` | Double | Y | 현재 위치 위도 |
| `audio` | File | Y | 음성 파일 |

지원 권장 형식:

| 항목 | 권장 |
|---|---|
| 포맷 | `wav`, `mp3`, `m4a` |
| 길이 | 8초 이하 |
| 언어 | 한국어 |

### 4-3. cURL 예시

```bash
curl -X POST "https://tourding.walab.info/ai/routes/adjustments/voice" \
  -F "userId=1" \
  -F "routeSummaryId=3" \
  -F "currentLon=127.0276" \
  -F "currentLat=37.4979" \
  -F "audio=@command.wav"
```

### 4-4. Success Response

```json
{
  "requestId": 3,
  "transcript": "비포장도로는 피해서 가자",
  "intent": "BETTER_SURFACE",
  "action": "RECALCULATE_REMAINING_ROUTE",
  "status": "SUCCESS",
  "rejectionReason": null,
  "weightUpdate": {
    "comfort": 0.15,
    "flatness": 0.15,
    "surface": 0.45,
    "waytype": 0.15,
    "efficiency": 0.1
  },
  "selectedCandidateId": 11,
  "candidates": []
}
```

참고:

- 실제 응답에서는 `candidates`에 후보 경로 목록이 포함된다.
- `FIND_FACILITY`, `ADD_TOUR_SPOT` 의도는 현재 후보 경로 재계산 대신 액션 분기만 반환한다.

---

## 5. 사용자 라이딩 프로필 저장

### 5-1. 기본 정보

```http
POST /ai/riding-profile
Content-Type: application/json
```

사용자의 기본 라이딩 성향을 저장한다. 저장된 값은 AI 경로 조정 시 ORS profile, 경사 난이도 기본값 등에 사용된다.

### 5-2. Request Body

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

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `userId` | Long | Y | 사용자 ID |
| `cyclingProfile` | String | N | ORS 자전거 프로필 |
| `skillLevel` | String | N | 사용자 숙련도 |
| `avoidHills` | Boolean | N | 오르막 회피 성향 |
| `preferPaved` | Boolean | N | 포장도로 선호 |
| `preferBikeRoad` | Boolean | N | 자전거도로 선호 |
| `avoidMainRoad` | Boolean | N | 큰길/간선도로 회피 |

### 5-3. `cyclingProfile` 값

| 값 | 설명 |
|---|---|
| `cycling-regular` | 일반 자전거 |
| `cycling-road` | 로드 자전거 |
| `cycling-mountain` | 산악 자전거 |
| `cycling-electric` | 전기자전거 |

### 5-4. `skillLevel` 값

| 값 | ORS 경사 난이도 기본값 | 설명 |
|---|---:|---|
| `BEGINNER` | `0` | 초보자 |
| `NORMAL` | `1` | 일반 |
| `ADVANCED` | `2` | 숙련자 |
| `PRO` | `3` | 상급자 |

### 5-5. Response

```json
{
  "profileId": 1,
  "userId": 1,
  "cyclingProfile": "cycling-regular",
  "skillLevel": "BEGINNER",
  "avoidHills": true,
  "preferPaved": true,
  "preferBikeRoad": true,
  "avoidMainRoad": false
}
```

---

## 6. 내부 ORS 분석 호출 변경

기존 `ORSCilent.getORSDirection()`은 기존 `/routes` 기능을 위해 그대로 유지한다.

AI 기능을 위해 아래 메서드가 추가되었다.

```java
public ORSJsonResponse getRouteAnalysis(ORSRouteAnalysisRequest request)
```

### 6-1. 호출 Endpoint

```http
POST https://api.openrouteservice.org/v2/directions/{profile}/json
```

### 6-2. 내부 요청 Body

```json
{
  "coordinates": [
    [127.0276, 37.4979],
    [127.0650, 37.5400]
  ],
  "preference": "recommended",
  "elevation": true,
  "instructions": true,
  "maneuvers": true,
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

### 6-3. 내부 응답 활용 필드

| ORS 필드 | 활용 |
|---|---|
| `routes[*].summary.distance` | 후보 거리 |
| `routes[*].summary.duration` | 후보 예상 시간 |
| `routes[*].summary.ascent` | 상승고도 |
| `routes[*].summary.descent` | 하강고도 |
| `routes[*].segments[*].avgspeed` | 효율 점수 |
| `routes[*].segments[*].detourfactor` | 효율 점수 |
| `routes[*].segments[*].percentage` | 구간 가중평균 |
| `routes[*].extras.suitability.summary` | 편안함 점수 |
| `routes[*].extras.steepness.summary` | 평탄함 점수 |
| `routes[*].extras.surface.summary` | 노면 점수 |
| `routes[*].extras.waytype.summary` | 길 유형 점수 |
| `routes[*].geometry` | 후보 경로 저장 |

---

## 7. 채점 방식

### 7-1. 기본 점수 항목

| 항목 | 범위 | 산출 방식 |
|---|---:|---|
| `comfort` | `0.0` ~ `1.0` | `suitability` 값의 구간비율 가중평균 |
| `flatness` | `0.0` ~ `1.0` | 급경사/오르막 비율이 낮을수록 높음 |
| `surface` | `0.0` ~ `1.0` | 포장 노면 비율 |
| `waytype` | `0.0` ~ `1.0` | 자전거도로/길 비율에서 간선도로/일반도로 감점 |
| `efficiency` | `0.0` ~ `1.0` | 평균속도와 우회도 기반 |

### 7-2. 의도별 대표 가중치

| 의도 | comfort | flatness | surface | waytype | efficiency |
|---|---:|---:|---:|---:|---:|
| `LESS_HILLS` | 0.20 | 0.45 | 0.15 | 0.10 | 0.10 |
| `BETTER_SURFACE` | 0.15 | 0.15 | 0.45 | 0.15 | 0.10 |
| `BIKE_FRIENDLY` | 0.35 | 0.15 | 0.15 | 0.25 | 0.10 |
| `FASTER_ROUTE` | 0.10 | 0.10 | 0.10 | 0.10 | 0.60 |
| `SHORTER_ROUTE` | 0.10 | 0.15 | 0.10 | 0.10 | 0.55 |
| `AVOID_ROAD` | 0.25 | 0.15 | 0.10 | 0.40 | 0.10 |

### 7-3. 종합 점수

```text
score =
comfort * weight.comfort
+ flatness * weight.flatness
+ surface * weight.surface
+ waytype * weight.waytype
+ efficiency * weight.efficiency
```

---

## 8. 저장 테이블

AI 기능 추가로 아래 테이블이 생성된다.

| 테이블 | 설명 |
|---|---|
| `user_riding_profile` | 사용자 라이딩 성향 |
| `ai_route_request` | AI 경로 조정 요청 로그 |
| `ai_route_candidate` | AI 요청별 후보 경로 및 점수 |

### 8-1. `ai_route_request`

주요 저장값:

| 컬럼 | 설명 |
|---|---|
| `user_id` | 요청 사용자 |
| `route_summary_id` | 기준 경로 |
| `input_type` | `TEXT` 또는 `VOICE` |
| `transcript` | 처리된 텍스트 |
| `intent` | 의도 라벨 |
| `action` | 처리 액션 |
| `status` | 처리 상태 |
| `current_lon`, `current_lat` | 요청 당시 현재 위치 |
| `stt_latency_ms` | STT 지연 |
| `llm_latency_ms` | 의도분류 지연 |
| `ors_latency_ms` | ORS 후보 조회 지연 |

### 8-2. `ai_route_candidate`

주요 저장값:

| 컬럼 | 설명 |
|---|---|
| `ai_route_request_id` | AI 요청 ID |
| `rank_no` | 추천 순위 |
| `score` | 종합 점수 |
| `distance`, `duration` | 거리/예상시간 |
| `ascent`, `descent` | 상승/하강고도 |
| `comfort_score` | 편안함 점수 |
| `flatness_score` | 평탄함 점수 |
| `surface_score` | 노면 점수 |
| `waytype_score` | 길 유형 점수 |
| `efficiency_score` | 효율 점수 |
| `weight_json` | 적용 가중치 |
| `extra_summary_json` | ORS extras 요약 |
| `geometry_json` | ORS geometry |
| `selected` | 최종 선택 후보 여부 |

---

## 9. 환경변수

Docker 실행 시 아래 환경변수를 사용한다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `OPEN_AI_SECRET_KEY` | 없음 | OpenAI API key |
| `OPENAI_STT_MODEL` | `gpt-4o-mini-transcribe` | STT 모델 |
| `OPENAI_INTENT_MODEL` | `gpt-5-mini` | 의도분류 모델 |
| `AI_INTENT_RULE_FIRST` | `true` | 규칙 기반 분류 우선 적용 |
| `AI_AUDIO_MAX_SECONDS` | `8` | 음성 최대 길이. 현재 문서상 정책값 |
| `AI_REQUEST_TIMEOUT_MS` | `15000` | AI 요청 timeout. 현재 문서상 정책값 |
| `AI_CANDIDATE_COUNT` | `3` | 반환 후보 개수 |
| `OPEN_ROUTE_SERVICE_KEY` | 없음 | ORS API key |

---

## 10. 에러/주의사항

| 상황 | 현재 처리 |
|---|---|
| 다른 사용자의 `routeSummaryId` 요청 | `IllegalArgumentException` |
| `UNSUPPORTED` 의도 | `REJECTED` 상태로 후보 없이 반환 |
| 시설 탐색/관광지 추가 의도 | 현재는 액션 분기만 반환, 후보 경로 재계산 없음 |
| ORS 후보 생성 실패 | `FAILED` 상태 반환 |
| OpenAI API key 누락 | OpenAI 호출 시 RuntimeException |

주의:

- 현재 API는 인증 없이 열려 있는 기존 보안 설정을 따른다.
- `AI_AUDIO_MAX_SECONDS`, `AI_REQUEST_TIMEOUT_MS`는 환경변수로 추가됐지만 현재 코드에서 직접 제한 로직은 아직 적용되지 않았다.
- `application.properties`는 레포에서 ignore되어 있으므로 운영에서는 Docker env로 주입해야 한다.
