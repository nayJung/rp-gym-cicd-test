-- ============================================================
-- parties
-- ============================================================
CREATE TABLE game_service.parties (
  id UUID NOT NULL,
  party_name VARCHAR(50) NOT NULL,
  owner_id UUID NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RECRUITING',
  visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE',
  -- 파티 퀘스트가 볼 지표. 생성 시 확정, 불변. 애플리케이션이 반드시 넣는다 (DEFAULT 없음).
  metric VARCHAR(20) NOT NULL,
  max_member INTEGER NOT NULL DEFAULT 4,
  current_member INTEGER NOT NULL DEFAULT 1,
  matching_deadline_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_parties PRIMARY KEY (id),
  CONSTRAINT ck_parties_status     CHECK (status IN ('RECRUITING', 'ACTIVE', 'ENDED', 'DISBANDED')),
  CONSTRAINT ck_parties_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
-- 값은 party 의 PartyMetric enum 과 1:1 (= quest 의 Metric 과 이름 동일). 여기 추가하면 enum 에도 추가해야 한다.
  CONSTRAINT ck_parties_metric     CHECK (metric IN ('STEPS', 'ACTIVE_MINUTES', 'ACTIVE_CALORIES')),
  CONSTRAINT ck_parties_max_member CHECK (max_member >= 1),
-- 카운터가 파생값이라 오염될 수 있다. 조건부 UPDATE 가 1차 방어, 이 CHECK 가 최종 방어다.
  CONSTRAINT ck_parties_member_range CHECK (current_member >= 0 AND current_member <= max_member)
);

-- 자동 매칭 탐색 전용. 조건이 고정(RECRUITING + PUBLIC)이라 부분 인덱스가 작다.
-- 자동 매칭은 같은 metric 끼리만. metric 을 선두에 두면
-- 조건 = metric, 정렬 = (current_member desc, created_at) 이 한 인덱스로 끝난다.
CREATE INDEX idx_parties_matching
    ON game_service.parties (metric, current_member DESC, created_at ASC)
    WHERE status = 'RECRUITING' AND visibility = 'PUBLIC';

-- 모집 마감 배치
CREATE INDEX idx_parties_recruiting_deadline
    ON game_service.parties (matching_deadline_at)
    WHERE status = 'RECRUITING';

-- 파티 종료 배치
CREATE INDEX idx_parties_active_ends
    ON game_service.parties (ends_at)
    WHERE status = 'ACTIVE';

-- ============================================================
-- party_members
-- ============================================================
CREATE TABLE game_service.party_members (
id UUID NOT NULL,
party_id UUID NOT NULL,
user_id UUID NOT NULL,
role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
joined_at TIMESTAMPTZ NOT NULL,
left_at TIMESTAMPTZ,
created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
CONSTRAINT pk_party_members PRIMARY KEY (id),
CONSTRAINT fk_party_members_party FOREIGN KEY (party_id) REFERENCES game_service.parties (id),
CONSTRAINT ck_party_members_role   CHECK (role IN ('OWNER', 'MEMBER')),
CONSTRAINT ck_party_members_status CHECK (status IN ('ACTIVE', 'LEFT')),
-- 상태와 시각이 어긋나지 않게. ACTIVE 면 left_at 이 없고, LEFT 면 반드시 있다.
CONSTRAINT ck_party_members_left CHECK (
(status = 'ACTIVE' AND left_at IS NULL) OR (status = 'LEFT' AND left_at IS NOT NULL)
)
);

-- 1인 1파티. soft delete 라 (party_id, user_id) 유니크는 못 걸고, ACTIVE 행에만 건다.
-- 생성 · 수락 · 매칭 세 경로 모두 이 인덱스가 최종 방어선이다.
CREATE UNIQUE INDEX uk_party_members_active_one_party
    ON game_service.party_members (user_id)
    WHERE status = 'ACTIVE';

-- 멤버 목록 조회, 파티장 승계(joined_at 최소)
CREATE INDEX idx_party_members_party_active
    ON game_service.party_members (party_id, joined_at)
    WHERE status = 'ACTIVE';

