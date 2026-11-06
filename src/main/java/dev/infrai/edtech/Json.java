package dev.infrai.edtech;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.space();
        if (!parser.done()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }

    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(stringify(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> items) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (Object item : items) {
                if (!first) out.append(',');
                first = false;
                out.append(stringify(item));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 32 ? String.format("\\u%04x", (int) c) : c);
            }
        }
        return out.append('"').toString();
    }

    private static final class Parser {
        private final String text;
        private int at;

        private Parser(String text) { this.text = text; }
        private boolean done() { return at == text.length(); }
        private void space() { while (!done() && Character.isWhitespace(text.charAt(at))) at++; }

        private Object value() {
            space();
            if (done()) throw new IllegalArgumentException("Expected JSON value");
            return switch (text.charAt(at)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            at++;
            Map<String, Object> map = new LinkedHashMap<>();
            space();
            if (take('}')) return map;
            do {
                space();
                String key = string();
                space();
                expect(':');
                map.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return map;
        }

        private List<Object> array() {
            at++;
            List<Object> list = new ArrayList<>();
            space();
            if (take(']')) return list;
            do { list.add(value()); space(); } while (take(','));
            expect(']');
            return list;
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!done()) {
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> { out.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); at += 4; }
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object number() {
            int start = at;
            while (!done() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            String token = text.substring(start, at);
            return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token);
        }

        private Object literal(String token, Object value) {
            if (!text.startsWith(token, at)) throw new IllegalArgumentException("Invalid JSON literal");
            at += token.length();
            return value;
        }

        private boolean take(char expected) {
            if (!done() && text.charAt(at) == expected) { at++; return true; }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected '" + expected + "'");
        }
    }
}
