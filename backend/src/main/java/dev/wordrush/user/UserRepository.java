package dev.wordrush.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByUsername(String username);

    List<User> findByBotTrueAndCreatedAtBefore(Instant createdBefore);
}
