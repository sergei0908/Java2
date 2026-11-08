import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Индекс по одному столбцу таблицы.
 *
 * Идея: TreeMap хранит ключи отсортированными и находит нужный ключ за O(log n),
 * поэтому не приходится просматривать всю таблицу.
 * Ключ - значение столбца, значение - первичные ключи строк с таким значением
 * (одно и то же значение столбца может встречаться в нескольких строках).
 */
public class Index implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final TreeMap<Object, List<Object>> map = new TreeMap<>();

    public Index(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** Добавляет строку в индекс. NULL в индекс не попадает. */
    public void add(Object value, Object primaryKey) {
        if (value == null) {
            return;
        }
        List<Object> keys = map.get(value);
        if (keys == null) {
            keys = new ArrayList<>();
            map.put(value, keys);
        }
        keys.add(primaryKey);
    }

    /** Удаляет строку из индекса. */
    public void remove(Object value, Object primaryKey) {
        if (value == null) {
            return;
        }
        List<Object> keys = map.get(value);
        keys.remove(primaryKey);
        if (keys.isEmpty()) {
            map.remove(value);
        }
    }

    /**
     * Возвращает первичные ключи строк, подходящих под условие "столбец operator value".
     * Метод tailMap даёт записи от значения и выше, метод headMap даёт записи ниже значения.
     */
    public List<Object> find(String operator, Object value) {
        List<Object> result = new ArrayList<>();
        switch (operator) {
            case "=":
                addIfPresent(result, value);
                break;
            case ">=":
                addAll(result, map.tailMap(value));
                break;
            case ">":
                for (Map.Entry<Object, List<Object>> entry : map.tailMap(value).entrySet()) {
                    if (!entry.getKey().equals(value)) { // само значение не подходит
                        result.addAll(entry.getValue());
                    }
                }
                break;
            case "<":
                addAll(result, map.headMap(value));
                break;
            case "<=":
                addAll(result, map.headMap(value));
                addIfPresent(result, value);
                break;
            default:
                throw new DbException("Index does not support operator " + operator);
        }
        return result;
    }

    private void addIfPresent(List<Object> result, Object value) {
        List<Object> keys = map.get(value);
        if (keys != null) {
            result.addAll(keys);
        }
    }

    private void addAll(List<Object> result, SortedMap<Object, List<Object>> part) {
        for (List<Object> keys : part.values()) {
            result.addAll(keys);
        }
    }
}
