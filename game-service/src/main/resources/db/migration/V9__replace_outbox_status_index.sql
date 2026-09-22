-- ============================================================
-- V9 -- outbox_events 폴링 인덱스를 부분 인덱스로 교체
-- 발행이 끝난 행은 PUBLISHED 로 바뀔 뿐 삭제되지 않는다.
-- 폴링은 status = 'PENDING' 만 찾는데 인덱스는 PUBLISHED 까지 들고 있었다.
-- 따라서 교체한다. 추가만 하면 유지할 인덱스가 하나 늘어 쓰기가 더 느려진다.
-- 이 인덱스를 쓰는 쿼리는 OutboxEventJpaRepository.findByStatusForUpdate 하나뿐이라 교체가 가능하다.
-- 교체한 이유는 최대치로 잡은 140,000건에 대한 데이터크기때문이다.
-- PUBLISHED 가 얼마나 쌓이든 이 인덱스는 PENDING 수에만 비례한다.
-- 데이터 증가에서 인덱스를 분리하는 것이 목적이다.
-- 남은 문제 : 테이블 자체는 계속 늘어난다. 발행 완료 행의 정리 정책이 없다.
-- 인덱스 교체는 그 증상 하나를 끊은 것이지 원인을 고친 것이 아니다.
-- ============================================================

CREATE INDEX idx_outbox_events_pending
    ON game_service.outbox_events (created_at)
 WHERE status = 'PENDING';

DROP INDEX game_service.idx_outbox_events_status;
