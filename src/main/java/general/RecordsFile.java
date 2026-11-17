package main.java.general;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and writes the flat numeric thresholds used by Manual records. */
final class RecordsFile {
    private static final String KEY = "[A-Za-z0-9_.-]+";
    private static final String NUMBER = "-?[0-9]+(?:\\.[0-9]+)?";
    private static final Pattern JSON_ENTRY = Pattern.compile(
            "\\s*\"(" + KEY + ")\"\\s*:\\s*(?:\"(" + NUMBER + ")\"|(" + NUMBER + "))\\s*");
    private static final Pattern TEXT_ENTRY = Pattern.compile("\\s*(" + KEY + ")\\s*=\\s*(" + NUMBER + ")\\s*");

    private RecordsFile() { }

    static Map<String, String> parse(String input) {
        String text = input.strip();
        if (text.startsWith("\ufeff")) text = text.substring(1).strip();
        Map<String, String> records = new LinkedHashMap<>();
        if (text.startsWith("{") && text.endsWith("}")) {
            String body = text.substring(1, text.length() - 1).strip();
            if (body.isEmpty()) return records;
            for (String entry : body.split(",", -1)) {
                Matcher match = JSON_ENTRY.matcher(entry);
                if (!match.matches()) throw new IllegalArgumentException("Expected a flat JSON object of numeric records");
                add(records, match.group(1), match.group(2) == null ? match.group(3) : match.group(2));
            }
        } else {
            for (String line : text.split("\\R")) {
                if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
                Matcher match = TEXT_ENTRY.matcher(line);
                if (!match.matches()) throw new IllegalArgumentException("Expected one numeric name=value record per line");
                add(records, match.group(1), match.group(2));
            }
        }
        return records;
    }

    private static void add(Map<String, String> records, String key, String value) {
        if (!Double.isFinite(Double.parseDouble(value))) throw new IllegalArgumentException("Invalid value for " + key);
        if (records.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate record " + key);
    }

    static String write(Map<String, String> records) {
        BootyDivision.Json json = new BootyDivision.Json().open();
        records.forEach((key, value) -> json.field(key, new BigDecimal(value)));
        return json.close() + "\n";
    }
}
