package com.example.tourding.external.openai;

import com.example.tourding.ai.dto.AiIntentClassifyRespDto;
import com.example.tourding.ai.dto.AiRouteRecommendationIntentDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Component
@RequiredArgsConstructor
public class OpenAiClient {
    @Qualifier("openAiRestTemplate")
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${OPEN_AI_SECRET_KEY:${openai.secret.key:}}")
    private String openAiSecretKey;

    @Value("${OPENAI_STT_MODEL:${openai.stt.model:gpt-4o-mini-transcribe}}")
    private String sttModel;

    @Value("${OPENAI_INTENT_MODEL:${openai.intent.model:gpt-5-mini}}")
    private String intentModel;

    public String transcribe(MultipartFile audioFile) {
        if (openAiSecretKey == null || openAiSecretKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key가 설정되지 않았습니다.");
        }

        try {
            String url = "https://api.openai.com/v1/audio/transcriptions";

            ByteArrayResource audioResource = new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return audioFile.getOriginalFilename() == null ? "audio.wav" : audioFile.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", sttModel);
            body.add("language", "ko");
            body.add("response_format", "json");
            body.add("file", audioResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(openAiSecretKey);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("text").asText("");
        } catch (Exception e) {
            throw new RuntimeException("OpenAI STT 호출 실패", e);
        }
    }

    public AiIntentClassifyRespDto classifyIntent(String transcript) {
        if (openAiSecretKey == null || openAiSecretKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key가 설정되지 않았습니다.");
        }

        try {
            String systemPrompt = """
                    너는 자전거 여행 앱 투어딩의 주행 중 명령 의도분류기다.
                    반드시 JSON만 반환한다.
                    intent는 LESS_HILLS, BETTER_SURFACE, BIKE_FRIENDLY, FASTER_ROUTE, SHORTER_ROUTE, AVOID_ROAD, FIND_FACILITY, ADD_TOUR_SPOT, UNSUPPORTED 중 하나만 사용한다.
                    UNSUPPORTED는 현재 데이터/API로 보장할 수 없는 절대 안전, 운세, 사고 0% 같은 요청에 사용한다.
                    route_action은 RECALCULATE_REMAINING_ROUTE, SEARCH_FACILITY, ADD_WAYPOINT_CANDIDATE, REJECT_WITH_ALTERNATIVE 중 하나다.
                    weight_update는 comfort, flatness, surface, waytype, efficiency의 합이 1.0이 되게 반환한다. 시설검색/관광지추가/거절은 null 가능하다.
                    """;

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", intentModel);
            body.put("input", List.of(
                    Map.of("role", "system", "content", List.of(Map.of("type", "input_text", "text", systemPrompt))),
                    Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", "사용자 발화: " + transcript)))
            ));
            body.put("text", Map.of("format", Map.of("type", "json_object")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiSecretKey);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.openai.com/v1/responses",
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                    String.class
            );

            String outputText = extractOutputText(objectMapper.readTree(response.getBody()));
            JsonNode result = objectMapper.readTree(outputText);

            return AiIntentClassifyRespDto.builder()
                    .intent(result.path("intent").asText("UNSUPPORTED"))
                    .confidence(result.path("confidence").isNumber() ? result.path("confidence").asDouble() : null)
                    .routeAction(result.path("route_action").asText("REJECT_WITH_ALTERNATIVE"))
                    .weightUpdate(parseWeightUpdate(result.path("weight_update")))
                    .explanation(result.path("explanation").asText(""))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("OpenAI 의도분류 호출 실패", e);
        }
    }

    public AiRouteRecommendationIntentDto classifyRouteRecommendationIntent(String text) {
        if (openAiSecretKey == null || openAiSecretKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key가 설정되지 않았습니다.");
        }

        try {
            String systemPrompt = """
                    너는 자전거 여행 앱 투어딩의 추천 코스 조건 분류기다.
                    반드시 JSON만 반환한다.
                    지원 의도는 waypoint_add, difficulty, avoid_segment, distance_limit, route_speed, cycling_profile, surface_preference, waytype_preference다.
                    이외 요청만 있으면 supported=false로 반환한다.
                    waypoint_names는 사용자가 경유하고 싶은 구체적인 장소명 배열이다.
                    "들러줘", "들려줘", "들렸다가", "들릴래", "들르고 싶어", "들리고 싶어", "들리고싶어요", "들리자", "갔다가", "가자", "찍고", "거쳐서"는 모두 waypoint_add 의도다.
                    waypoint_names에는 카카오 장소 검색에 넣을 최소 장소명/브랜드명만 넣는다.
                    예: "제일 어려운코스로 가는데 옹짬뽕 들렸다가 가줘" -> waypoint_names=["옹짬뽕"].
                    예: "가다가 근처 올리브영도 한번 들러줘" -> waypoint_names=["올리브영"].
                    예: "가는길에 맥도날드랑 죽천해수욕장 들렸다 가는 코스" -> waypoint_names=["맥도날드","죽천해수욕장"].
                    경로 조건, "가다가", "근처", "한번", "들러줘" 같은 연결어/조사는 waypoint_names에서 제거한다.
                    여러 장소가 "랑", "이랑", "하고", "와", "과", "및"으로 연결되면 순서를 유지해 각각 분리한다.
                    카페, 화장실, 편의점, 맛집처럼 시설 종류만 있고 구체적인 장소명이 없으면 supported=false로 반환한다.
                    target_difficulty는 1,2,3,4 중 하나이며 없으면 null이다.
                    avoid_construction, avoid_steps, avoid_fords, avoid_ice는 각각 공사구간, 계단, 물길/도섭, 빙판길 제외 요청 여부다.
                    fast_route는 빠른길/최단시간/효율 우선이면 true, 여유/느긋한 경로면 false, 없으면 null이다.
                    cycling_profile은 cycling-regular, cycling-road, cycling-mountain, cycling-electric 중 하나이며 없으면 null이다.
                    prefer_paved는 포장도로/아스팔트/좋은 노면 선호 여부다.
                    prefer_bike_road는 자전거도로/자전거길/산책로/강변길 우선 여부다.
                    avoid_main_road는 큰도로/차도/차 많은 길 회피 여부다.
                    max_distance_km는 키로수 제한이 있을 때 숫자로 반환한다.
                    weight_update는 comfort, flatness, surface, waytype, efficiency 합이 1.0이 되게 반환한다.
                    """;

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", intentModel);
            body.put("input", List.of(
                    Map.of("role", "system", "content", List.of(Map.of("type", "input_text", "text", systemPrompt))),
                    Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", "사용자 입력: " + text)))
            ));
            body.put("text", Map.of("format", Map.of("type", "json_object")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiSecretKey);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.openai.com/v1/responses",
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                    String.class
            );

            JsonNode result = objectMapper.readTree(extractOutputText(objectMapper.readTree(response.getBody())));

            return AiRouteRecommendationIntentDto.builder()
                    .waypointNames(parseStringList(result.path("waypoint_names")))
                    .targetDifficulty(result.path("target_difficulty").isNumber() ? result.path("target_difficulty").asInt() : null)
                    .avoidConstruction(result.path("avoid_construction").isBoolean() ? result.path("avoid_construction").asBoolean() : null)
                    .avoidSteps(result.path("avoid_steps").isBoolean() ? result.path("avoid_steps").asBoolean() : null)
                    .avoidFords(result.path("avoid_fords").isBoolean() ? result.path("avoid_fords").asBoolean() : null)
                    .avoidIce(result.path("avoid_ice").isBoolean() ? result.path("avoid_ice").asBoolean() : null)
                    .fastRoute(result.path("fast_route").isBoolean() ? result.path("fast_route").asBoolean() : null)
                    .cyclingProfile(result.path("cycling_profile").isTextual() ? result.path("cycling_profile").asText() : null)
                    .preferPaved(result.path("prefer_paved").isBoolean() ? result.path("prefer_paved").asBoolean() : null)
                    .preferBikeRoad(result.path("prefer_bike_road").isBoolean() ? result.path("prefer_bike_road").asBoolean() : null)
                    .avoidMainRoad(result.path("avoid_main_road").isBoolean() ? result.path("avoid_main_road").asBoolean() : null)
                    .maxDistanceKm(result.path("max_distance_km").isNumber() ? result.path("max_distance_km").asDouble() : null)
                    .weightUpdate(parseWeightUpdate(result.path("weight_update")))
                    .explanation(result.path("explanation").asText(""))
                    .supported(result.path("supported").asBoolean(false))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("OpenAI 추천 의도분류 호출 실패", e);
        }
    }

    private String extractOutputText(JsonNode root) {
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode contents = item.path("content");
                if (contents.isArray()) {
                    for (JsonNode content : contents) {
                        if (content.has("text")) {
                            return content.path("text").asText();
                        }
                    }
                }
            }
        }
        return root.path("output_text").asText("{}");
    }

    private Map<String, Double> parseWeightUpdate(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }

        Map<String, Double> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isNumber()) {
                result.put(field.getKey(), field.getValue().asDouble());
            }
        }
        return result;
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText().trim());
            }
        }
        return result;
    }
}
