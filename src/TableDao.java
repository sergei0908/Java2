import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Сохранение таблиц в файлы и загрузка их обратно */
public class TableDao {
    private final File folder;

    /** Создаёт DAO для указанной папки и создаёт её, если её нет */
    public TableDao(File folder) {
        this.folder = folder;
        folder.mkdirs();
    }

    /** Загружает все таблицы из папки, перед этим исправляет последствия сбоя */
    public List<Table> loadAll() {
        recoverAfterCrash();
        List<Table> tables = new ArrayList<>();
        for (File file : folder.listFiles()) {
            if (file.getName().endsWith(".tbl")) {
                Table table = read(file);
                if (table != null) {
                    tables.add(table);
                }
            }
        }
        return tables;
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

    /** Удаляет файл таблицы */
    public void delete(String tableName) {
        new File(folder, tableName + ".tbl").delete();
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
