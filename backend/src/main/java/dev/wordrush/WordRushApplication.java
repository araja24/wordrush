package dev.wordrush;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WordRushApplication {

    public static void main(String[] args) {
        SpringApplication.run(WordRushApplication.class, args);
    }
}
