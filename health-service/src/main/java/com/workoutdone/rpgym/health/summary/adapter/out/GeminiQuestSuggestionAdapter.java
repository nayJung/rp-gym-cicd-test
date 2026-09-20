package com.workoutdone.rpgym.health.summary.adapter.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workoutdone.rpgym.health.summary.application.QuestSuggestionAiPort;
import com.workoutdone.rpgym.health.summary.domain.MetricType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Spring AI 프레임워크를 거치지 않고, Gemini REST API를 RestClient로 직접 호출한다.
 * Spring AI 최신 버전(google-genai starter)이 Spring Framework 7.0 전용 클래스
 * (org.springframework.core.retry.RetryTemplate)를 요구해 Spring Boot 3.5.x와
 * 런타임 호환이 안 되는 문제를 우회하기 위함.
 */
@Component
public class GeminiQuestSuggestionAdapter implements QuestSuggestionAiPort {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";

    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiQuestSuggestionAdapter(RestClient.Builder restClientBuilder,
                                        @Value("${GEMINI_API_KEY:}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(5000);

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
    }

    @Override
    public String generateTitle(MetricType metricType, int shortageValue) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다.");
        }

        String metricLabel = switch (metricType) {
            case STEPS -> "걸음 수";
            case ACTIVE_MINUTES -> "활동 시간(분)";
            case ACTIVE_CALORIES -> "활동 칼로리(kcal)";
        };

        String prompt = """
                사용자가 오늘 '%s' 목표를 %d만큼 채우지 못했습니다.
                이 사용자에게 제안할 짧은 건강 Quest 제목을 한글로 한 문장만 출력하세요.
                예시: "20분 산책하기", "1,500보 더 걷기", "100kcal 운동하기"
                다른 설명 없이 제목 문구만 출력하세요.
                """.formatted(metricLabel, shortageValue);

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{Map.of("text", prompt)})
                }
        );

        String responseJson = restClient.post()
                .uri(ENDPOINT + "?key={key}", apiKey)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        return extractText(responseJson);
    }

    private String extractText(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            return root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText().trim();
        } catch (Exception e) {
            throw new IllegalStateException("Gemini 응답 파싱 실패: " + responseJson, e);
        }
    }
}