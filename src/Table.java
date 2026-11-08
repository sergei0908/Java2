import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Таблица: столбцы, строки и индексы.
 * Реализует Iterable (паттерн Iterator), поэтому строки можно обойти так: for (Object[] row : table)
 */
public class Table implements Iterable<Object[]>, Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final List<Column> columns;
    private final int primaryKeyPosition;

    // Строки по значению первичного ключа. Каждая строка - массив значений в порядке столбцов.
    // LinkedHashMap запоминает порядок вставки и быстро находит строку по ключу.
    private final Map<Object, Object[]> rows = new LinkedHashMap<>();

    // Индексы: имя столбца -> индекс по этому столбцу.
    private final Map<String, Index> indexes = new HashMap<>();

    public Table(String name, List<Column> columns) {
        this.name = name;
        this.columns = columns;
        int position = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).isPrimaryKey()) {
                position = i;
            }
        }
        this.primaryKeyPosition = position;
    }

    public String getName() {
        return name;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public Column getPrimaryKey() {
        return columns.get(primaryKeyPosition);
    }

    public int getPrimaryKeyPosition() {
        return primaryKeyPosition;
    }

    public int getRowCount() {
        return rows.size();
    }

    public Set<Object> getKeys() {
        return rows.keySet();
    }

    public Map<String, Index> getIndexes() {
        return indexes;
    }

    public boolean hasKey(Object primaryKey) {
        return rows.containsKey(primaryKey);
    }

    /** Возвращает номер столбца по имени. */
    public int columnPosition(String columnName) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equals(columnName)) {
                return i;
            }
        }
        throw new DbException("Unknown column '" + columnName + "' in table '" + name + "'");
    }

    @Override
    public Iterator<Object[]> iterator() {
        return rows.values().iterator();
    }

    /** Добавляет строку и обновляет индексы. */
    public void insert(Object[] row) {
        Object key = row[primaryKeyPosition];
        rows.put(key, row);
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
            entry.getValue().add(row[columnPosition(entry.getKey())], key);
        }
    }

    /** Заменяет старую версию строки новой (первичный ключ у них одинаковый). */
    public void update(Object[] oldRow, Object[] newRow) {
        Object key = oldRow[primaryKeyPosition];
        rows.put(key, newRow);
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
            int position = columnPosition(entry.getKey());
            entry.getValue().remove(oldRow[position], key);
            entry.getValue().add(newRow[position], key);
        }
    }

    /** Удаляет строку и её записи в индексах. */
    public void delete(Object[] row) {
        Object key = row[primaryKeyPosition];
        rows.remove(key);
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
            entry.getValue().remove(row[columnPosition(entry.getKey())], key);
        }
    }

    /** Создаёт индекс по столбцу и заполняет его уже существующими строками. */
    public void createIndex(String indexName, String columnName) {
        int position = columnPosition(columnName);
        Index index = new Index(indexName);
        for (Object[] row : this) {
            index.add(row[position], row[primaryKeyPosition]);
        }
        indexes.put(columnName, index);
    }

    /**
     * Находит строки, подходящие под условие. Выбирает самый быстрый способ поиска:
     * 1) по первичному ключу, 2) через индекс, 3) просмотр всей таблицы.
     */
    public List<Object[]> find(Condition where) {
        List<Object[]> result = new ArrayList<>();
        if (where == null) {
            result.addAll(rows.values());
            return result;
        }
        int position = columnPosition(where.getColumn());
        Object value = columns.get(position).convert(where.getValue());
        if (value == null) {
            return result; // сравнение с NULL никогда не выполняется
        }
        if (usesPrimaryKey(where)) {
            Object[] row = rows.get(value);
            if (row != null) {
                result.add(row);
            }
        } else if (usesIndex(where)) {
            for (Object key : indexes.get(where.getColumn()).find(where.getOperator(), value)) {
                result.add(rows.get(key));
            }
        } else {
            for (Object[] row : this) {
                if (where.check(row[position], value)) {
                    result.add(row);
                }
            }
        }
        return result;
    }

    /** Текстовое описание способа поиска, которое выводится под результатом запроса. */
    public String describeSearch(Condition where) {
        if (where != null && usesPrimaryKey(where)) {
            return "primary key";
        }
        if (where != null && usesIndex(where)) {
            return "index " + indexes.get(where.getColumn()).getName();
        }
        return "full scan";
    }

    private boolean usesPrimaryKey(Condition where) {
        return where.getOperator().equals("=") && where.getColumn().equals(getPrimaryKey().getName());
    }

    private boolean usesIndex(Condition where) {
        // Для != индекс бесполезен: подходят почти все строки.
        return indexes.containsKey(where.getColumn()) && !where.getOperator().equals("!=");
    }
}
