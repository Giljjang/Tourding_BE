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
}
