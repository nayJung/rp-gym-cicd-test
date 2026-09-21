-- 정리 배치는 status = 'PUBLISHED' AND published_at < publishedBefore 로 삭제 대상을 찾는다.
-- 기존 idx_health_activity_outbox_status (status, seq)는 발행 폴링용이라
-- published_at 조건을 커버하지 못한다.
--
-- 선두 컬럼이 등치 조건(status), 두 번째가 범위 조건(published_at)이라
-- 복합 인덱스가 그대로 활용된다.
CREATE INDEX idx_health_activity_outbox_cleanup
    ON health_service.event_outbox (status, published_at);