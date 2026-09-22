package com.workoutdone.rpgym.game.ranking.application;

import java.util.List;
import java.util.UUID;

public record PartyRankingView(
        String week,
        List<Entry> content,
        MyParty myParty,    // 소속 없거나 집계 전이면 null
        int page,
        int size,
        long totalElements
) {
    public record Entry(long rank, UUID partyId, String partyName, int memberCount, long weeklyXp){}
    public record MyParty(long rank, UUID partyId, long weeklyXp){}

    public int totalPages(){
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public boolean last(){
        return (long) (page + 1) * size >= totalElements;
    }
}
