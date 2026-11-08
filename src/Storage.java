import java.util.List;

/**
 * Паттерн DAO: остальная программа работает с таблицами через этот интерфейс
 * и не знает, как именно они хранятся.
 */
public interface Storage {
    /** Сохраняет таблицу. */
    void save(Table table);

    /** Загружает все сохранённые таблицы. */
    List<Table> loadAll();

    /** Удаляет сохранённую таблицу. */
    void delete(String tableName);
}
