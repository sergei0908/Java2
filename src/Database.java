import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Все таблицы базы данных (паттерн Repository).
 * При создании загружает таблицы, а сохранять их просит объект Storage.
 */
public class Database {
    private final Storage storage;
    private final Map<String, Table> tables = new HashMap<>();

    public Database(Storage storage) {
        this.storage = storage;
        for (Table table : storage.loadAll()) {
            tables.put(table.getName(), table);
        }
    }

    /** Проверяет, существует ли таблица. */
    public boolean hasTable(String name) {
        return tables.containsKey(name);
    }

    /** Возвращает таблицу по имени или выбрасывает исключение, если её нет. */
    public Table getTable(String name) {
        Table table = tables.get(name);
        if (table == null) {
            throw new DbException("Table '" + name + "' does not exist");
        }
        return table;
    }

    /** Возвращает все таблицы. */
    public Collection<Table> getTables() {
        return tables.values();
    }

    /** Добавляет новую таблицу и сохраняет её. */
    public void addTable(Table table) {
        tables.put(table.getName(), table);
        save(table);
    }

    /** Удаляет таблицу вместе с её файлом. */
    public void removeTable(String name) {
        tables.remove(name);
        storage.delete(name);
    }

    /** Сохраняет таблицу. */
    public void save(Table table) {
        storage.save(table);
    }
}
