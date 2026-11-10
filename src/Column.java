import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/** Столбец таблицы */
public class Column implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Типы данных, которые можно использовать в столбцах */
    public static final List<String> TYPES = Arrays.asList("INT", "DOUBLE", "STRING", "DATE", "BOOLEAN");

    /** Имя столбца */
    private final String name;
    /** Тип столбца */
    private final String type;
    /** Является ли столбец первичным ключом */
    private final boolean primaryKey;
    /** Таблица, на которую ссылается столбец, или null */
    private final String refTable;
    /** Столбец, на который ссылается этот столбец, или null */
    private final String refColumn;

    /** Создаёт описание столбца */
    public Column(String name, String type, boolean primaryKey, String refTable, String refColumn) {
        this.name = name;
        this.type = type;
        this.primaryKey = primaryKey;
        this.refTable = refTable;
        this.refColumn = refColumn;
    }

    /** Возвращает имя столбца */
    public String getName() {
        return name;
    }

    /** Возвращает тип столбца */
    public String getType() {
        return type;
    }

    /** Проверяет, является ли столбец первичным ключом */
    public boolean isPrimaryKey() {
        return primaryKey;
    }

    /** Возвращает таблицу, на которую ссылается столбец */
    public String getRefTable() {
        return refTable;
    }

    /** Возвращает столбец, на который ссылается этот столбец */
    public String getRefColumn() {
        return refColumn;
    }

    /** Приводит значение из запроса к типу столбца, если не подходит — выбрасывает ошибку */
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
