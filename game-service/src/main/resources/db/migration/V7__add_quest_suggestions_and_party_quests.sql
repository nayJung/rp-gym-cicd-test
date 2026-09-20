-- ============================================================
-- V7 -- MVP 이후 1차: 제안 수락 흐름 + 파티 퀘스트
--
--
-- 세 덩어리가 들어간다.
--   (1) 기존 CHECK 제약 확장 -- 이걸 안 하면 아래 테이블을 만들어도 첫 INSERT에서 터진다
--   (2) quest_suggestions        -- 제안을 보관하고, 수락 시점에 Quest를 만든다
--   (3) party_quests / party_quest_members
--
-- 자바 enum(SourceType · AggregateType · OutboxEventType)은 이 파일과 반드시 같은 커밋에서
-- 움직인다. 한쪽만 바꾸면 기동은 성공하고 런타임에 터진다.

-- ============================================================
-- (1) 기존 CHECK 제약 확장
--
-- V2의 CHECK가 값 목록을 문자열로 박아놨다. 새 값을 쓰는 순간
-- DataIntegrityViolationException이 나고, 그 경로가 Kafka 컨슈머라면
-- FixedBackOff(2s, UNLIMITED_ATTEMPTS)를 만나 파티션이 영원히 멈춘다.
-- 재시도해도 같은 값이 같은 제약에 걸리는, 절대 성공하지 못하는 재시도다.
-- ============================================================

-- ACHIEVEMENT를 함께 넣는다.
-- 업적은 (건우) 담당자 기능이지만 xp_ledgers는 내 소유 테이블이고, XpGrantService를 통해
-- 들어온다. 이 제약을 두 사람이 번갈아 DROP/ADD하면 나중 마이그레이션이 앞의 값을
-- 지우기 쉽다. 소유자가 한 번에 열어두는 편이 안전하다.
ALTER TABLE game_service.xp_ledgers
    DROP CONSTRAINT ck_xp_ledgers_source_type;
ALTER TABLE game_service.xp_ledgers
    ADD CONSTRAINT ck_xp_ledgers_source_type
    CHECK (source_type IN ('QUEST', 'PARTY_QUEST', 'ACHIEVEMENT'));

-- QUEST_SUGGESTION이 애그리거트로 들어온다.
-- Notification이 Slack 카드에 쓸 식별자를 Game이 발급하게 되면서,
-- Game도 QUEST_SUGGESTED를 game.events로 발행한다.
ALTER TABLE game_service.outbox_events
    DROP CONSTRAINT ck_outbox_events_aggregate_type;
ALTER TABLE game_service.outbox_events
    ADD CONSTRAINT ck_outbox_events_aggregate_type
    CHECK (aggregate_type IN ('QUEST', 'PARTY_QUEST', 'QUEST_SUGGESTION'));

-- uk_outbox_events_aggregate(aggregate_type, aggregate_id, event_type)가
-- "하나의 애그리거트는 같은 종류의 이벤트를 최대 1회만 발행한다"를 DB에 박은 것이다.
-- QUEST_SUGGESTED의 aggregate_id는 suggestion_id이므로 제안 하나당 카드 한 장이 된다.
ALTER TABLE game_service.outbox_events
    DROP CONSTRAINT ck_outbox_events_event_type;
ALTER TABLE game_service.outbox_events
    ADD CONSTRAINT ck_outbox_events_event_type
    CHECK (event_type IN ('QUEST_CREATED', 'QUEST_COMPLETED',
                          'QUEST_SUGGESTED', 'PARTY_QUEST_COMPLETED'));


-- ============================================================
-- (2) quest_suggestions
--
-- 수락 대기 상태를 quests.status에 PENDING으로 얹지 않는다.
-- 얹으면 quests의 불변식이 전부 무너진다 -- "ACTIVE Quest는 baseline이 확정돼 있다",
-- "유저당 동시 ACTIVE 1개" 같은 조건에 매번 AND status <> 'PENDING'이 붙고,
-- Quest.applySnapshot이 수락되지 않은 행을 만날 수 있게 된다.
--
-- 테이블을 하나 늘리는 대신 "quests에 행이 있다 = 유저가 수락했다"는 등식을 지킨다.
-- 그 결과 Quest 애그리거트와 판정 코드는 한 줄도 바뀌지 않는다.
-- ============================================================

