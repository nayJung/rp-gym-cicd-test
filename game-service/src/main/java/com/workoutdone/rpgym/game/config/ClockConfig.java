package com.workoutdone.rpgym.game.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
//"지금" 을 주입가능하게 만듬.
//파티는 모집 마감/만료/종료가 전부 시각비교라서 Instant.now()를 코드에 박으면
//24시간 뒤를 테스트할 방법이 없다. 테스트에서는 Clock.fixed()를 넣는다.
    @Bean
    public Clock clock(){
        return Clock.systemUTC();
    }
}
