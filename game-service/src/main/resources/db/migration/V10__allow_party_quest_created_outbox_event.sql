-- 파티 퀘스트 생성 이벤트를 game.events 로 발행한다.
-- 파티장은 이 API 의 응답으로 결과를 이미 받지만, 나머지 멤버는 자기가 만들지 않은 퀘스트가
-- 진행도에 걸리는 쪽이라 시작을 알 방법이 없었다. PARTY_QUEST_COMPLETED 만 있는 상태로 두면
-- 멤버가 받는 첫 알림이 "완료" 카드가 된다.
-- 값 목록을 CHECK 로 박아 둔 탓에 enum 에만 값을 넣으면 적재가 제약 위반으로 실패한다.
-- 그 실패는 생성 트랜잭션 전체를 롤백시키므로 퀘스트 자체가 만들어지지 않는다.
-- uk_outbox_events_aggregate(aggregate_type, aggregate_id, event_type) 가
-- 파티 퀘스트 하나당 생성 이벤트 한 건을 보장한다.
ALTER TABLE game_service.outbox_events
    DROP CONSTRAINT ck_outbox_events_event_type;
ALTER TABLE game_service.outbox_events
    ADD CONSTRAINT ck_outbox_events_event_type
    CHECK (event_type IN ('QUEST_CREATED', 'QUEST_COMPLETED',
                          'QUEST_SUGGESTED',
                          'PARTY_QUEST_CREATED', 'PARTY_QUEST_COMPLETED'));