CREATE TABLE game_service.quest_suggestions (
    -- Health event_outbox.event_id를 그대로 쓴다. 같은 제안이 재배달되면 PK 충돌로 걸린다.
    -- 애플리케이션이 그 앞에서 조용히 먼저 답하고(1차), 이 PK는 2차 방어선이다.
    suggestion_id        UUID         NOT NULL,
    user_id              UUID         NOT NULL,
    title                VARCHAR(100) NOT NULL,
    metric               VARCHAR(20)  NOT NULL,
    target_val           INTEGER      NOT NULL,
    activity_date        DATE         NOT NULL,

    -- 제안이 근거한 스냅샷의 측정 시각.
    -- 누적값(baseline)은 여기 담지 않는다 -- QuestSuggested payload에 누적값이 없다.
    -- 수락 시점에 user_latest_snapshots를 읽어, 이 시각과 일치할 때만 그 스냅샷의
    -- 누적값을 baseline으로 쓴다. 즉 이 컬럼은 값이 아니라 포인터다.
    based_on_measured_at TIMESTAMPTZ  NOT NULL,

    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',

    -- based_on_measured_at + 30분. 내 서버 시계(Instant.now())로 계산하지 않는다.
    -- 이벤트가 들고 온 시각을 기준으로 해야 언제 처리하든 같은 값이 나온다(멱등).
    -- 만료된 제안을 배치로 청소하지 않고 이 컬럼으로 판정한다.
    expires_at           TIMESTAMPTZ  NOT NULL,

    decided_at           TIMESTAMPTZ,
    quest_id             UUID,

    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_quest_suggestions PRIMARY KEY (suggestion_id),

    -- 방향에 주의. quests -> quest_suggestions로는 FK를 걸지 않는다.
    -- 기존 개발 DB의 quests 행에는 대응하는 제안 행이 없어서 마이그레이션이 깨진다.
    CONSTRAINT fk_quest_suggestions_quest FOREIGN KEY (quest_id)
        REFERENCES game_service.quests (quest_id),

    CONSTRAINT ck_quest_suggestions_target_val CHECK (target_val > 0),
    CONSTRAINT ck_quest_suggestions_metric
        CHECK (metric IN ('STEPS', 'ACTIVE_MINUTES', 'ACTIVE_CALORIES')),
    CONSTRAINT ck_quest_suggestions_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'SUPERSEDED')),
    CONSTRAINT ck_quest_suggestions_expires_at
        CHECK (expires_at > based_on_measured_at),
    -- 수락됐는데 만들어진 Quest가 없는 상태를 DB가 막는다.
    CONSTRAINT ck_quest_suggestions_accepted
        CHECK (status <> 'ACCEPTED' OR quest_id IS NOT NULL)
);

-- 수락 API는 suggestion_id(PK)로만 찾는다.
-- "내 PENDING 제안 목록" 조회 API가 실제로 생기면 그때 (user_id, status) 인덱스를 넣는다.


-- ============================================================
-- (3) party_quests
--
-- 개인 quests 테이블에 얹지 않는 이유: 파티는 baseline이 멤버별이라
-- quests.baseline_val 컬럼 하나에 넣을 값이 없다. NULL 허용으로 바꾸는 순간
-- "ACTIVE면 baseline이 확정돼 있다"는 불변식이 깨지고 판정 코드에 분기가 생긴다.
-- ============================================================

CREATE TABLE game_service.party_quests (
    party_quest_id UUID         NOT NULL,

    -- 파티 담당자 소유 테이블 참조. 물리 FK를 걸지 않았다 --
    -- party 테이블의 컬럼명·타입 확정본을 아직 못 받았고, 이 마이그레이션 시점에
    -- 그 테이블이 존재하지 않을 수 있다. 확정되면 FK만 추가하는 마이그레이션을 따로 낸다.
    party_id       BIGINT       NOT NULL,

    title          VARCHAR(100) NOT NULL,
    metric         VARCHAR(20)  NOT NULL,

    -- 멤버 기여분의 합계 목표
    target_val     INTEGER      NOT NULL,

    -- 경합 지점. 반드시 UPDATE ... SET current_val = current_val + :delta 한 문장으로만
    -- 갱신한다. SELECT 후 애플리케이션에서 더해서 쓰면 lost update가 난다.
    -- 완료 후에도 계속 누적되어 target_val을 초과할 수 있다 -- 의도된 동작이다.
    -- 여기에 status 가드를 걸면 완료 직후 도착한 이벤트에서 멤버 행만 갱신되고
    -- 합계가 안 올라가, 정합성 등식 current_val == SUM(contributed_val)이 깨진다.
    current_val    INTEGER      NOT NULL DEFAULT 0,

    -- 완료 선점 가드 컬럼. 완료 여부는 이 컬럼을 건 조건부 UPDATE가 단독으로 결정한다.
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    -- 완료 시 멤버 1인당 지급 XP. 생성 시점 정책값의 스냅샷이다.
    reward_xp      INTEGER      NOT NULL,

    started_at     TIMESTAMPTZ  NOT NULL,
    -- 최대 1일. 건강 데이터는 자정마다 리셋되는 당일 누적값이고 어제 값을 보관하는
    -- 테이블이 없어서, 다일 퀘스트는 날짜별 증분을 따로 쌓아야 한다.
    expired_at     TIMESTAMPTZ  NOT NULL,

    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_party_quests PRIMARY KEY (party_quest_id),
    CONSTRAINT ck_party_quests_target_val  CHECK (target_val > 0),
    CONSTRAINT ck_party_quests_current_val CHECK (current_val >= 0),
    CONSTRAINT ck_party_quests_reward_xp   CHECK (reward_xp > 0),
    CONSTRAINT ck_party_quests_expired_at  CHECK (expired_at > started_at),
    CONSTRAINT ck_party_quests_metric
        CHECK (metric IN ('STEPS', 'ACTIVE_MINUTES', 'ACTIVE_CALORIES')),
    CONSTRAINT ck_party_quests_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'EXPIRED'))
);

