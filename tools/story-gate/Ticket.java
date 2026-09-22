import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parts of a ticket the gates need: frontmatter (status, touches), the numbered acceptance criteria, and the
 * scenarios with their {@code @AC-n} tags.
 */
record Ticket(String id, String status, List<String> touches, List<Criterion> criteria, List<Scenario> scenarios) {

    /** A live or retired acceptance criterion. */
    record Criterion(int number, boolean retired) {}

    /** A Gherkin scenario and the criterion numbers named by the tags directly above it. */
    record Scenario(String title, Set<Integer> tags) {}

    private static final Pattern KEY = Pattern.compile("^([A-Za-z_][\\w-]*):\\s*(.*)$");
    private static final Pattern CRITERION = Pattern.compile("^-\\s+\\*\\*AC-(\\d+)(\\s+\\(retired\\))?\\*\\*.*$");
    private static final Pattern SCENARIO = Pattern.compile("^Scenario(?: Outline)?:\\s*(.*)$");
    private static final Pattern TAG = Pattern.compile("^@AC-(\\d+)$");

    static Ticket parse(String id, String text) {
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        Map<String, Object> front = frontmatter(lines);

        List<Criterion> criteria = new ArrayList<>();
        List<Scenario> scenarios = new ArrayList<>();
        String section = "";
        Set<Integer> pending = new LinkedHashSet<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("## ")) {
                section = line.substring(3).strip().toLowerCase();
                pending.clear();
                continue;
            }
            if (section.equals("acceptance criteria")) {
                Matcher m = CRITERION.matcher(line);
                if (m.matches()) criteria.add(new Criterion(Integer.parseInt(m.group(1)), m.group(2) != null));
            } else if (section.equals("scenarios")) {
                scenarioLine(line, pending, scenarios);
            }
        }
        return new Ticket(id, string(front.get("status")), list(front.get("touches")), criteria, scenarios);
    }

    /** Tags accumulate until a Scenario line claims them; any other line (a step, a Feature) drops them. */
    private static void scenarioLine(String line, Set<Integer> pending, List<Scenario> scenarios) {
        if (line.startsWith("@")) {
            for (String token : line.split("\\s+")) {
                Matcher t = TAG.matcher(token);
                if (t.matches()) pending.add(Integer.parseInt(t.group(1)));
            }
            return;
        }
        Matcher s = SCENARIO.matcher(line);
        if (s.matches()) {
            scenarios.add(new Scenario(s.group(1).strip(), new LinkedHashSet<>(pending)));
            pending.clear();
        } else if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("```")) {
            pending.clear();
        }
    }

    Criterion criterion(int number) {
        return criteria.stream().filter(c -> c.number() == number).findFirst().orElse(null);
    }

    List<Criterion> liveCriteria() {
        return criteria.stream().filter(c -> !c.retired()).toList();
    }

    /** Values are a String, or a List of Strings for {@code [a, b]} and for a block list of {@code - item} lines. */
    private static Map<String, Object> frontmatter(String[] lines) {
        Map<String, Object> front = new LinkedHashMap<>();
        if (lines.length == 0 || !lines[0].strip().equals("---")) return front;
        String openKey = null;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.strip().equals("---")) break;
            if (openKey != null && line.strip().startsWith("- ")) {
                @SuppressWarnings("unchecked")
                List<String> items = (List<String>) front.get(openKey);
                items.add(unquote(line.strip().substring(2)));
                continue;
            }
            Matcher m = KEY.matcher(line);
            if (!m.matches()) continue;
            String key = m.group(1), value = m.group(2).strip();
            openKey = null;
            if (value.isEmpty()) {
                front.put(key, new ArrayList<String>());
                openKey = key;
            } else if (value.startsWith("[") && value.endsWith("]")) {
                List<String> items = new ArrayList<>();
                for (String item : value.substring(1, value.length() - 1).split(",")) {
                    if (!item.isBlank()) items.add(unquote(item.strip()));
                }
                front.put(key, items);
            } else {
                front.put(key, unquote(value));
            }
        }
        return front;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String string(Object o) {
        return o instanceof String s ? s : "";
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object o) {
        return o instanceof List<?> l ? (List<String>) l : List.of();
    }
}
