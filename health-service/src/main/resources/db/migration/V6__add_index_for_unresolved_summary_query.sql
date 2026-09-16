CREATE INDEX idx_daily_health_summaries_unresolved
    ON health_service.daily_health_summaries (activity_date, achieved_at, failed_at);