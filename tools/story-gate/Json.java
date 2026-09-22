import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small but complete JSON parser (RFC 8259). Objects become {@code LinkedHashMap}, arrays {@code ArrayList},
 * numbers {@code Long} (integral, fits) or {@code Double}, plus {@code String}, {@code Boolean} and {@code null}.
 * The JDK has no JSON parser, and the gates must read fields out of plan.json, not only validate it.
 */
final class Json {

    /** Thrown for any text that is not valid JSON; the message carries the line and column. */
    static final class ParseException extends RuntimeException {
        ParseException(String message) {
            super(message);
        }
    }

    private static final int MAX_DEPTH = 256;

    private final String s;
    private int pos;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Json p = new Json(text);
        if (!p.s.isEmpty() && p.s.charAt(0) == 0xFEFF) p.pos = 1; // a BOM is what PowerShell's Out-File writes
        p.skipWhitespace();
        Object value = p.value(0);
        p.skipWhitespace();
        if (p.pos < p.s.length()) throw p.error("unexpected content after the value");
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asObject(Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asArray(Object o) {
        return o instanceof List<?> ? (List<Object>) o : null;
    }

    static String asString(Object o) {
        return o instanceof String str ? str : null;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) throw error("nesting deeper than " + MAX_DEPTH);
        if (pos >= s.length()) throw error("unexpected end of input");
        char c = s.charAt(pos);
        return switch (c) {
            case '{' -> object(depth);
            case '[' -> array(depth);
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) yield number();
                throw error("unexpected character '" + c + "'");
            }
        };
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek('}')) {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (!peek('"')) throw error("expected a string key");
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, value(depth + 1));
            skipWhitespace();
            if (peek(',')) {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek(']')) {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(value(depth + 1));
            skipWhitespace();
            if (peek(',')) {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= s.length()) throw error("unterminated string");
            char c = s.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c < 0x20) throw error("control character in string");
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= s.length()) throw error("unterminated escape");
            char e = s.charAt(pos++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > s.length()) throw error("incomplete \\u escape");
                    int code = 0;
                    for (int i = 0; i < 4; i++) {
                        int digit = hex(s.charAt(pos + i));
                        if (digit < 0) throw error("invalid \\u escape");
                        code = code * 16 + digit;
                    }
                    sb.append((char) code);
                    pos += 4;
                }
                default -> throw error("invalid escape \\" + e);
            }
        }
    }

    private Object number() {
        int start = pos;
        if (peek('-')) pos++;
        if (peek('0')) {
            pos++;
        } else if (digitAhead()) {
            while (digitAhead()) pos++;
        } else {
            throw error("invalid number");
        }
        boolean integral = true;
        if (peek('.')) {
            integral = false;
            pos++;
            if (!digitAhead()) throw error("digits expected after the decimal point");
            while (digitAhead()) pos++;
        }
        if (peek('e') || peek('E')) {
            integral = false;
            pos++;
            if (peek('+') || peek('-')) pos++;
            if (!digitAhead()) throw error("digits expected in the exponent");
            while (digitAhead()) pos++;
        }
        String text = s.substring(start, pos);
        if (integral) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException tooBig) {
                // falls through to a double
            }
        }
        return Double.parseDouble(text);
    }

    private Object literal(String word, Object value) {
        if (!s.startsWith(word, pos)) throw error("unexpected character '" + s.charAt(pos) + "'");
        pos += word.length();
        return value;
    }

    /** The value of an ASCII hex digit, or -1. (Not {@code Character.digit}, which also accepts full-width digits.) */
    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private boolean peek(char c) {
        return pos < s.length() && s.charAt(pos) == c;
    }

    private boolean digitAhead() {
        return pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9';
    }

    private void expect(char c) {
        if (!peek(c)) throw error("expected '" + c + "'");
        pos++;
    }

    private void skipWhitespace() {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
            else break;
        }
    }

    private ParseException error(String message) {
        int line = 1, column = 1;
        for (int i = 0; i < pos && i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new ParseException(message + " at line " + line + ", column " + column);
    }
}
