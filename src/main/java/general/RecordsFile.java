package main.java.general;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads and writes the shared records file: a flat JSON-like map of record name to number, e.g.
 *
 *   {
 *     "ccforage7": 31,
 *     "lairics12": 40,
 *     "singledr7": 12.5
 *   }
 *
 * Reading is forgiving so a hand-edited file still works: braces and quotes are optional, entries
 * may be split by commas or new lines, "name = value" works as well as "name: value", and lines
 * starting with # or // are ignored.
 */
public final class RecordsFile {

    private RecordsFile() {
    }

    /** Name -> value text, in file order. Names are lower-cased. Throws if a line can't be read. */
    public static Map<String, String> parse(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        int lineNo = 0;
        for (String line : text.split("\r?\n")) {
            lineNo++;
            String l = line.trim();
            if (l.startsWith("#") || l.startsWith("//")) continue;
            for (String entry : l.split(",")) {
                String e = entry.replace("{", "").replace("}", "").trim();
                if (e.isEmpty()) continue;
                int sep = indexOfSeparator(e);
                if (sep < 0) throw new IllegalArgumentException("Line " + lineNo + ": expected name: value, got \"" + e + "\"");
                String name = unquote(e.substring(0, sep)).toLowerCase();
                String value = unquote(e.substring(sep + 1)).replace("_", "");
                if (name.isEmpty()) throw new IllegalArgumentException("Line " + lineNo + ": missing name");
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Line " + lineNo + ": \"" + value + "\" isn't a number");
                }
                out.put(name, value);
            }
        }
        return out;
    }

    /** The map as a JSON object, one record per line. */
    public static String write(Map<String, String> records) {
        StringBuilder sb = new StringBuilder("{").append(System.lineSeparator());
        int i = 0;
        for (Map.Entry<String, String> e : records.entrySet()) {
            sb.append("  \"").append(e.getKey()).append("\": ").append(e.getValue());
            if (++i < records.size()) sb.append(',');
            sb.append(System.lineSeparator());
        }
        return sb.append('}').append(System.lineSeparator()).toString();
    }

    private static int indexOfSeparator(String s) {
        int c = s.indexOf(':'), q = s.indexOf('=');
        if (c < 0) return q;
        if (q < 0) return c;
        return Math.min(c, q);
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'")))
            s = s.substring(1, s.length() - 1).trim();
        return s;
    }
}
