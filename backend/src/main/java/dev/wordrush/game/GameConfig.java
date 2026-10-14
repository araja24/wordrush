package dev.wordrush.game;

import dev.wordrush.core.FeedbackTable;
import dev.wordrush.core.WordList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class GameConfig {

    /** Loaded once at startup (~9k words); immutable, so safe to share across threads. */
    @Bean
    WordList wordList() {
        return WordList.loadDefault();
    }

    /** ~5 MB of precomputed feedback for the bots' solver; built on first use, shared by every bot. */
    @Bean
    @Lazy
    FeedbackTable feedbackTable(WordList wordList) {
        return FeedbackTable.of(wordList.answers());
    }
}
