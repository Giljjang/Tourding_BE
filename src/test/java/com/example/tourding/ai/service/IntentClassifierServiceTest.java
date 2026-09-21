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

    @Test
    void classifiesGenericConvenienceRequestAsNearbyFacility() {
        ReflectionTestUtils.setField(service, "ruleFirst", true);

        AiIntentClassifyRespDto result = service.classify("가는 길에 편의점 들리고 싶어");

        assertThat(result.getIntent()).isEqualTo("FIND_FACILITY");
        assertThat(result.getRouteAction()).isEqualTo("SEARCH_FACILITY");
        assertThat(result.getWaypointQueries()).containsExactly("편의점");
        assertThat(result.getWaypointMode()).isEqualTo("FACILITY_CATEGORY");
    }

    @Test
    void rejectsGenericCafeRequestButAcceptsConcreteCafeName() {
        ReflectionTestUtils.setField(service, "ruleFirst", true);

        AiIntentClassifyRespDto generic = service.classify("카페 들러줘");
        AiIntentClassifyRespDto concrete = service.classify("스타벅스 들러줘");

        assertThat(generic.getIntent()).isEqualTo("UNSUPPORTED");
        assertThat(concrete.getIntent()).isEqualTo("ADD_TOUR_SPOT");
        assertThat(concrete.getWaypointQueries()).containsExactly("스타벅스");
    }

    @Test
    void doesNotTreatFastRouteRequestAsPlaceSearch() {
        ReflectionTestUtils.setField(service, "ruleFirst", true);

        AiIntentClassifyRespDto result = service.classify("빠르게 가고싶어");

        assertThat(result.getIntent()).isEqualTo("FASTER_ROUTE");
        assertThat(result.getRouteAction()).isEqualTo("RECALCULATE_REMAINING_ROUTE");
    }

    @Test
    void extractsPlaceNameFromGoThereRequest() {
        ReflectionTestUtils.setField(service, "ruleFirst", true);

        AiIntentClassifyRespDto result = service.classify("올리브영 가자");

        assertThat(result.getIntent()).isEqualTo("ADD_TOUR_SPOT");
        assertThat(result.getWaypointQueries()).containsExactly("올리브영");
    }
}
