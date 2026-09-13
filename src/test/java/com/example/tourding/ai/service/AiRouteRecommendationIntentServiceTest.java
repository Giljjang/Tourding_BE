package com.example.tourding.ai.service;

import com.example.tourding.ai.dto.AiRouteRecommendationIntentDto;
import com.example.tourding.external.openai.OpenAiClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiRouteRecommendationIntentServiceTest {

    private final OpenAiClient openAiClient = mock(OpenAiClient.class);
    private final AiRouteRecommendationIntentService service = new AiRouteRecommendationIntentService(openAiClient);

    @Test
    void classifiesUphillAvoidanceAsEasyRouteWithoutOpenAi() {
        AiRouteRecommendationIntentDto result = service.classify("오르막 피해줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getTargetDifficulty()).isEqualTo(1);
        assertThat(result.getWeightUpdate()).containsEntry("flatness", 0.35);
        verifyNoInteractions(openAiClient);
    }

    @Test
    void rejectsFacilitySearchAsUnsupportedRecommendationCondition() {
        AiRouteRecommendationIntentDto result = service.classify("근처 카페 가고싶어");

        assertThat(result.isSupported()).isFalse();
        assertThat(result.getExplanation()).contains("시설 탐색");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void allowsSpecificWaypointNameInRouteRecommendation() {
        AiRouteRecommendationIntentDto result = service.classify("황리단길 경유해서 쉬운 코스로 추천해줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getWaypointNames()).containsExactly("황리단길");
        assertThat(result.getTargetDifficulty()).isEqualTo(1);
        verifyNoInteractions(openAiClient);
    }

    @Test
    void classifiesFastRouteWithoutOpenAi() {
        AiRouteRecommendationIntentDto result = service.classify("제일 빠른길로 안내해줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getFastRoute()).isTrue();
        assertThat(result.getWeightUpdate().get("efficiency")).isGreaterThan(0.30);
        verifyNoInteractions(openAiClient);
    }

    @Test
    void classifiesRoadBikeAndPavedPreferenceWithoutOpenAi() {
        AiRouteRecommendationIntentDto result = service.classify("로드 자전거로 포장도로 위주로 추천해줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getCyclingProfile()).isEqualTo("cycling-road");
        assertThat(result.getPreferPaved()).isTrue();
        verifyNoInteractions(openAiClient);
    }

    @Test
    void classifiesAvoidFordsAndStepsWithoutOpenAi() {
        AiRouteRecommendationIntentDto result = service.classify("물길이랑 계단 피해줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getAvoidFords()).isTrue();
        assertThat(result.getAvoidSteps()).isTrue();
        verifyNoInteractions(openAiClient);
    }

    @Test
    void classifiesBikeRoadPreferenceAndMainRoadAvoidanceWithoutOpenAi() {
        AiRouteRecommendationIntentDto result = service.classify("자전거도로 우선으로 큰도로 피해줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getPreferBikeRoad()).isTrue();
        assertThat(result.getAvoidMainRoad()).isTrue();
        verifyNoInteractions(openAiClient);
    }

    @Test
    void rejectsGenericFacilityCategoryAsWaypoint() {
        AiRouteRecommendationIntentDto result = service.classify("카페 들러서 코스 추천해줘");

        assertThat(result.isSupported()).isFalse();
        assertThat(result.getExplanation()).contains("구체적인 장소명");
        verifyNoInteractions(openAiClient);
    }
}
