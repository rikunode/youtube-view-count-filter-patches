package app.rikunode.morphe.extension.youtube.diagnostic.filters;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Independent localized view-count parser. Inspired by the configurable multiplier
 * approach in Anddea's FeedVideoViewsFilter, but not dependent on its extension API.
 */
public final class ViewCountParser {
    private static final Pattern JAPANESE = Pattern.compile(
        "(?<![\\p{L}\\p{N}])([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(万|億)?\\s*回\\s*(?:視聴|再生)");
    private static final Pattern ENGLISH = Pattern.compile(
        "(?i)(?<![\\p{L}\\p{N}])([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KMB])?\\s*views?\\b");

    private ViewCountParser() {}

    /**
     * Returns -1 for unrecognized, overflowed, or ambiguous view counts.
     * Repeated matches of the same number are permitted.
     */
    public static long parse(String raw) {
        if (raw == null || raw.isEmpty()) return -1L;
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace('\u00a0', ' ').replace('\u202f', ' ');
        long result = -1L;
        for (Pattern pattern : new Pattern[]{JAPANESE, ENGLISH}) {
            Matcher matcher = pattern.matcher(value);
            while (matcher.find()) {
                long count = parseOne(matcher.group(1), matcher.group(2));
                if (count < 0 || (result >= 0 && result != count)) return -1L;
                result = count;
            }
        }
        return result;
    }

    private static long parseOne(String digits, String unit) {
        try {
            BigDecimal number = new BigDecimal(digits.replace(",", ""));
            long factor = 1L;
            if (unit != null) {
                switch (unit.toUpperCase(java.util.Locale.ROOT)) {
                    case "万": factor = 10_000L; break;
                    case "億": factor = 100_000_000L; break;
                    case "K": factor = 1_000L; break;
                    case "M": factor = 1_000_000L; break;
                    case "B": factor = 1_000_000_000L; break;
                    default: return -1L;
                }
            }
            long count = number.multiply(BigDecimal.valueOf(factor)).longValueExact();
            return count < 0 ? -1L : count;
        } catch (RuntimeException e) {
            return -1L;
        }
    }
}
