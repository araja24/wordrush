package dev.wordrush.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A player account. Guests and bots get a unique username of the form {@code nickname#1234}
 * (Discord-style), so two guests can both be "alex". Never returned from controllers; map to
 * a DTO instead.
 */
@Entity
@Table(name = "users")
public class User {

    static final char DISCRIMINATOR_SEPARATOR = '#';

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "is_guest", nullable = false)
    private boolean guest;

    @Column(name = "is_bot", nullable = false)
    private boolean bot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // for JPA
    }

    private User(String username, boolean bot) {
        this.username = username;
        this.guest = true;
        this.bot = bot;
    }

    public static User guest(String nickname, int discriminator) {
        return new User(tagged(nickname, discriminator), false);
    }

    public static User bot(String nickname, int discriminator) {
        return new User(tagged(nickname, discriminator), true);
    }

    private static String tagged(String nickname, int discriminator) {
        return nickname + DISCRIMINATOR_SEPARATOR + String.format("%04d", discriminator);
    }

    /** Display name: the username without its {@code #1234} suffix. */
    public String nickname() {
        int separator = username.lastIndexOf(DISCRIMINATOR_SEPARATOR);
        return separator < 0 ? username : username.substring(0, separator);
    }

    public UUID id() {
        return id;
    }

    public String username() {
        return username;
    }

    public boolean isGuest() {
        return guest;
    }

    public boolean isBot() {
        return bot;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
