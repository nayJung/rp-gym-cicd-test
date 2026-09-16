CREATE INDEX idx_daily_health_summaries_unresolved
    ON health_service.daily_health_summaries (activity_date)
    WHERE achieved_at IS NULL AND failed_at IS NULL;