-- "파티당 진행 중 1개" 판정용.
-- 부분 유니크 인덱스(... WHERE status = 'ACTIVE')를 걸지 않았다. 걸면 만료됐지만
-- 아직 EXPIRED로 안 바뀐 행이 새 퀘스트 생성을 막는다. 판정 기준은
-- status = 'ACTIVE' AND expired_at > now() 이고, 청소 스케줄러가 늦게 돌아도 틀리지 않는다.
CREATE INDEX idx_party_quests_party_status
    ON game_service.party_quests (party_id, status, expired_at);

-- version 컬럼을 두지 않는다. 이 테이블의 두 UPDATE는 JPA 변경 감지가 아니라
-- 네이티브 조건부 UPDATE다. @Version을 붙이면 조건부 UPDATE가 version을 올리지 않아
-- 영속성 컨텍스트와 어긋난다. 동시성 방어는 조건부 UPDATE 자체가 한다.


-- ============================================================
-- (4) party_quest_members
--
-- 파티 담당자의 party_member와 별개 테이블이다. 덮어쓰지 않는다.
--   party_member        -- "이 유저가 이 파티에 있는가"        (파티당 1행, 담당자 소유)
--   party_quest_members -- "이 유저가 이 퀘스트에 얼마나 쌓았나" (퀘스트당 1행, 내 소유)
--
-- 명단은 생성 시점에 복사하고 그 뒤로 party_member를 읽지 않는다.
-- "시작 후 탈퇴/중도 합류 불가"가 확정됐으므로 명단이 변하지 않는다.
-- ============================================================

CREATE TABLE game_service.party_quest_members (
    party_quest_member_id    UUID        NOT NULL,
    party_quest_id           UUID        NOT NULL,
    user_id                  UUID        NOT NULL,

    -- 이 멤버가 퀘스트 시작 시점에 이미 쌓아둔 누적값. 멤버마다 다르다.
    -- 생성 시 user_latest_snapshots에서 멤버 수만큼 한 번에 읽어 채운다.
    --
    -- NULL = 아직 미확정. 한 번도 동기화한 적 없는 유저만 해당한다.
    -- 그 멤버의 첫 이벤트가 도착할 때 그 누적값으로 확정하고 기여는 0으로 둔다.
    -- NULL을 0으로 취급하면 안 된다 -- 아침에 이미 걸어둔 활동이 통째로 소급 인정돼
    -- 공짜 완료가 난다.
    baseline_val             INTEGER,

    -- 이 멤버가 시작 이후 쌓은 기여분. 대입으로 갱신한다(= 이번 누적값 - baseline_val).
    -- += 로 누적하면 이벤트 하나가 유실될 때 그만큼이 영구히 사라진다.
    -- 대입이면 다음 스냅샷의 누적값이 알아서 메운다. 퀘스트가 1일이라
    -- 이 자기치유가 날짜 경계를 넘을 일이 없다.
    contributed_val          INTEGER     NOT NULL DEFAULT 0,

    -- 이 값 이하의 이벤트는 중복이거나 순서 역전이므로 무시한다.
    -- 조건부 UPDATE의 WHERE에 들어가는 워터마크다.
    last_applied_measured_at TIMESTAMPTZ,

    created_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_party_quest_members PRIMARY KEY (party_quest_member_id),
    CONSTRAINT fk_party_quest_members_quest FOREIGN KEY (party_quest_id)
        REFERENCES game_service.party_quests (party_quest_id),
    CONSTRAINT ck_party_quest_members_contributed CHECK (contributed_val >= 0),
    CONSTRAINT ck_party_quest_members_baseline
        CHECK (baseline_val IS NULL OR baseline_val >= 0)
);

-- 같은 멤버가 한 퀘스트에 두 번 들어가는 것을 막는다.
-- 완료 시 XP는 멤버당 1건이고, 멱등키가 (user_id, 'PARTY_QUEST', party_quest_id)이므로
-- 멤버 행이 둘이면 두 번째 XP INSERT가 유니크 제약에 걸린다. 그 전에 여기서 막는다.
CREATE UNIQUE INDEX uk_party_quest_members_user
    ON game_service.party_quest_members (party_quest_id, user_id);

-- 이벤트 처리 경로의 조회: "이 유저가 속한 ACTIVE 파티 퀘스트의 내 멤버 행"
CREATE INDEX idx_party_quest_members_user
    ON game_service.party_quest_members (user_id);

-- 멤버 4명 상한은 DB로 강제하지 않는다. 행 개수 제약은 CHECK로 표현할 수 없고
-- 트리거는 이 범위에서 과하다. 생성 API에서 막는다.
