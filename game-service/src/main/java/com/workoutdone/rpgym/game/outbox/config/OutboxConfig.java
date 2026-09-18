package com.workoutdone.rpgym.game.outbox.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Outbox 발행기가 이 서비스의 유일한 스케줄 컴포넌트이므로 @EnableScheduling을 여기서 켠다.
 * 다른 곳에 스케줄 작업이 생기면 그때 config로 옮긴다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxPublishProperties.class)
public class OutboxConfig {
}
