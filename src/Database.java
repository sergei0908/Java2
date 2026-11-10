import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** База данных: все таблицы и их хранение в файлах */
public class Database {
    private final File folder;
    private final Map<String, Table> tables = new HashMap<>();

    /** Открывает базу в указанной папке и загружает все таблицы */
    public Database(File folder) {
        this.folder = folder;
        folder.mkdirs();
        recoverAfterCrash();
        for (File file : folder.listFiles()) {
            if (file.getName().endsWith(".tbl")) {
                Table table = read(file);
                if (table != null) {
                    tables.put(table.getName(), table);
                }
            }
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
        new File(folder, name + ".tbl").delete();
    }

    /** Сохраняет таблицу в файл через временный файл */
    public void save(Table table) {
        folder.mkdirs();
        File temp = new File(folder, table.getName() + ".tmp");
        File target = new File(folder, table.getName() + ".tbl");
        try (FileOutputStream stream = new FileOutputStream(temp);
             ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(stream))) {
            out.writeObject(table);
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось сохранить таблицу " + table.getName() + ": " + e.getMessage());
        }
        target.delete();
        if (!temp.renameTo(target)) {
            throw new IllegalArgumentException("Не удалось сохранить таблицу " + table.getName());
        }
    }

    private Table read(File file) {
        try (FileInputStream stream = new FileInputStream(file);
             ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(stream))) {
            return (Table) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Восстановление: файл " + file.getName() + " повреждён, переименован в .bad");
        }
        file.renameTo(new File(file.getPath() + ".bad"));
        return null;
    }

    private void recoverAfterCrash() {
        for (File temp : folder.listFiles()) {
            if (temp.getName().endsWith(".tmp")) {
                String tableName = temp.getName().replace(".tmp", "");
                File target = new File(folder, tableName + ".tbl");
                if (target.exists()) {
                    temp.delete();
                    System.out.println("Восстановление: удалён недописанный файл " + temp.getName());
                } else {
                    temp.renameTo(target);
                    System.out.println("Восстановление: таблица " + tableName + " восстановлена");
                }
            }
        }
    }
}
