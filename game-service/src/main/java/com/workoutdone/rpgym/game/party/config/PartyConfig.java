package com.workoutdone.rpgym.game.party.config;

import com.workoutdone.rpgym.game.party.application.PartyProperties;
import com.workoutdone.rpgym.game.party.outbox.config.PartyOutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({PartyProperties.class, PartyOutboxProperties.class})
public class PartyConfig {
}
