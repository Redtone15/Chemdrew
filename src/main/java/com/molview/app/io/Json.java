package com.molview.app.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny dependency-free JSON reader/writer. Supports the subset needed by the
 * native project format and keeps the app easy to build offline.
 */
public final class Json {
    private Json() {
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder(4096);
        writeValue(value, out, 0, true);
        return out.toString();
    }

    private static void writeValue(Object value, StringBuilder out, int indent, boolean pretty) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            writeString(string, out);
        } else if (value instanceof Number number) {
            out.append(number);
        } else if (value instanceof Boolean bool) {
            out.append(bool);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(map, out, indent, pretty);
        } else if (value instanceof Iterable<?> list) {
            writeArray(list, out, indent, pretty);
        } else if (value instanceof Enum<?> enumeration) {
            writeString(enumeration.name(), out);
        } else {
            writeString(value.toString(), out);
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out, int indent, boolean pretty) {
        out.append('{');
        if (map.isEmpty()) {
            out.append('}');
            return;
        }
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (pretty) {
                out.append('\n');
                indent(out, indent + 1);
            }
            writeString(String.valueOf(entry.getKey()), out);
            out.append(':');
            if (pretty) {
                out.append(' ');
            }
            writeValue(entry.getValue(), out, indent + 1, pretty);
            if (++i < map.size()) {
                out.append(',');
            }
        }
        if (pretty) {
            out.append('\n');
            indent(out, indent);
        }
        out.append('}');
    }

    private static void writeArray(Iterable<?> list, StringBuilder out, int indent, boolean pretty) {
        out.append('[');
        int i = 0;
        int size = 0;
        for (Object ignored : list) {
            size++;
        }
        for (Object value : list) {
            if (pretty) {
                out.append('\n');
                indent(out, indent + 1);
            }
            writeValue(value, out, indent + 1, pretty);
            if (++i < size) {
                out.append(',');
            }
        }
        if (size > 0 && pretty) {
            out.append('\n');
            indent(out, indent);
        }
        out.append(']');
    }

    private static void indent(StringBuilder out, int indent) {
        out.append("  ".repeat(Math.max(0, indent)));
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Unexpected content at position " + parser.position);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) value;
    }

    private static final class Parser {
        private final String text;
        private int position;

        private Parser(String text) {
            this.text = text;
        }

        private boolean atEnd() {
            return position >= text.length();
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private char peek() {
            if (atEnd()) {
                throw error("Unexpected end of JSON");
            }
            return text.charAt(position);
        }

        private char consume() {
            char c = peek();
            position++;
            return c;
        }

        private void expect(char expected) {
            skipWhitespace();
            char c = consume();
            if (c != expected) {
                throw error("Expected '" + expected + "' but found '" + c + "'");
            }
        }

        private Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, position)) {
                throw error("Invalid literal");
            }
            position += literal.length();
            return value;
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                position++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = consume();
                if (c == '}') {
                    break;
                }
                if (c != ',') {
                    throw error("Expected ',' or '}'");
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                position++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char c = consume();
                if (c == ']') {
                    break;
                }
                if (c != ',') {
                    throw error("Expected ',' or ']'");
                }
            }
            return list;
        }

        private String parseString() {
            skipWhitespace();
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = consume();
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    char esc = consume();
                    switch (esc) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (position + 4 > text.length()) {
                                throw error("Truncated unicode escape");
                            }
                            String hex = text.substring(position, position + 4);
                            position += 4;
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                throw error("Invalid unicode escape");
                            }
                        }
                        default -> throw error("Invalid escape \\" + esc);
                    }
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }

        private Number parseNumber() {
            int start = position;
            if (!atEnd() && peek() == '-') {
                position++;
            }
            while (!atEnd() && Character.isDigit(peek())) {
                position++;
            }
            if (!atEnd() && peek() == '.') {
                position++;
                while (!atEnd() && Character.isDigit(peek())) {
                    position++;
                }
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                position++;
                if (!atEnd() && (peek() == '+' || peek() == '-')) {
                    position++;
                }
                while (!atEnd() && Character.isDigit(peek())) {
                    position++;
                }
            }
            String token = text.substring(start, position);
            if (token.isBlank()) {
                throw error("Expected a value");
            }
            try {
                if (!token.contains(".") && !token.contains("e") && !token.contains("E")) {
                    return Long.parseLong(token);
                }
                return Double.parseDouble(token);
            } catch (NumberFormatException ex) {
                throw error("Invalid number '" + token + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + position);
        }
    }
}
