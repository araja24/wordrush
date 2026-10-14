package dev.wordrush.core;

import static dev.wordrush.core.GameRules.WORD_LENGTH;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Immutable dictionary. {@code answers} are the words a round can pick (common words, no plurals);
 * {@code allowed} is every word accepted as a guess and always includes all answers.
 * Words are stored uppercase; callers pass user input through {@link #normalize} first.
 * Sources and licensing: game-core/WORDLISTS.md.
 */
public final class WordList {

    private static final Pattern WORD = Pattern.compile("[A-Z]{" + WORD_LENGTH + "}");
    private static final String RESOURCE_DIR = "/dev/wordrush/core/words/";

    private final List<String> answers;
    private final Set<String> allowed;

    public WordList(Collection<String> answers, Collection<String> allowedGuesses) {
        if (answers.isEmpty()) {
            throw new IllegalArgumentException("At least one answer is required");
        }
        for (String word : answers) {
            requireWord(word);
        }
        for (String word : allowedGuesses) {
            requireWord(word);
        }
        this.answers = answers.stream().distinct().sorted().toList();
        Set<String> all = new HashSet<>(allowedGuesses);
        all.addAll(this.answers);
        this.allowed = Set.copyOf(all);
    }

    /** Loads the bundled lists from the classpath. */
    public static WordList loadDefault() {
        return new WordList(readResource("answers.txt"), readResource("allowed.txt"));
    }

    /** Trims and uppercases raw user input. Does not validate; use {@link #isAllowed}. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
    }

    /** True if the (normalized) word is an accepted guess. */
    public boolean isAllowed(String word) {
        return allowed.contains(word);
    }

    public List<String> answers() {
        return answers;
    }

    public int allowedCount() {
        return allowed.size();
    }

    /**
     * Picks a random answer not in {@code exclude} (answers already used this game).
     * If every answer is excluded, repeats are allowed rather than failing.
     */
    public String randomAnswer(RandomGenerator rng, Set<String> exclude) {
        if (exclude.containsAll(answers)) {
            return answers.get(rng.nextInt(answers.size()));
        }
        while (true) {
            String candidate = answers.get(rng.nextInt(answers.size()));
            if (!exclude.contains(candidate)) {
                return candidate;
            }
        }
    }

    private static void requireWord(String word) {
        if (word == null || !WORD.matcher(word).matches()) {
            throw new IllegalArgumentException("Not a " + WORD_LENGTH + "-letter uppercase word: " + word);
        }
    }

    private static List<String> readResource(String name) {
        InputStream in = WordList.class.getResourceAsStream(RESOURCE_DIR + name);
        if (in == null) {
            throw new IllegalStateException("Missing word list resource: " + RESOURCE_DIR + name);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
