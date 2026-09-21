package com.workoutdone.rpgym.notification.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slack 연동(Bot Token, chat.postMessage)이 실제로 되는지만 확인하기 위한 임시 엔드포인트.
 *
 * game-service의 이벤트 계약(QUEST_CREATED인지 QUEST_SUGGESTED인지, questId가 오는지)이
 * 아직 PR 머지 전이라 확정되지 않았다. questoffer 쪽 로직(컨슈머/엔티티/DB)은 그 계약이
 * 확정될 때까지 걷어냈고, 이 컨트롤러로 Slack 발송 자체만 먼저 검증한다.
 *
 * 계약이 확정되면 이 컨트롤러는 지우고 questoffer 쪽 흐름으로 대체한다.
 */
@RestController
@RequiredArgsConstructor
public class SlackTestController {

    private static final String ACCEPT_ACTION_ID = "quest_offer_accept";
    private static final String REJECT_ACTION_ID = "quest_offer_reject";

    private final SlackApiClient slackApiClient;
    private final ObjectMapper objectMapper;

    /**
     * channel에는 공개 채널 ID(C...) 또는 사용자 Slack ID(U...)를 넣는다.
     * 사용자 ID를 넣으면 Slack이 그 사람과의 DM을 자동으로 연다.
     *
     * 예) POST /api/v1/notifications/test/slack-message?channel=U0123456789
     */
    @PostMapping("/api/v1/notifications/test/slack-message")
    public SlackMessageResult sendTestMessage(
            @RequestParam String channel,
            @RequestParam(defaultValue = "notification-service Slack 연동 테스트입니다.") String message
    ) {
        return slackApiClient.postMessage(channel, message);
    }

    /**
     * 실제 Quest 제안 카드와 같은 모양(본문 + 수락/거절 버튼)으로 발송해본다.
     * 버튼을 눌러도 아직 받아줄 Interactivity 엔드포인트가 없어서 클릭 자체는 에러가 날 텐데,
     * 지금은 메시지 모양과 버튼이 제대로 뜨는지만 확인하는 용도다.
     *
     * 예) POST /api/v1/notifications/test/slack-message-with-buttons?channel=U0123456789&title=20분 산책하기
     */
    @PostMapping("/api/v1/notifications/test/slack-message-with-buttons")
    public SlackMessageResult sendTestMessageWithButtons(
            @RequestParam String channel,
            @RequestParam(defaultValue = "테스트 퀘스트") String title,
            @RequestParam(defaultValue = "5") int rewardXp
    ) {
        String fallbackText = "오늘의 Quest 제안이 도착했어요";
        return slackApiClient.postMessage(channel, buildBlocksJson(title, rewardXp), fallbackText);
    }

    private String buildBlocksJson(String title, int rewardXp) {
        // 테스트용이라 questId 대신 아무 문자열이나 버튼 value로 넣는다.
        String dummyValue = "test-" + System.currentTimeMillis();

        ArrayNode blocks = objectMapper.createArrayNode();

        ObjectNode section = blocks.addObject();
        section.put("type", "section");
        section.putObject("text")
                .put("type", "mrkdwn")
                .put("text", "*" + title + "*\n보상: +" + rewardXp + " XP");

        ObjectNode actions = blocks.addObject();
        actions.put("type", "actions");
        ArrayNode elements = actions.putArray("elements");
        elements.add(buttonNode("수락", ACCEPT_ACTION_ID, dummyValue, "primary"));
        elements.add(buttonNode("거절", REJECT_ACTION_ID, dummyValue, "danger"));

        return blocks.toString();
    }

    private ObjectNode buttonNode(String label, String actionId, String value, String style) {
        ObjectNode button = objectMapper.createObjectNode();
        button.put("type", "button");
        button.putObject("text")
                .put("type", "plain_text")
                .put("text", label);
        button.put("style", style);
        button.put("action_id", actionId);
        button.put("value", value);
        return button;
    }
}