-- 주간 랭킹 집계 조인 (xp_ledgers 와 소속 기간 교집합)
CREATE INDEX idx_party_members_user_period
    ON game_service.party_members (user_id, joined_at, left_at);

-- ============================================================
-- party_invitations
-- ============================================================
CREATE TABLE game_service.party_invitations (
id           UUID        NOT NULL,
party_id     UUID        NOT NULL,
inviter_id   UUID        NOT NULL,
invitee_id   UUID        NOT NULL,
status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
expires_at   TIMESTAMPTZ NOT NULL,
responded_at TIMESTAMPTZ,
created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
CONSTRAINT pk_party_invitations PRIMARY KEY (id),
CONSTRAINT fk_party_invitations_party FOREIGN KEY (party_id) REFERENCES game_service.parties (id),
CONSTRAINT ck_party_invitations_status
CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELED'))
);

-- 같은 파티에 같은 사람 PENDING 초대 중복 금지. 처리된 초대는 제외되므로 거절 후 재초대는 된다.
CREATE UNIQUE INDEX uk_party_invitations_pending
    ON game_service.party_invitations (party_id, invitee_id)
    WHERE status = 'PENDING';

-- 받은 초대 조회
CREATE INDEX idx_party_invitations_invitee_pending
    ON game_service.party_invitations (invitee_id, created_at DESC)
    WHERE status = 'PENDING';

-- 초대 시 PENDING 수 세기, 마감 시 일괄 CANCELED
CREATE INDEX idx_party_invitations_party_pending
    ON game_service.party_invitations (party_id)
    WHERE status = 'PENDING';

-- 만료 정리 배치. 만료된 PENDING 을 오래된 순으로 limit 만큼 읽어 건별로 닫는다.
CREATE INDEX idx_party_invitations_expires
    ON game_service.party_invitations (expires_at)
    WHERE status = 'PENDING';

-- ============================================================
-- party_outbox_events — 파티 전용 Outbox
-- ============================================================
-- outbox_events(quest 소유)와 구조는 같고 테이블만 다르다. 파티 이벤트를 위해 quest 쪽 CHECK 제약을
-- 건드리지 않으려고 분리했다. 발행 토픽은 같은 game.events 이고 소비 측은 eventType 헤더로만 분기한다.
CREATE TABLE game_service.party_outbox_events (
    outbox_id      UUID        NOT NULL,
    aggregate_type VARCHAR(30) NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     VARCHAR(50) NOT NULL,
    event_id       UUID        NOT NULL,
    partition_key  VARCHAR(50) NOT NULL,
    payload        JSONB       NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count    INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMPTZ,

    CONSTRAINT pk_party_outbox_events PRIMARY KEY (outbox_id),
    CONSTRAINT ck_party_outbox_events_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_party_outbox_events_aggregate_type
        CHECK (aggregate_type IN ('PARTY', 'PARTY_MEMBER', 'PARTY_INVITATION')),
    -- PARTY_INVITATION_CLOSED = 초대의 끝(수락 · 거절 · 만료 · 취소). 알림이 슬랙 버튼을 거두는 신호다.
    CONSTRAINT ck_party_outbox_events_event_type
        CHECK (event_type IN ('PARTY_INVITED', 'PARTY_INVITATION_CLOSED', 'PARTY_MEMBER_JOINED',
                              'PARTY_MEMBER_LEFT', 'PARTY_MATCHED', 'PARTY_ENDED')),
    CONSTRAINT ck_party_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE UNIQUE INDEX uk_party_outbox_events_event_id
    ON game_service.party_outbox_events (event_id);

-- 같은 애그리거트에 같은 이벤트가 두 번 적재되는 것을 막는다.
-- PARTY_MEMBER_JOINED 는 멤버 행(id) 기준이라 유저가 다른 파티에 다시 들어가도 충돌하지 않는다.
CREATE UNIQUE INDEX uk_party_outbox_events_aggregate
    ON game_service.party_outbox_events (aggregate_type, aggregate_id, event_type);

-- 릴레이 폴링. (status, created_at) 순으로 PENDING 만 집는다.
CREATE INDEX idx_party_outbox_events_status
    ON game_service.party_outbox_events (status, created_at);
