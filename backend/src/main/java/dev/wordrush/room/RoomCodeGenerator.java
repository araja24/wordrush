package dev.wordrush.room;

import java.security.SecureRandom;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Six characters from an alphabet without look-alikes (no 0/O, 1/I/L), so codes are easy to
 * read out loud. 31^6 ~ 887 million codes; SecureRandom makes them unguessable.
 */
@Component
public class RoomCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 6;
    private static final Pattern VALID = Pattern.compile("[" + ALPHABET + "]{" + LENGTH + "}");

    private final SecureRandom random = new SecureRandom();

    public String next() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }

    public static boolean isValid(String code) {
        return code != null && VALID.matcher(code).matches();
    }
}
