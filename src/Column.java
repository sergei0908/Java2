import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/** Столбец таблицы. */
public class Column implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final List<String> TYPES = Arrays.asList("INT", "DOUBLE", "STRING", "DATE", "BOOLEAN");

    private final String name;
    private final String type;
    private final boolean primaryKey;
    private final String refTable;
    private final String refColumn;

    public Column(String name, String type, boolean primaryKey, String refTable, String refColumn) {
        this.name = name;
        this.type = type;
        this.primaryKey = primaryKey;
        this.refTable = refTable;
        this.refColumn = refColumn;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public String getRefTable() {
        return refTable;
    }

    public String getRefColumn() {
        return refColumn;
    }

    public Object convert(Object value) {
        if (value == null) {
            return null;
        }
        switch (type) {
            case "INT":
                if (value instanceof Long) {
                    return value;
                }
                break;
            case "DOUBLE":
                if (value instanceof Double) {
                    return value;
                }
                if (value instanceof Long) {
                    return ((Long) value).doubleValue();
                }
                break;
            case "STRING":
                if (value instanceof String) {
                    return value;
                }
                break;
            case "DATE":
                if (value instanceof String) {
                    try {
                        return LocalDate.parse((String) value);
                    } catch (DateTimeParseException e) {
                        throw new IllegalArgumentException("Неверная дата '" + value + "', нужен формат ГГГГ-ММ-ДД");
                    }
                }
                break;
            default:
                if (value instanceof Boolean) {
                    return value;
                }
        }
        throw new IllegalArgumentException("Значение " + value + " не подходит столбцу '" + name + "' типа " + type);
    }
}
