import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Table-driven checks of {@link Json}, part of {@code selftest}: what parses, what is refused, what values come out. */
final class JsonSelfTest {

    private JsonSelfTest() {}

    static List<String> run() {
        List<String> problems = new ArrayList<>();
        for (String valid : List.of("{}", "[]", "null", "true", "false", "-0", "1e5", "0.5E-3", "\"\"", " [ 1 , 2 ] ",
                "\"\\u00e9\\ud83d\\ude00\"", "{\"a\":[1,2,{\"b\":null}],\"c\":\"}{\"}", "\uFEFF{}",
                "[".repeat(200) + "]".repeat(200))) {
            expectValid(problems, valid, true);
        }
        for (String invalid : List.of("", "{", "{\"a\":1,}", "[1,]", "{\"a\" 1}", "{a:1}", "01", "1.", "-", "1e",
                "\"\\x\"", "\"unterminated", "\"tab\there\"", "tru", "{\"a\":1} x", "[".repeat(300) + "]".repeat(300),
                "'single'", "[1 2]", "\"\\u+123\"", "\"\\u-001\"", "\"\\u12G4\"", "\"\\u 123\"")) {
            expectValid(problems, invalid, false);
        }

        expectEquals(problems, "escapes", "a\"b\\c\nd/", string("{\"k\":\"a\\\"b\\\\c\\nd\\/\"}"));
        expectEquals(problems, "small integer", 123L, Json.parse("123"));
        expectEquals(problems, "fraction", 1.5, Json.parse("1.5"));
        expectEquals(problems, "huge integer", 1.2345678901234567890e19, Json.parse("12345678901234567890"));
        expectEquals(problems, "surrogate pair", "\uD83D\uDE00", Json.parse("\"\\ud83d\\ude00\""));
        expectEquals(problems, "key order", "[z, a, m]", new ArrayList<>(
                Json.asObject(Json.parse("{\"z\":1,\"a\":2,\"m\":3}")).keySet()).toString());
        expectEquals(problems, "last duplicate key wins", 2L, Json.asObject(Json.parse("{\"a\":1,\"a\":2}")).get("a"));
        try {
            Json.parse("{\n  \"a\": ?\n}");
            problems.add("json: an error position case parsed");
        } catch (Json.ParseException e) {
            expectEquals(problems, "error position", "unexpected character '?' at line 2, column 8", e.getMessage());
        }
        return problems.stream().map(p -> "json parser: " + p).toList();
    }

    private static Object string(String json) {
        Map<String, Object> o = Json.asObject(Json.parse(json));
        return o.get("k");
    }

    private static void expectValid(List<String> problems, String text, boolean valid) {
        try {
            Json.parse(text);
            if (!valid) problems.add("accepted invalid input " + abbreviate(text));
        } catch (Json.ParseException e) {
            if (valid) problems.add("refused valid input " + abbreviate(text) + ": " + e.getMessage());
        }
    }

    private static void expectEquals(List<String> problems, String what, Object expected, Object actual) {
        if (!expected.equals(actual)) problems.add(what + ": expected " + expected + " but got " + actual);
    }

    private static String abbreviate(String s) {
        return s.length() > 20 ? "'" + s.substring(0, 20) + "...'" : "'" + s.replace("\n", "\\n") + "'";
    }
}
