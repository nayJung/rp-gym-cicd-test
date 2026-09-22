-- ============================================================
-- 업적 (achievement) — 개인 · 파티 공용, 테이블 2개
-- ============================================================
--   achievements       어떤 업적이 있는가 (정의 · 시드)
--   user_achievements  owner(유저 or 파티) 별 업적 1행. 진행 중이면 IN_PROGRESS, 따면 ACHIEVED
--
-- 진행도 테이블 · 파티 획득 테이블을 따로 두지 않는다 —
-- (owner_type, owner_id, achievement_id) 한 행이 카운터 + 획득 기록을 겸한다.
-- 기획 미확정 단계라 테이블 수를 줄이고, condition_type 만 늘려가며 대응한다.

-- ============================================================
-- achievements — 업적 정의
-- ============================================================
CREATE TABLE game_service.achievements (
                                           achievement_id  UUID         NOT NULL,
                                           code            VARCHAR(50)  NOT NULL,
                                           name            VARCHAR(50)  NOT NULL,
                                           description     VARCHAR(255) NOT NULL,
                                           scope           VARCHAR(10)  NOT NULL,
                                           condition_type  VARCHAR(30)  NOT NULL,
                                           condition_value INTEGER      NOT NULL DEFAULT 1,
                                           reward_xp       INTEGER      NOT NULL DEFAULT 0,
                                           status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                                           created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                           CONSTRAINT pk_achievements PRIMARY KEY (achievement_id),
                                           CONSTRAINT ck_achievements_scope CHECK (scope IN ('PERSONAL', 'PARTY')),
    -- 코드가 switch 하는 값. 기획 확정되면 여기와 enum 에 같이 추가한다.
                                           CONSTRAINT ck_achievements_condition_type CHECK (condition_type IN (
                                                                                                               'DAILY_GOAL_COUNT',      -- 일일 목표 누적 달성 횟수 (최초 달성 = 1)
                                                                                                               'DAILY_GOAL_STREAK'      -- 일일 목표 연속 달성 일수
                                               )),
                                           CONSTRAINT ck_achievements_condition_value CHECK (condition_value >= 1),
                                           CONSTRAINT ck_achievements_reward_xp CHECK (reward_xp >= 0),
                                           CONSTRAINT ck_achievements_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- 코드에서 UUID 대신 이걸로 찾는다. 시드 UUID 가 환경마다 달라도 코드는 안 바뀐다.
CREATE UNIQUE INDEX uk_achievements_code
    ON game_service.achievements (code);

CREATE INDEX idx_achievements_condition
    ON game_service.achievements (condition_type, status);

-- ============================================================
-- user_achievements — owner 별 업적 진행 · 획득
-- ============================================================
CREATE TABLE game_service.user_achievements (
                                                user_achievement_id UUID        NOT NULL,
                                                owner_type          VARCHAR(10) NOT NULL,
                                                owner_id            UUID        NOT NULL,
                                                achievement_id      UUID        NOT NULL,
                                                status              VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
                                                current_value       INTEGER     NOT NULL DEFAULT 0,
    -- 마지막으로 센 활동 날짜. 같은 날 이벤트가 두 번 와도 한 번만 센다 (멱등).
    -- 연속 판정: last + 1일 == 오늘 → current+1, 그 외 → current=1
                                                last_counted_date   DATE,
                                                achieved_at         TIMESTAMPTZ,
                                                version             BIGINT      NOT NULL DEFAULT 0,
                                                created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                                CONSTRAINT pk_user_achievements PRIMARY KEY (user_achievement_id),
                                                CONSTRAINT fk_user_achievements_achievement
                                                    FOREIGN KEY (achievement_id) REFERENCES game_service.achievements (achievement_id),
                                                CONSTRAINT ck_user_achievements_owner_type CHECK (owner_type IN ('USER', 'PARTY')),
                                                CONSTRAINT ck_user_achievements_status CHECK (status IN ('IN_PROGRESS', 'ACHIEVED')),
                                                CONSTRAINT ck_user_achievements_current_value CHECK (current_value >= 0),
    -- ACHIEVED 면 achieved_at 필수, IN_PROGRESS 면 없어야 한다
                                                CONSTRAINT ck_user_achievements_achieved_at
                                                    CHECK ((status = 'ACHIEVED') = (achieved_at IS NOT NULL))
);

-- owner 당 업적 1행. 중복 지급의 최종 방어선
CREATE UNIQUE INDEX uk_user_achievements_owner_achievement
    ON game_service.user_achievements (owner_type, owner_id, achievement_id);

-- 내 업적 목록 (획득 순)
CREATE INDEX idx_user_achievements_owner
    ON game_service.user_achievements (owner_type, owner_id, status, achieved_at DESC);

-- ============================================================
-- xp_ledgers.source_type 에 ACHIEVEMENT 추가
-- ============================================================
-- 개인: (user_id, 'ACHIEVEMENT', user_achievement_id)
-- 파티: 멤버마다 (member_user_id, 'ACHIEVEMENT', user_achievement_id) — 기존 유니크가 멤버당 1회 보장
--
-- PARTY_QUEST 를 같이 넣는다. 지금 이 서비스에 파티 퀘스트 코드는 없지만,
-- 이 CHECK 는 값 목록을 통째로 다시 쓰는 형태라 뒤에 도는 쪽이 앞의 값을 조용히 지운다.
-- 퀘스트 담당의 V7 도 같은 제약을 세 값으로 다시 쓰므로 어느 쪽이 먼저 돌든 결과가 같아야 한다.
-- 빠뜨리면 기동에는 성공하고 파티 퀘스트 XP 의 첫 INSERT 에서 터진다.
ALTER TABLE game_service.xp_ledgers
DROP CONSTRAINT ck_xp_ledgers_source_type;
ALTER TABLE game_service.xp_ledgers
    ADD CONSTRAINT ck_xp_ledgers_source_type
    CHECK (source_type IN ('QUEST', 'PARTY_QUEST', 'ACHIEVEMENT'));

-- ============================================================
-- 시드 — 우선 개발 대상 (XP 값은 기획 확정 전 임시)
-- ============================================================
INSERT INTO game_service.achievements
(achievement_id, code, name, description, scope, condition_type, condition_value, reward_xp)
VALUES
    (gen_random_uuid(), 'DAILY_GOAL_FIRST',    '첫 걸음',     '일일 목표를 처음으로 달성', 'PERSONAL', 'DAILY_GOAL_COUNT',  1,  50),
    (gen_random_uuid(), 'DAILY_GOAL_STREAK_3', '3일 연속',    '일일 목표를 3일 연속 달성', 'PERSONAL', 'DAILY_GOAL_STREAK', 3, 100),
    (gen_random_uuid(), 'DAILY_GOAL_STREAK_7', '일주일 연속', '일일 목표를 7일 연속 달성', 'PERSONAL', 'DAILY_GOAL_STREAK', 7, 300);
