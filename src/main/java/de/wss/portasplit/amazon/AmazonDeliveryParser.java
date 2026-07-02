package de.wss.portasplit.amazon;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the earliest delivery date from Amazon buy-box delivery strings, in German or English and
 * in either order, e.g. "GRATIS Lieferung Donnerstag, 26. Juni", "Lieferung morgen",
 * "FREE delivery Thursday, June 26", or a range "25. Juni - 27. Juni" (earliest wins). Returns
 * {@code null} when no concrete near-term date is present (e.g. "versandfertig in 1 bis 2 Monaten"),
 * which the caller treats as "not shippable soon".
 */
public final class AmazonDeliveryParser {

    private static final String MONTH = "(jan|feb|m\\u00e4r|maer|mar|apr|may|mai|jun|jul|aug|sep|sept|oct|okt|nov|dec|dez)[a-z\\u00e4]*\\.?";
    // "26. Juni" / "26 June"
    private static final Pattern DAY_MONTH = Pattern.compile(
            "(\\d{1,2})\\.?\\s*" + MONTH, Pattern.CASE_INSENSITIVE);
    // "June 26" / "Juni 26"
    private static final Pattern MONTH_DAY = Pattern.compile(
            MONTH + "\\s+(\\d{1,2})", Pattern.CASE_INSENSITIVE);

    private AmazonDeliveryParser() {
    }

    public static LocalDate parseEarliest(String text, LocalDate today) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.GERMAN);
        LocalDate earliest = null;

        // Relative words. Order matters: "übermorgen"/"day after tomorrow" contain "morgen"/"tomorrow".
        if (lower.contains("heute") || lower.contains("today")) {
            earliest = min(earliest, today);
        }
        if (lower.contains("übermorgen") || lower.contains("uebermorgen") || lower.contains("day after tomorrow")) {
            earliest = min(earliest, today.plusDays(2));
        } else if (lower.contains("morgen") || lower.contains("tomorrow")) {
            earliest = min(earliest, today.plusDays(1));
        }

        earliest = scan(DAY_MONTH, lower, today, earliest, 1, 2);
        earliest = scan(MONTH_DAY, lower, today, earliest, 2, 1);
        return earliest;
    }

    private static LocalDate scan(Pattern p, String text, LocalDate today, LocalDate earliest,
                                  int dayGroup, int monthGroup) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            Integer month = monthFromToken(m.group(monthGroup));
            if (month == null) {
                continue;
            }
            int day = Integer.parseInt(m.group(dayGroup));
            LocalDate candidate = resolve(day, month, today);
            if (candidate != null) {
                earliest = min(earliest, candidate);
            }
        }
        return earliest;
    }

    /** Build the next future date for a day/month, rolling into next year if it already passed. */
    private static LocalDate resolve(int day, int month, LocalDate today) {
        for (int year = today.getYear(); year <= today.getYear() + 1; year++) {
            try {
                LocalDate d = LocalDate.of(year, month, day);
                if (!d.isBefore(today)) {
                    return d;
                }
            } catch (DateTimeException ignored) {
                return null; // invalid day for month
            }
        }
        return null;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }

    private static Integer monthFromToken(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.toLowerCase(Locale.GERMAN).replace(".", "");
        if (t.startsWith("jan")) return 1;
        if (t.startsWith("feb")) return 2;
        if (t.startsWith("mär") || t.startsWith("maer") || t.startsWith("mar")) return 3;
        if (t.startsWith("apr")) return 4;
        if (t.startsWith("may") || t.startsWith("mai")) return 5;
        if (t.startsWith("jun")) return 6;
        if (t.startsWith("jul")) return 7;
        if (t.startsWith("aug")) return 8;
        if (t.startsWith("sep")) return 9;
        if (t.startsWith("oct") || t.startsWith("okt")) return 10;
        if (t.startsWith("nov")) return 11;
        if (t.startsWith("dec") || t.startsWith("dez")) return 12;
        return null;
    }
}
