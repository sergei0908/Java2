import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Индекс по одному столбцу таблицы на основе TreeMap. */
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

    public List<Object> find(String operator, Object value) {
        Map<Object, List<Object>> part;
        switch (operator) {
            case "=":
                part = map.subMap(value, true, value, true);
                break;
            case ">":
                part = map.tailMap(value, false);
                break;
            case ">=":
                part = map.tailMap(value, true);
                break;
            case "<":
                part = map.headMap(value, false);
                break;
            default:
                part = map.headMap(value, true);
        }
        List<Object> result = new ArrayList<>();
        for (List<Object> keys : part.values()) {
            result.addAll(keys);
        }
        return result;
    }
}
