import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Паттерн Repository: все таблицы базы. Сохранять их на диск просит TableDao */
public class Database {
    private final TableDao dao;
    private final Map<String, Table> tables = new HashMap<>();

    /** Открывает базу в указанной папке и загружает все таблицы */
    public Database(File folder) {
        dao = new TableDao(folder);
        for (Table table : dao.loadAll()) {
            tables.put(table.getName(), table);
        }
    }

    /** Проверяет, есть ли таблица с таким именем */
    public boolean hasTable(String name) {
        return tables.containsKey(name);
    }

    /** Возвращает таблицу по имени, если её нет — выбрасывает ошибку */
    public Table getTable(String name) {
        Table table = tables.get(name);
        if (table == null) {
            throw new IllegalArgumentException("Таблицы '" + name + "' не существует");
        }
        return table;
    }

    /** Возвращает все таблицы базы */
    public Collection<Table> getTables() {
        return tables.values();
    }

    /** Сохраняет новую таблицу на диск и добавляет её в базу */
    public void addTable(Table table) {
        save(table);
        tables.put(table.getName(), table);
    }

    /** Удаляет таблицу из базы и её файл с диска */
    public void removeTable(String name) {
        tables.remove(name);
        dao.delete(name);
    }

    /** Сохраняет таблицу на диск */
    public void save(Table table) {
        dao.save(table);
    }
}
