import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Разбор текста SQL-запроса в объект Query */
public class Parser {
    private final List<String> tokens;
    private int position = 0;

    private Parser(List<String> tokens) {
        this.tokens = tokens;
    }

    /** Разбирает текст запроса и возвращает объект Query */
    public static Query parse(String sql) {
        Parser parser = new Parser(tokenize(sql));
        Query query = parser.parseCommand();
        parser.accept(";");
        if (parser.position < parser.tokens.size()) {
            throw new IllegalArgumentException("Лишний текст в запросе: " + parser.peek());
        }
        return query;
    }

    private static List<String> tokenize(String sql) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '\'') {
                StringBuilder text = new StringBuilder("'");
                i++;
                while (true) {
                    if (i >= sql.length()) {
                        throw new IllegalArgumentException("Строка не закрыта: не хватает '");
                    }
                    if (sql.charAt(i) != '\'') {
                        text.append(sql.charAt(i));
                        i++;
                    } else if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        text.append('\'');
                        i += 2;
                    } else {
                        i++;
                        break;
                    }
                }
                result.add(text.toString());
            } else if (isWordChar(c) || c == '-') {
                int start = i;
                i++;
                while (i < sql.length() && isWordChar(sql.charAt(i))) {
                    i++;
                }
                result.add(sql.substring(start, i));
            } else if (sql.startsWith("<=", i) || sql.startsWith(">=", i)
                    || sql.startsWith("!=", i) || sql.startsWith("<>", i)) {
                result.add(sql.substring(i, i + 2));
                i += 2;
            } else if ("(),;*=<>".indexOf(c) >= 0) {
                result.add(String.valueOf(c));
                i++;
            } else {
                throw new IllegalArgumentException("Недопустимый символ: " + c);
            }
        }
        return result;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    private Query parseCommand() {
        String word = next().toUpperCase();
        switch (word) {
            case "SELECT":
                return parseSelect();
            case "INSERT":
                return parseInsert();
            case "UPDATE":
                return parseUpdate();
            case "DELETE":
                return parseDelete();
            case "CREATE":
                if (accept("TABLE")) {
                    return parseCreateTable();
                }
                expect("INDEX");
                return parseCreateIndex();
            case "DROP":
                expect("TABLE");
                return parseDropTable();
            default:
                throw new IllegalArgumentException("Неизвестная команда: " + word);
        }
    }

    private Query parseSelect() {
        Query query = new Query("SELECT");
        if (!accept("*")) {
            query.columns = parseNameList();
        }
        expect("FROM");
        query.table = parseName();
        parseWhere(query);
        return query;
    }

    private Query parseInsert() {
        Query query = new Query("INSERT");
        expect("INTO");
        query.table = parseName();
        if (accept("(")) {
            query.columns = parseNameList();
            expect(")");
        }
        expect("VALUES");
        do {
            expect("(");
            List<Object> values = new ArrayList<>();
            do {
                values.add(parseValue());
            } while (accept(","));
            expect(")");
            query.rows.add(values);
        } while (accept(","));
        return query;
    }

    private Query parseUpdate() {
        Query query = new Query("UPDATE");
        query.table = parseName();
        expect("SET");
        query.columns = new ArrayList<>();
        do {
            query.columns.add(parseName());
            expect("=");
            query.newValues.add(parseValue());
        } while (accept(","));
        parseWhere(query);
        return query;
    }

    private Query parseDelete() {
        Query query = new Query("DELETE");
        expect("FROM");
        query.table = parseName();
        parseWhere(query);
        return query;
    }

    private Query parseCreateTable() {
        Query query = new Query("CREATE TABLE");
        query.table = parseName();
        expect("(");
        do {
            String name = parseName();
            String type = next().toUpperCase();
            if (!Column.TYPES.contains(type)) {
                throw new IllegalArgumentException("Неизвестный тип: " + type + ". Доступны " + Column.TYPES);
            }
            boolean primaryKey = false;
            String refTable = null;
            String refColumn = null;
            if (accept("PRIMARY")) {
                expect("KEY");
                primaryKey = true;
            }
            if (accept("REFERENCES")) {
                refTable = parseName();
                expect("(");
                refColumn = parseName();
                expect(")");
            }
            query.newColumns.add(new Column(name, type, primaryKey, refTable, refColumn));
        } while (accept(","));
        expect(")");
        return query;
    }

    private Query parseDropTable() {
        Query query = new Query("DROP TABLE");
        if (accept("IF")) {
            expect("EXISTS");
            query.ifExists = true;
        }
        query.table = parseName();
        return query;
    }

    private Query parseCreateIndex() {
        Query query = new Query("CREATE INDEX");
        query.indexName = parseName();
        expect("ON");
        query.table = parseName();
        expect("(");
        query.indexColumn = parseName();
        expect(")");
        return query;
    }

    private void parseWhere(Query query) {
        if (!accept("WHERE")) {
            return;
        }
        query.whereColumn = parseName();
        String operator = next();
        if (operator.equals("<>")) {
            operator = "!=";
        }
        if (!Arrays.asList("=", "!=", "<", "<=", ">", ">=").contains(operator)) {
            throw new IllegalArgumentException("Неизвестный оператор: " + operator);
        }
        query.whereOperator = operator;
        query.whereValue = parseValue();
    }

    private List<String> parseNameList() {
        List<String> names = new ArrayList<>();
        do {
            names.add(parseName());
        } while (accept(","));
        return names;
    }

    private String parseName() {
        String name = next().toLowerCase();
        boolean valid = !Character.isDigit(name.charAt(0));
        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                valid = false;
            }
        }
        if (!valid) {
            throw new IllegalArgumentException("Недопустимое имя: " + name);
        }
        return name;
    }

    private Object parseValue() {
        String token = next();
        if (token.startsWith("'")) {
            return token.substring(1);
        }
        if (token.equalsIgnoreCase("TRUE")) {
            return Boolean.TRUE;
        }
        if (token.equalsIgnoreCase("FALSE")) {
            return Boolean.FALSE;
        }
        if (token.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            if (token.contains(".")) {
                return Double.parseDouble(token);
            }
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Недопустимое значение: " + token);
        }
    }

    private String peek() {
        return position < tokens.size() ? tokens.get(position) : "";
    }

    private String next() {
        if (position >= tokens.size()) {
            throw new IllegalArgumentException("Запрос неожиданно закончился");
        }
        return tokens.get(position++);
    }

    private boolean accept(String word) {
        if (peek().equalsIgnoreCase(word)) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(String word) {
        if (!accept(word)) {
            String found = position < tokens.size() ? peek() : "конец запроса";
            throw new IllegalArgumentException("Ожидалось " + word + ", а найдено: " + found);
        }
    }
}
