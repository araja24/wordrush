package dev.wordrush;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** Stage 0 "done" check: app boots, Flyway migrates, and health reports db + redis UP. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIT {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void readinessIsUpWithDbAndRedis() {
        String body = RestClient.create("http://localhost:" + port)
                .get().uri("/actuator/health/readiness")
                .retrieve().body(String.class);

        assertThat(body).contains("\"status\":\"UP\"").contains("\"db\"").contains("\"redis\"");
    }

    @Test
    void flywayCreatedTheSchema() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users', 'games', 'game_players', 'rounds', 'guesses')
                """, Integer.class);

        assertThat(tables).isEqualTo(5);
    }
}
