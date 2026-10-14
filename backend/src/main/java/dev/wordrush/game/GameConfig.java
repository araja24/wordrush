package dev.wordrush.game;

import dev.wordrush.core.WordList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class GameConfig {

    /** Loaded once at startup (~9k words); immutable, so safe to share across threads. */
    @Bean
    WordList wordList() {
        return WordList.loadDefault();
    }
}
