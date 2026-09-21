package com.workoutdone.rpgym.health.summary.adapter.in;

import com.workoutdone.rpgym.health.summary.application.DeficientGoalDetectedEvent;
import com.workoutdone.rpgym.health.summary.application.QuestSuggestionAiPort;
import com.workoutdone.rpgym.health.summary.application.QuestSuggestionRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;


import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * QuestSuggestionEventListener가 실제로 "커밋 이후에만" 실행되는지,
 * "롤백되면 실행되지 않는지"를 검증한다.
 *
 * 이건 handle()을 직접 호출하는 단위 테스트(QuestSuggestionEventListenerTest)로는
 * 확인할 수 없다 (직접 호출하면 AFTER_COMMIT/트랜잭션 여부와 무관하게 그냥 실행되므로).
 * 그래서 진짜 트랜잭션(TransactionTemplate)을 통해 이벤트를 발행하고,
 * 커밋/롤백에 따라 리스너가 실행되는지를 Awaitility로 비동기 대기하며 확인한다.
 *
 * 전체 애플리케이션 컨텍스트(DB/Kafka/Eureka)는 필요 없으므로,
 * 트랜잭션 매니저만 갖춘 최소 구성(H2 인메모리)으로 가볍게 띄운다.
 */
@SpringBootTest(classes = {
        QuestSuggestionEventListenerAfterCommitTest.TestConfig.class,
        QuestSuggestionEventListener.class
})
@DisplayName("QuestSuggestionEventListener AFTER_COMMIT 동작 검증")
class QuestSuggestionEventListenerAfterCommitTest {

    @Configuration
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        RetryTemplate retryTemplate() {
            return new RetryTemplate();
        }
    }

    @MockBean
    QuestSuggestionAiPort aiPort;
    @MockBean
    QuestSuggestionRecorder questSuggestionRecorder;

    @Autowired
    ApplicationEventPublisher eventPublisher;
    @Autowired
    PlatformTransactionManager transactionManager;

    private final UUID userId = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();

    private DeficientGoalDetectedEvent sampleEvent() {
        return new DeficientGoalDetectedEvent(
                userId, UUID.randomUUID(), activityId,
                LocalDate.of(2026, 8, 30), Instant.parse("2026-08-30T01:00:00Z"),
                "STEPS", 4000
        );
    }

    @Test
    @DisplayName("트랜잭션이 커밋되면 그 이후에 handle이 실행된다")
    void 커밋되면_실행된다() {
        given(aiPort.generateTitle(any(), anyInt())).willReturn("제목");

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(sampleEvent())
        );

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(questSuggestionRecorder).record(any(), any(), any(), any(), any()));
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 handle이 실행되지 않는다")
    void 롤백되면_실행되지_않는다() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(sampleEvent());
            status.setRollbackOnly();
        });

        // 비동기 리스너가 혹시라도 뒤늦게 실행될 시간을 잠깐 준 뒤에도
        // 여전히 호출되지 않았는지 확인한다.
        await().pollDelay(1, TimeUnit.SECONDS)
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(questSuggestionRecorder, never()).record(any(), any(), any(), any(), any()));
    }
}