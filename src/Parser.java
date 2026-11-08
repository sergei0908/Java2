import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Превращает текст SQL-запроса в объект Query.
 * Работает в два шага:
 * 1) разбивает текст на слова (токены): SELECT, *, FROM, cars ...
 * 2) читает токены по порядку и заполняет Query.
 */
public class Parser {
    private final List<String> tokens;
    private int position = 0; // номер токена, который читаем сейчас

    private Parser(List<String> tokens) {
        this.tokens = tokens;
    }

    /** Разбирает текст запроса и возвращает объект Query. */
    public static Query parse(String sql) {
        Parser parser = new Parser(tokenize(sql));
        Query query = parser.parseCommand();
        parser.accept(";"); // точка с запятой в конце необязательна
        if (!parser.isEnd()) {
            throw new DbException("Unexpected text in query: " + parser.peek());
        }
        return query;
    }

    // ---------- Шаг 1: разбиение текста на токены ----------

    // Строковые значения сохраняем с кавычкой в начале ('BMW),
    // чтобы потом отличить строку 'BMW' от имени столбца BMW.
    private static List<String> tokenize(String sql) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '\'') {
                // Строка в кавычках. Две кавычки подряд означают одну кавычку: 'O''Brien'
                StringBuilder text = new StringBuilder("'");
                i++;
                while (true) {
                    if (i >= sql.length()) {
                        throw new DbException("String is not closed: missing '");
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
                // Слово или число (минус может стоять только в начале числа)
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
                throw new DbException("Unexpected character: " + c);
            }
        }
        return result;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    // ---------- Шаг 2: разбор команд ----------

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
                throw new DbException("Unknown command: " + word);
        }
    }

    // SELECT * FROM cars WHERE price > 20000
    // SELECT brand, price FROM cars
    private Query parseSelect() {
        Query query = new Query("SELECT");
        if (!accept("*")) {
            query.setColumns(parseNameList());
        }
        expect("FROM");
        query.setTable(parseName());
        query.setWhere(parseWhere());
        return query;
    }

    // INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE), (2, 'BMW', 30000.0, FALSE)
    // INSERT INTO cars (id, brand) VALUES (2, 'BMW')
    private Query parseInsert() {
        Query query = new Query("INSERT");
        expect("INTO");
        query.setTable(parseName());
        if (accept("(")) {
            query.setColumns(parseNameList());
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
            query.getRows().add(values);
        } while (accept(","));
        return query;
    }

    // UPDATE cars SET price = 30000, brand = 'Audi' WHERE id = 2
    private Query parseUpdate() {
        Query query = new Query("UPDATE");
        query.setTable(parseName());
        expect("SET");
        List<String> columns = new ArrayList<>();
        do {
            columns.add(parseName());
            expect("=");
            query.getNewValues().add(parseValue());
        } while (accept(","));
        query.setColumns(columns);
        query.setWhere(parseWhere());
        return query;
    }

    // DELETE FROM cars WHERE brand = 'Toyota'
    private Query parseDelete() {
        Query query = new Query("DELETE");
        expect("FROM");
        query.setTable(parseName());
        query.setWhere(parseWhere());
        return query;
    }

    // CREATE TABLE sales (id INT PRIMARY KEY, car_id INT REFERENCES cars(id), sold DATE)
    private Query parseCreateTable() {
        Query query = new Query("CREATE_TABLE");
        query.setTable(parseName());
        expect("(");
        do {
            query.getNewColumns().add(parseColumn());
        } while (accept(","));
        expect(")");
        return query;
    }

    private Column parseColumn() {
        String name = parseName();
        DataType type = parseType();
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
        return new Column(name, type, primaryKey, refTable, refColumn);
    }

    private DataType parseType() {
        String word = next().toUpperCase();
        try {
            return DataType.valueOf(word);
        } catch (IllegalArgumentException e) {
            throw new DbException("Unknown type: " + word + ". Use INT, DOUBLE, STRING, DATE or BOOLEAN");
        }
    }

    // DROP TABLE cars
    // DROP TABLE IF EXISTS cars
    private Query parseDropTable() {
        Query query = new Query("DROP_TABLE");
        if (accept("IF")) {
            expect("EXISTS");
            query.setIfExists(true);
        }
        query.setTable(parseName());
        return query;
    }

    // CREATE INDEX idx_cars_brand ON cars (brand)
    private Query parseCreateIndex() {
        Query query = new Query("CREATE_INDEX");
        query.setIndexName(parseName());
        expect("ON");
        query.setTable(parseName());
        expect("(");
        query.setIndexColumn(parseName());
        expect(")");
        return query;
    }

    // Необязательная часть: WHERE столбец оператор значение
    private Condition parseWhere() {
        if (!accept("WHERE")) {
            return null;
        }
        String column = parseName();
        String operator = next();
        if (operator.equals("<>")) {
            operator = "!="; // это два обозначения одного и того же
        }
        if (!Arrays.asList("=", "!=", "<", "<=", ">", ">=").contains(operator)) {
            throw new DbException("Unknown operator: " + operator);
        }
        return new Condition(column, operator, parseValue());
    }

    // Читает имена через запятую: id, brand, price
    private List<String> parseNameList() {
        List<String> names = new ArrayList<>();
        do {
            names.add(parseName());
        } while (accept(","));
        return names;
    }

    // Читает имя таблицы или столбца и приводит его к нижнему регистру.
    private String parseName() {
        String name = next().toLowerCase();
        // Имя состоит из букв, цифр и _, а начинается не с цифры.
        boolean valid = !Character.isDigit(name.charAt(0));
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                valid = false;
            }
        }
        if (!valid) {
            throw new DbException("Invalid name: " + name);
        }
        return name;
    }

    // Читает значение: строку, TRUE, FALSE, NULL или число.
    private Object parseValue() {
        String token = next();
        if (token.startsWith("'")) {
            return token.substring(1); // убираем служебную кавычку
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
            throw new DbException("Invalid value: " + token);
        }
    }

    // ---------- Вспомогательные методы для чтения токенов ----------

    private boolean isEnd() {
        return position >= tokens.size();
    }

    private String peek() {
        return isEnd() ? "" : tokens.get(position);
    }

    private String next() {
        if (isEnd()) {
            throw new DbException("Query ended unexpectedly");
        }
        return tokens.get(position++);
    }

    // Если следующий токен равен word, пропускает его и возвращает true.
    private boolean accept(String word) {
        if (peek().equalsIgnoreCase(word)) {
            position++;
            return true;
        }
        return false;
    }

    // Следующий токен обязан быть равен word, иначе это ошибка.
    private void expect(String word) {
        if (!accept(word)) {
            throw new DbException("Expected " + word + " but found: " + (isEnd() ? "end of query" : peek()));
        }
    }
}
