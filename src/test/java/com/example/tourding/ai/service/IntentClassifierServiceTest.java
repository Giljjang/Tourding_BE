package com.example.tourding.ai.service;

import com.example.tourding.ai.dto.AiIntentClassifyRespDto;
import com.example.tourding.external.openai.OpenAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentClassifierServiceTest {

    private final OpenAiClient openAiClient = mock(OpenAiClient.class);
    private final IntentClassifierService service = new IntentClassifierService(openAiClient);

    @Test
    void fallsBackToRuleClassificationWhenOpenAiFails() {
        ReflectionTestUtils.setField(service, "ruleFirst", false);
        when(openAiClient.classifyIntent("좀 더 편한 길로 가줘"))
                .thenThrow(new RuntimeException("timeout"));

        AiIntentClassifyRespDto result = service.classify("좀 더 편한 길로 가줘");

        assertThat(result.getIntent()).isEqualTo("BIKE_FRIENDLY");
        assertThat(result.getRouteAction()).isEqualTo("RECALCULATE_REMAINING_ROUTE");
    }
}
