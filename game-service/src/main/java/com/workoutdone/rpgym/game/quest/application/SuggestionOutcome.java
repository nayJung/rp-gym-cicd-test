package com.workoutdone.rpgym.game.quest.application;

public enum SuggestionOutcome {
    // CREATED
    CREATED,
    // INFO
    DUPLICATE_SUGGESTION,
    ALREADY_ACTIVE,
    DATE_MISMATCH,
    // ERROR
    UNKNOWN_METRIC,
    INVALID_TARGET,
    SNAPSHOT_MISSING,
    SNAPSHOT_MISMATCH
}
