-- 낙관적 락 (JPA @Version)
ALTER TABLE user_service.users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE user_service.user_health_profiles
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE user_service.user_daily_health_goals
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
