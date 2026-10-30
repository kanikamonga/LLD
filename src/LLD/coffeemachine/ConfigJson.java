package LLD.coffeemachine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Small strict reader for this configuration schema, not a general-purpose JSON library. */
public final class ConfigJson {
    private ConfigJson() { }

    public static MachineConfig parse(String json) {
        Reader reader = new Reader(Objects.requireNonNull(json, "json"));
        Map<String, Object> root = object(reader.value(0), "root");
        reader.space();
        if (reader.index != json.length()) throw reader.error("unexpected trailing content");
        exactKeys(root, "root", "machine");
        Map<String, Object> machine = object(root.get("machine"), "machine");
        exactKeys(machine, "machine", "outlets", "total_items_quantity", "beverages");
        Map<String, Object> outlets = object(machine.get("outlets"), "outlets");
        exactKeys(outlets, "outlets", "count_n");
        long count = number(outlets.get("count_n"), "count_n");
        if (count == 0 || count > Integer.MAX_VALUE)
            throw new IllegalArgumentException("count_n must be between 1 and Integer.MAX_VALUE");
        Map<String, Long> inventory = quantities(machine.get("total_items_quantity"), "total_items_quantity");
        Map<String, Map<String, Long>> recipes = new LinkedHashMap<>();
        object(machine.get("beverages"), "beverages").forEach((name, value) ->
                recipes.put(name, quantities(value, "recipe " + name)));
        return new MachineConfig((int) count, inventory, recipes);
    }

    private static void exactKeys(Map<String, Object> map, String path, String... names) {
        for (String name : names)
            if (!map.containsKey(name)) throw new IllegalArgumentException(path + " missing field " + name);
        if (map.size() != names.length)
            throw new IllegalArgumentException(path + " contains unsupported fields");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String path) {
        if (!(value instanceof Map)) throw new IllegalArgumentException(path + " must be an object");
        return (Map<String, Object>) value;
    }

    private static long number(Object value, String path) {
        if (!(value instanceof Long))
            throw new IllegalArgumentException(path + " must be a nonnegative integer");
        return (Long) value;
    }

    private static Map<String, Long> quantities(Object value, String path) {
        Map<String, Long> result = new LinkedHashMap<>();
        object(value, path).forEach((name, amount) -> result.put(name, number(amount, path + "." + name)));
        return result;
    }

    private static final class Reader {
        private final String text;
        private int index;
        private Reader(String text) { this.text = text; }
        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid configuration JSON at offset " + index + ": " + message);
        }
        private void space() {
            while (index < text.length() && " \t\r\n".indexOf(text.charAt(index)) >= 0) index++;
        }
        private boolean take(char c) {
            space();
            if (index < text.length() && text.charAt(index) == c) { index++; return true; }
            return false;
        }
        private void expect(char c) {
            if (!take(c)) throw error("expected '" + c + "'");
        }
        private Object value(int depth) {
            space();
            if (depth > 8) throw error("object nesting exceeds 8 levels");
            if (index == text.length()) throw error("expected a value");
            char c = text.charAt(index);
            if (c == '{') {
                index++;
                Map<String, Object> map = new LinkedHashMap<>();
                if (take('}')) return map;
                do {
                    space();
                    String key = string();
                    if (map.containsKey(key)) throw error("duplicate key '" + key + "'");
                    expect(':');
                    map.put(key, value(depth + 1));
                    if (take('}')) return map;
                    expect(',');
                    space();
                    if (index < text.length() && text.charAt(index) == '}')
                        throw error("trailing commas are not supported");
                } while (true);
            }
            if (c == '"') return string();
            if (c >= '0' && c <= '9') {
                int start = index++;
                if (c == '0' && index < text.length() && Character.isDigit(text.charAt(index)))
                    throw error("leading zeros are not allowed");
                while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') index++;
                if (index < text.length() && ".eE+-".indexOf(text.charAt(index)) >= 0)
                    throw error("only nonnegative integers are supported");
                try { return Long.parseLong(text.substring(start, index)); }
                catch (NumberFormatException overflow) { throw error("integer exceeds Long.MAX_VALUE"); }
            }
            throw error("only objects, strings and nonnegative integers are supported");
        }
        private String string() {
            if (index >= text.length() || text.charAt(index++) != '"')
                throw error("expected a quoted string key");
            StringBuilder result = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') return result.toString();
                if (c < 0x20) throw error("unescaped control character");
                if (c == '\\') {
                    if (index == text.length()) throw error("unfinished escape");
                    c = text.charAt(index++);
                    switch (c) {
                        case '"': case '\\': case '/': result.append(c); break;
                        case 'b': result.append('\b'); break;
                        case 'f': result.append('\f'); break;
                        case 'n': result.append('\n'); break;
                        case 'r': result.append('\r'); break;
                        case 't': result.append('\t'); break;
                        case 'u':
                            if (index + 4 > text.length()) throw error("incomplete unicode escape");
                            int code = 0;
                            for (int i = 0; i < 4; i++) {
                                char hex = text.charAt(index++);
                                int digit = "0123456789abcdef".indexOf(Character.toLowerCase(hex));
                                if (digit < 0) throw error("invalid unicode escape");
                                code = code * 16 + digit;
                            }
                            result.append((char) code);
                            break;
                        default: throw error("invalid string escape");
                    }
                } else result.append(c);
            }
            throw error("unterminated string");
        }
    }
}
