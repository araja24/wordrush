package dev.wordrush.auth;

import dev.wordrush.user.User;
import dev.wordrush.user.UserRepository;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_USERNAME_ATTEMPTS = 10;

    private final UserRepository users;
    private final TokenService tokens;

    public AuthService(UserRepository users, TokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    /** {@code bot} only labels the account (a badge in the UI); it grants nothing. */
    public AuthResponse loginAsGuest(String nickname, boolean bot) {
        User user = createWithFreeUsername(nickname, bot);
        log.info("Guest created userId={} username={} bot={}", user.id(), user.username(), bot);
        return AuthResponse.of(tokens.issue(user), user);
    }

    /**
     * Picks a random {@code #0000-#9999} suffix. The pre-check makes collisions rare; the unique
     * constraint is the real guarantee if two requests race for the same name.
     */
    /** An account for an in-app bot; bots never sign in, they act through the services directly. */
    public User registerBot(String nickname) {
        return createWithFreeUsername(nickname, true);
    }

    private User createWithFreeUsername(String nickname, boolean bot) {
        for (int attempt = 0; attempt < MAX_USERNAME_ATTEMPTS; attempt++) {
            int discriminator = ThreadLocalRandom.current().nextInt(10_000);
            User candidate = bot ? User.bot(nickname, discriminator) : User.guest(nickname, discriminator);
            if (users.existsByUsername(candidate.username())) {
                continue;
            }
            try {
                return users.saveAndFlush(candidate);
            } catch (DataIntegrityViolationException raceLost) {
                log.debug("Username {} taken concurrently, retrying", candidate.username());
            }
        }
        throw new IllegalStateException("Could not find a free username for " + nickname);
    }
}
