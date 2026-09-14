package com.example.tourding.ai.service;

import com.example.tourding.ai.dto.AiRouteRecommendationIntentDto;
import com.example.tourding.external.openai.OpenAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiRouteRecommendationIntentServiceTest {

    private final OpenAiClient openAiClient = mock(OpenAiClient.class);
    private final AiRouteRecommendationIntentService service = new AiRouteRecommendationIntentService(openAiClient);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "recommendationAiFirst", false);
    }

    @Test
    void usesOpenAiFirstForComplexRouteConditionWithWaypoint() {
        AiRouteRecommendationIntentService aiFirstService = new AiRouteRecommendationIntentService(openAiClient);
        ReflectionTestUtils.setField(aiFirstService, "recommendationAiFirst", true);
        String text = "제일 어려운코스로 가는데 가다가 옹짬뽕 들렸다가 가줘";
        when(openAiClient.classifyRouteRecommendationIntent(text)).thenReturn(
                AiRouteRecommendationIntentDto.builder()
                        .targetDifficulty(4)
                        .waypointNames(java.util.List.of("제일 어려운코스로 가는데 가다가 옹짬뽕"))
                        .supported(true)
                        .build()
        );

        AiRouteRecommendationIntentDto result = aiFirstService.classify(text);

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getTargetDifficulty()).isEqualTo(4);
        assertThat(result.getWaypointNames()).containsExactly("옹짬뽕");
        verify(openAiClient).classifyRouteRecommendationIntent(text);
    }

    @Test
    void usesOpenAiFirstForSimpleWaypointRequest() {
        AiRouteRecommendationIntentService aiFirstService = new AiRouteRecommendationIntentService(openAiClient);
        ReflectionTestUtils.setField(aiFirstService, "recommendationAiFirst", true);
        String text = "올리브영 들리고싶어요";
        when(openAiClient.classifyRouteRecommendationIntent(text)).thenReturn(
                AiRouteRecommendationIntentDto.builder()
                        .waypointNames(java.util.List.of("올리브영"))
                        .supported(true)
                        .build()
        );

        AiRouteRecommendationIntentDto result = aiFirstService.classify(text);

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getWaypointNames()).containsExactly("올리브영");
        verify(openAiClient).classifyRouteRecommendationIntent(text);
    }

    @Test
    void fallsBackToRuleWhenOpenAiRejectsSimpleWaypointRequest() {
        AiRouteRecommendationIntentService aiFirstService = new AiRouteRecommendationIntentService(openAiClient);
        ReflectionTestUtils.setField(aiFirstService, "recommendationAiFirst", true);
        String text = "올리브영 가자";
        when(openAiClient.classifyRouteRecommendationIntent(text)).thenReturn(
                AiRouteRecommendationIntentDto.builder()
                        .waypointNames(java.util.List.of())
                        .explanation("지원하지 않는 추천 조건입니다.")
                        .supported(false)
                        .build()
        );

        AiRouteRecommendationIntentDto result = aiFirstService.classify(text);

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getWaypointNames()).containsExactly("올리브영");
        verify(openAiClient).classifyRouteRecommendationIntent(text);
    }

    @Test
    void splitsCombinedWaypointNamesFromOpenAi() {
        AiRouteRecommendationIntentService aiFirstService = new AiRouteRecommendationIntentService(openAiClient);
        ReflectionTestUtils.setField(aiFirstService, "recommendationAiFirst", true);
        String text = "가는길에 맥도날드랑 죽천해수욕장 들렸다 가는 코스로 해줘";
        when(openAiClient.classifyRouteRecommendationIntent(text)).thenReturn(
                AiRouteRecommendationIntentDto.builder()
                        .waypointNames(java.util.List.of("맥도날드랑 죽천해수욕장"))
                        .supported(true)
                        .build()
        );

        AiRouteRecommendationIntentDto result = aiFirstService.classify(text);

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getWaypointNames()).containsExactly("맥도날드", "죽천해수욕장");
        verify(openAiClient).classifyRouteRecommendationIntent(text);
    }

    @Test
    void cleansSpeechRecognitionNoiseFromWaypointName() {
        AiRouteRecommendationIntentDto result = service.classify("제일 길고 어려운길류 가는데 옹짬뽕 들렸다가 가줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getTargetDifficulty()).isEqualTo(3);
        assertThat(result.getWaypointNames()).containsExactly("옹짬뽕");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void classifiesMultipleConditionSentencesWithoutOpenAi() {
        AiRouteRecommendationIntentDto case1 = service.classify("오르막이 많은 어려운코스로 경로를 설정해주고, 가다가 근처 올리브영도 한번 들러줘");
        assertThat(case1.isSupported()).isTrue();
        assertThat(case1.getTargetDifficulty()).isEqualTo(3);
        assertThat(case1.getWaypointNames()).containsExactly("올리브영");

        AiRouteRecommendationIntentDto case2 = service.classify("30km 이하로 제일 빠른길로 가고, 중간에 스타벅스 들렸다가줘");
        assertThat(case2.isSupported()).isTrue();
        assertThat(case2.getMaxDistanceKm()).isEqualTo(30.0);
        assertThat(case2.getFastRoute()).isTrue();
        assertThat(case2.getWaypointNames()).containsExactly("스타벅스");

        AiRouteRecommendationIntentDto case3 = service.classify("로드 자전거로 포장도로 위주로 영일대 거쳐서 추천해줘");
        assertThat(case3.isSupported()).isTrue();
        assertThat(case3.getCyclingProfile()).isEqualTo("cycling-road");
        assertThat(case3.getPreferPaved()).isTrue();
        assertThat(case3.getWaypointNames()).containsExactly("영일대");

        AiRouteRecommendationIntentDto case4 = service.classify("초보도 갈 수 있게 계단이랑 물길 피하고 올리브영 들러서 가줘");
        assertThat(case4.isSupported()).isTrue();
        assertThat(case4.getTargetDifficulty()).isEqualTo(1);
        assertThat(case4.getAvoidSteps()).isTrue();
        assertThat(case4.getAvoidFords()).isTrue();
        assertThat(case4.getWaypointNames()).containsExactly("올리브영");

        AiRouteRecommendationIntentDto case5 = service.classify("큰도로 피해서 자전거도로 우선으로 가고, 첨성대 찍고 10km 이하로 가줘");
        assertThat(case5.isSupported()).isTrue();
        assertThat(case5.getAvoidMainRoad()).isTrue();
        assertThat(case5.getPreferBikeRoad()).isTrue();
        assertThat(case5.getMaxDistanceKm()).isEqualTo(10.0);
        assertThat(case5.getWaypointNames()).containsExactly("첨성대");

        AiRouteRecommendationIntentDto case6 = service.classify("전기자전거라서 여유롭게 보문호 돌아갔다가 가줘");
        assertThat(case6.isSupported()).isTrue();
        assertThat(case6.getCyclingProfile()).isEqualTo("cycling-electric");
        assertThat(case6.getFastRoute()).isFalse();
        assertThat(case6.getWaypointNames()).containsExactly("보문호");

        AiRouteRecommendationIntentDto case7 = service.classify("산악자전거로 임도 있는 힘든 코스로 황리단길 들렀다가 가줘");
        assertThat(case7.isSupported()).isTrue();
        assertThat(case7.getCyclingProfile()).isEqualTo("cycling-mountain");
        assertThat(case7.getTargetDifficulty()).isEqualTo(4);
        assertThat(case7.getWaypointNames()).containsExactly("황리단길");

        AiRouteRecommendationIntentDto case8 = service.classify("빙판길이랑 공사구간은 피하고 빠른길로 올리브영 들려서 가줘");
        assertThat(case8.isSupported()).isTrue();
        assertThat(case8.getAvoidIce()).isTrue();
        assertThat(case8.getAvoidConstruction()).isTrue();
        assertThat(case8.getFastRoute()).isTrue();
        assertThat(case8.getWaypointNames()).containsExactly("올리브영");

        AiRouteRecommendationIntentDto case9 = service.classify("포장도로 위주로 큰길 피하고 스타벅스 거쳐서 20키로 안으로 추천해줘");
        assertThat(case9.isSupported()).isTrue();
        assertThat(case9.getPreferPaved()).isTrue();
        assertThat(case9.getAvoidMainRoad()).isTrue();
        assertThat(case9.getMaxDistanceKm()).isEqualTo(20.0);
        assertThat(case9.getWaypointNames()).containsExactly("스타벅스");

        AiRouteRecommendationIntentDto case10 = service.classify("근처 카페 가고싶어");
        assertThat(case10.isSupported()).isFalse();
        assertThat(case10.getExplanation()).contains("시설 탐색");

        verifyNoInteractions(openAiClient);
    }

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
    void classifiesWaypointWithNaturalVisitExpressionsWithoutOpenAi() {
        AiRouteRecommendationIntentDto result = service.classify("올리브영 들렸다가줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getWaypointNames()).containsExactly("올리브영");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void classifiesWaypointWishExpressionsWithoutOpenAi() {
        AiRouteRecommendationIntentDto result = service.classify("올리브영 들리자");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getWaypointNames()).containsExactly("올리브영");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void classifiesCasualVisitExpressionsWithoutOpenAi() {
        AiRouteRecommendationIntentDto goTogether = service.classify("올리브영 가자");
        assertThat(goTogether.isSupported()).isTrue();
        assertThat(goTogether.getWaypointNames()).containsExactly("올리브영");

        AiRouteRecommendationIntentDto wantToStopBy = service.classify("올리브영 들릴래");
        assertThat(wantToStopBy.isSupported()).isTrue();
        assertThat(wantToStopBy.getWaypointNames()).containsExactly("올리브영");

        verifyNoInteractions(openAiClient);
    }

    @Test
    void removesLeadingTravelFillerFromWaypointName() {
        AiRouteRecommendationIntentDto result = service.classify("가다가 올리브영 들렸다가줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getWaypointNames()).containsExactly("올리브영");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void classifiesHardUphillRouteWithBrandWaypoint() {
        AiRouteRecommendationIntentDto result = service.classify("오르막이 많은 어려운코스로 경로를 설정해주고, 가다가 근처 올리브영도 한번 들러줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getTargetDifficulty()).isEqualTo(3);
        assertThat(result.getWaypointNames()).containsExactly("올리브영");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void classifiesWaypointWithPassByExpressionsWithoutOpenAi() {
        AiRouteRecommendationIntentDto result = service.classify("보문호 거쳐서 첨성대 찍고 가줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getWaypointNames()).containsExactly("보문호", "첨성대");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void classifiesOrderedWaypointNamesWithoutOpenAi() {
        AiRouteRecommendationIntentDto result = service.classify("칠포해수욕장 갔다가 올리브영 가자");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getWaypointNames()).containsExactly("칠포해수욕장", "올리브영");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void splitsWaypointNamesConnectedByParticleWithoutOpenAi() {
        AiRouteRecommendationIntentDto result = service.classify("가는길에 맥도날드랑 죽천해수욕장 들렸다 가는 코스로 해줘");

        assertThat(result.isSupported()).isTrue();
        assertThat(result.getWaypointNames()).containsExactly("맥도날드", "죽천해수욕장");
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
