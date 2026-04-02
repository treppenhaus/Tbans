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

        long amount = Long.parseLong(matcher.group(1));
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
}
