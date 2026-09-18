-- ============================================================
-- parties.metric — 파티 퀘스트가 볼 지표. 생성 시 확정, 불변.
-- ============================================================
-- 기존 행이 있을 수 있어 DEFAULT 로 채운 뒤 DEFAULT 를 뗀다. 앞으로는 애플리케이션이 반드시 넣는다.
ALTER TABLE game_service.parties
    ADD COLUMN metric VARCHAR(20) NOT NULL DEFAULT 'STEPS';
ALTER TABLE game_service.parties
    ALTER COLUMN metric DROP DEFAULT;

-- 값은 party 의 PartyMetric enum 과 1:1 (= quest 의 Metric 과 이름 동일). 여기 추가하면 enum 에도 추가해야 한다.
ALTER TABLE game_service.parties
    ADD CONSTRAINT ck_parties_metric
        CHECK (metric IN ('STEPS', 'ACTIVE_MINUTES', 'ACTIVE_CALORIES'));

-- 자동 매칭은 같은 metric 끼리만. metric 을 선두에 두면 조건 = metric, 정렬 = (current_member desc, created_at) 이 한 인덱스로 끝난다.
DROP INDEX IF EXISTS game_service.idx_parties_matching;
CREATE INDEX idx_parties_matching
    ON game_service.parties (metric, current_member DESC, created_at ASC)
    WHERE status = 'RECRUITING' AND visibility = 'PUBLIC';
