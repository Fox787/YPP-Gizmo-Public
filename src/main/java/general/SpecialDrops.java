package main.java.general;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

final class SpecialDrops {

	static final String TITLE = "Booty Alerts";
	private static final String KEY = "specialDrops";
	static final List<Rule> DEFAULTS = List.of("Ippolito", "Serpent", "Lobster", "Krakling", "Ghostling", "Bat",
			"Squirrel", "Karkinos", "Gator", "Ghost rat").stream()
			.map(item -> new Rule(item, Scope.SHIPWIDE)).toList();

	enum Scope {
		SHIPWIDE("Shipwide"), OWN("Own wins only");

		private final String label;

		Scope(String label) { this.label = label; }

		@Override public String toString() { return label; }
	}

	record Rule(String item, Scope scope) { }

	private SpecialDrops() { }

	static List<Rule> load(Preferences prefs) {
		String stored = prefs.get(KEY, null);
		if (stored == null) return DEFAULTS;

		List<Rule> rules = new ArrayList<>();
		for (String line : stored.split("\n")) {
			int tab = line.indexOf('\t');
			if (tab < 0 || tab == line.length() - 1) continue;
			String item = normalizeName(line.substring(tab + 1));
			if (item.isEmpty()) continue;
			try {
				rules.add(new Rule(item, Scope.valueOf(line.substring(0, tab))));
			} catch (IllegalArgumentException ignored) {
				// A saved scope from a different version need not disable the other rules.
			}
		}
		return rules;
	}

	static void save(Preferences prefs, List<Rule> rules) {
		StringBuilder stored = new StringBuilder();
		for (Rule rule : rules) stored.append(rule.scope().name()).append('\t').append(rule.item()).append('\n');
		prefs.put(KEY, stored.toString());
	}

	static String normalizeName(String item) {
		return item.strip().replaceAll("\\s+", " ");
	}

	/** The longest matching rule takes precedence when several item names overlap. */
	static Scope scopeOf(List<Rule> rules, String tooltip) {
		String name = tooltip.toLowerCase(Locale.ROOT);
		Rule bestMatch = null;
		for (Rule rule : rules) {
			if ((bestMatch == null || rule.item().length() > bestMatch.item().length())
					&& name.contains(rule.item().toLowerCase(Locale.ROOT))) {
				bestMatch = rule;
			}
		}
		return bestMatch == null ? null : bestMatch.scope();
	}
}
