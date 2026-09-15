-- ============================================================
-- parties
-- ============================================================
CREATE TABLE game_service.parties (
  id UUID NOT NULL,
  party_name VARCHAR(50) NOT NULL,
  owner_id UUID NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RECRUITING',
  visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE',
  max_member INTEGER NOT NULL DEFAULT 4,
  current_member INTEGER NOT NULL DEFAULT 1,
  matching_deadline_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_parties PRIMARY KEY (id),
  CONSTRAINT ck_parties_status     CHECK (status IN ('RECRUITING', 'ACTIVE', 'ENDED', 'DISBANDED')),
  CONSTRAINT ck_parties_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
  CONSTRAINT ck_parties_max_member CHECK (max_member >= 1),
-- 카운터가 파생값이라 오염될 수 있다. 조건부 UPDATE 가 1차 방어, 이 CHECK 가 최종 방어다.
  CONSTRAINT ck_parties_member_range CHECK (current_member >= 0 AND current_member <= max_member)
);

-- 자동 매칭 탐색 전용. 조건이 고정(RECRUITING + PUBLIC)이라 부분 인덱스가 작다.
CREATE INDEX idx_parties_matching
    ON game_service.parties (current_member DESC, created_at ASC)
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

-- 만료 정리 배치
CREATE INDEX idx_party_invitations_expires
    ON game_service.party_invitations (expires_at)
    WHERE status = 'PENDING';

-- ============================================================
-- outbox_events — 파티 이벤트 허용
-- ============================================================
-- V2 의 CHECK 가 QUEST 만 허용한다. 제약 이름은 V2 와 동일하게 다시 건다.
ALTER TABLE game_service.outbox_events
DROP CONSTRAINT ck_outbox_events_aggregate_type;
ALTER TABLE game_service.outbox_events
    ADD CONSTRAINT ck_outbox_events_aggregate_type
        CHECK (aggregate_type IN ('QUEST', 'PARTY', 'PARTY_MEMBER', 'PARTY_INVITATION'));

ALTER TABLE game_service.outbox_events
DROP CONSTRAINT ck_outbox_events_event_type;
ALTER TABLE game_service.outbox_events
    ADD CONSTRAINT ck_outbox_events_event_type
        CHECK (event_type IN (
                              'QUEST_CREATED', 'QUEST_COMPLETED',
                              'PARTY_INVITED', 'PARTY_MEMBER_JOINED', 'PARTY_MEMBER_LEFT', 'PARTY_MATCHED', 'PARTY_ENDED'
            ));
