import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Описание одного столбца таблицы. */
public class Column implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final DataType type;
    private final boolean primaryKey;
    private final String refTable;   // на какую таблицу ссылается столбец (null, если внешнего ключа нет)
    private final String refColumn;  // на какой столбец этой таблицы он ссылается

    public Column(String name, DataType type, boolean primaryKey, String refTable, String refColumn) {
        this.name = name;
        this.type = type;
        this.primaryKey = primaryKey;
        this.refTable = refTable;
        this.refColumn = refColumn;
    }

    public String getName() {
        return name;
    }

    public DataType getType() {
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

    /**
     * Приводит значение из запроса к типу столбца.
     * Если значение не подходит, выбрасывает исключение.
     */
    public Object convert(Object value) {
        if (value == null) {
            return null;
        }
        switch (type) {
            case INT:
                if (value instanceof Long) {
                    return value;
                }
                break;
            case DOUBLE:
                if (value instanceof Double) {
                    return value;
                }
                if (value instanceof Long) {
                    return ((Long) value).doubleValue(); // 30000 превращается в 30000.0
                }
                break;
            case STRING:
                if (value instanceof String) {
                    return value;
                }
                break;
            case DATE:
                if (value instanceof String) {
                    try {
                        return LocalDate.parse((String) value);
                    } catch (DateTimeParseException e) {
                        throw new DbException("Invalid date '" + value + "', expected YYYY-MM-DD");
                    }
                }
                break;
            case BOOLEAN:
                if (value instanceof Boolean) {
                    return value;
                }
                break;
        }
        throw new DbException("Value " + value + " does not fit column '" + name + "' of type " + type);
    }
}
