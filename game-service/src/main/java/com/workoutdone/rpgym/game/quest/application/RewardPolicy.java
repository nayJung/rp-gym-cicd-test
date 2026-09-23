package com.workoutdone.rpgym.game.quest.application;

import org.springframework.stereotype.Component;

@Component
public class RewardPolicy {

    private static final int QUEST_REWARD_XP = 5;

    private static final int PARTY_QUEST_REWARD_XP = 30;

    public int questRewardXp() {
        return QUEST_REWARD_XP;
    }

    public int partyQuestRewardXp() {
        return PARTY_QUEST_REWARD_XP;
    }
}
