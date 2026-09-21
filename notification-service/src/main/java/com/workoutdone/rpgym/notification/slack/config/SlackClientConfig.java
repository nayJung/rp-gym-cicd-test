package com.workoutdone.rpgym.notification.slack.config;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackClientConfig {

    /*
    Slack Java SDK를 쓰려면 Bot Token을 들고 있는 MethodsClient 객체가 필요한데
    애플리케이션 전체에서 하나만 만들어서(싱글톤) 재사용하기 위해 @Bean으로 등록
     */
    @Bean
    public MethodsClient slackMethodsClient(@Value("${rpgym.slack.bot-token}") String botToken) {
        return Slack.getInstance().methods(botToken);
    }
}
