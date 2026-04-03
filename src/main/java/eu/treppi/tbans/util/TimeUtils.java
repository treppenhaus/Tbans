package eu.treppi.tbans.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([a-zA-Z]+)");

    public static long parseTime(String input) {
        Matcher matcher = TIME_PATTERN.matcher(input.toLowerCase());
        if (!matcher.find()) {
            return -1;
        }

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
        String unit = matcher.group(2);

        switch (unit) {
            case "s":
            case "sec":
            case "second":
            case "seconds":
                return amount * 1000L;
            case "min":
            case "minute":
            case "minutes":
                return amount * 60 * 1000L;
            case "h":
            case "hour":
            case "hours":
            case "ho":
                return amount * 60 * 60 * 1000L;
            case "d":
            case "day":
            case "days":
                return amount * 24 * 60 * 60 * 1000L;
            case "w":
            case "week":
            case "weeks":
                return amount * 7 * 24 * 60 * 60 * 1000L;
            case "m":
            case "month":
            case "months":
                return amount * 30 * 24 * 60 * 60 * 1000L;
            default:
                return -1;
        }
    }

    public static String formatRemainingTime(long millis) {
        if (millis < 0) {
            return "Permanent";
        }

        long seconds = millis / 1000;
        if (seconds == 0) return "Less than a second";

        long days = seconds / (24 * 3600);
        seconds = seconds % (24 * 3600);
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s");

        String result = sb.toString().trim();
        return result.isEmpty() ? "Less than a second" : result;
    }
}
