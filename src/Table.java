import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Таблица: столбцы, строки и индексы. */
public class Table implements Iterable<Object[]>, Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final List<Column> columns;
    private final int keyPosition;
    private final Map<Object, Object[]> rows = new LinkedHashMap<>();
    private final Map<String, Index> indexes = new HashMap<>();

    public Table(String name, List<Column> columns) {
        this.name = name;
        this.columns = columns;
        int position = 0;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).isPrimaryKey()) {
                position = i;
            }
        }
        this.keyPosition = position;
    }

    public String getName() {
        return name;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public Column getPrimaryKey() {
        return columns.get(keyPosition);
    }

    public int getKeyPosition() {
        return keyPosition;
    }

    public Map<String, Index> getIndexes() {
        return indexes;
    }

    public boolean hasKey(Object key) {
        return rows.containsKey(key);
    }

    public int columnPosition(String columnName) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equals(columnName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Нет столбца '" + columnName + "' в таблице '" + name + "'");
    }

    public Iterator<Object[]> iterator() {
        return rows.values().iterator();
    }

    public void insert(Object[] row) {
        Object key = row[keyPosition];
        rows.put(key, row);
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
            entry.getValue().add(row[columnPosition(entry.getKey())], key);
        }
    }

    public void update(Object[] oldRow, Object[] newRow) {
        Object key = oldRow[keyPosition];
        rows.put(key, newRow);
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
            int position = columnPosition(entry.getKey());
            entry.getValue().remove(oldRow[position], key);
            entry.getValue().add(newRow[position], key);
        }
    }

    public void delete(Object[] row) {
        Object key = row[keyPosition];
        rows.remove(key);
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
            entry.getValue().remove(row[columnPosition(entry.getKey())], key);
        }
    }

    public void createIndex(String indexName, String columnName) {
        int position = columnPosition(columnName);
        Index index = new Index(indexName);
        for (Object[] row : this) {
            index.add(row[position], row[keyPosition]);
        }
        indexes.put(columnName, index);
    }

    public List<Object[]> find(Query query) {
        List<Object[]> result = new ArrayList<>();
        if (query.whereColumn == null) {
            result.addAll(rows.values());
            return result;
        }
        int position = columnPosition(query.whereColumn);
        Object value = columns.get(position).convert(query.whereValue);
        if (value == null) {
            return result;
        }
        String method = searchMethod(query);
        if (method.equals("первичный ключ")) {
            Object[] row = rows.get(value);
            if (row != null) {
                result.add(row);
            }
        } else if (method.startsWith("индекс")) {
            for (Object key : indexes.get(query.whereColumn).find(query.whereOperator, value)) {
                result.add(rows.get(key));
            }
        } else {
            for (Object[] row : this) {
                if (row[position] != null && matches(compare(row[position], value), query.whereOperator)) {
                    result.add(row);
                }
            }
        }
        return result;
    }

    public String searchMethod(Query query) {
        if (query.whereColumn == null) {
            return "полный просмотр";
        }
        if (query.whereOperator.equals("=") && query.whereColumn.equals(getPrimaryKey().getName())) {
            return "первичный ключ";
        }
        if (indexes.containsKey(query.whereColumn) && !query.whereOperator.equals("!=")) {
            return "индекс " + indexes.get(query.whereColumn).getName();
        }
        return "полный просмотр";
    }

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

    private static boolean matches(int result, String operator) {
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
            default:
                return result >= 0;
        }
    }
}
