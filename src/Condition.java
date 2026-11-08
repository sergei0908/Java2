import java.time.LocalDate;

/** Условие из WHERE, например: {@code price > 20000}. */
public class Condition {
    private final String column;
    private final String operator; // одно из: = != < <= > >=
    private final Object value;

    public Condition(String column, String operator, Object value) {
        this.column = column;
        this.operator = operator;
        this.value = value;
    }

    public String getColumn() {
        return column;
    }

    public String getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    /**
     * Проверяет условие для значения из ячейки таблицы.
     * convertedValue - это значение из условия, уже приведённое к типу столбца.
     */
    public boolean check(Object cell, Object convertedValue) {
        // Сравнение с NULL никогда не выполняется, как и в настоящем SQL.
        if (cell == null || convertedValue == null) {
            return false;
        }
        int result = compare(cell, convertedValue);
        switch (operator) {
            case "=":
                return result == 0;
            case "!=":
                return result != 0;
            case "<":
                return result < 0;
            case "<=":
                return result <= 0;
            case ">":
                return result > 0;
            default: // ">="
                return result >= 0;
        }
    }

    // Сравнивает два значения одного типа с помощью их метода compareTo.
    // Результат: меньше нуля, если a < b; ноль, если равны; больше нуля, если a > b.
    private static int compare(Object a, Object b) {
        if (a instanceof Long) {
            return ((Long) a).compareTo((Long) b);
        }
        if (a instanceof Double) {
            return ((Double) a).compareTo((Double) b);
        }
        if (a instanceof String) {
            return ((String) a).compareTo((String) b);
        }
        if (a instanceof LocalDate) {
            return ((LocalDate) a).compareTo((LocalDate) b);
        }
        return ((Boolean) a).compareTo((Boolean) b);
    }
}
