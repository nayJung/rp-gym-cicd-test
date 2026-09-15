package com.workoutdone.rpgym.game.party.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;

/** 파티 랭킹의 집계 단위 = ISO 주 (월 9:00 ~ 다음 월 00:00, kst)
 *
 * @param key Redis 키 접미사이자 API의 week 파라미터임. 예: 2026-w38
 * @param start 포함
 * @param end 미포함
 */
public record PartyWeek(String key, Instant start, Instant end) {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public static PartyWeek of(Instant at){
        LocalDate date = at.atZone(ZONE).toLocalDate();
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));


        return fromMonday(monday);
    }

    /** "2026-W38" 형식을 파싱한다. 형식이 틀리면 IllegalArgumentException. */
    public static PartyWeek parse(String key){
        if (key == null || !key.matches("\\d{4}-W\\d{2}")){
            throw new IllegalArgumentException("week 형식은 yyyy-Www 여야 합니다: " + key);
        }
        int year = Integer.parseInt(key.substring(0, 4));
        int week = Integer.parseInt(key.substring(6));
        LocalDate monday = LocalDate.of(year, 1, 4) // ISO: 1월 4일이 속한 주가 1주차
                .with(WeekFields.ISO.weekBasedYear(), week)
                .with(DayOfWeek.MONDAY);

        return fromMonday(monday);
    }

    private static PartyWeek fromMonday(LocalDate monday){

        Instant start = monday.atStartOfDay(ZONE).toInstant();
        Instant end = monday.plusWeeks(1).atStartOfDay(ZONE).toInstant();
        // DateTimeFormatter 의 'Y'/'w' 는 JVM 로케일의 주 정의를 따른다 (미국 로케일이면 일요일 시작).
        // 서버마다 키가 달라지면 안 되므로 WeekFields.ISO 로 직접 뽑는다.
        String key = String.format("%d-W%02d",
                monday.get(WeekFields.ISO.weekBasedYear()),
                monday.get(WeekFields.ISO.weekOfWeekBasedYear()));

        return new PartyWeek(key, start, end);
    }

    public boolean contains(Instant at){
        return !at.isBefore(start) && at.isBefore(end);
    }

}
