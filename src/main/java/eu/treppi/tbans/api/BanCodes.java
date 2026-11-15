package eu.treppi.tbans.api;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Format helpers for ban codes (ban IDs).
 *
 * <p>New ban codes are {@value #LENGTH} characters long and drawn from a 32 character alphabet
 * ({@value #ALPHABET}) that leaves out the easily confused characters {@code 0 O 1 I}. That gives
 * 32<sup>8</sup> ≈ 1.1 trillion possible codes, generated with a {@link java.security.SecureRandom}.
 *
 * <p>Bans created by older TBans versions keep their legacy code (6 hexadecimal characters, e.g.
 * {@code 3FA09C}); those are still accepted everywhere.
 *
 * <p>Codes are case-insensitive. Use {@link #normalize(String)} on user input before comparing.
 */
public final class BanCodes {

    /** Characters used for new ban codes. */
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** Length of newly generated ban codes. */
    public static final int LENGTH = 8;

    private static final Pattern CURRENT = Pattern.compile("[A-HJ-NP-Z2-9]{" + LENGTH + "}");
    private static final Pattern LEGACY = Pattern.compile("[0-9A-F]{6}");

    private BanCodes() {
    }

    /**
     * Normalizes user input to the canonical code form: surrounding whitespace, inner spaces and
     * dashes are removed and letters are upper-cased. Does not validate the result.
     *
     * @param input raw user input, may be {@code null}
     * @return the normalized code, or {@code null} if the input was {@code null}
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Checks whether the input looks like a ban code (current or legacy format) after
     * {@link #normalize(String) normalization}. Says nothing about whether the code exists.
     *
     * @param input raw user input, may be {@code null}
     * @return {@code true} if the input has a valid ban code format
     */
    public static boolean isValidFormat(String input) {
        String code = normalize(input);
        return code != null && (CURRENT.matcher(code).matches() || LEGACY.matcher(code).matches());
    }

    /**
     * Checks whether the input is a legacy (6 hex characters) ban code after normalization.
     *
     * @param input raw user input, may be {@code null}
     * @return {@code true} if the input has the legacy format
     */
    public static boolean isLegacyFormat(String input) {
        String code = normalize(input);
        return code != null && LEGACY.matcher(code).matches();
    }
}
