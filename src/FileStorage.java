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

/**
 * Хранит каждую таблицу вместе с её индексами в отдельном файле: data/cars.tbl
 * Для записи используется стандартная сериализация Java.
 */
public class FileStorage implements Storage {
    private final File folder;

    public FileStorage(File folder) {
        this.folder = folder;
        folder.mkdirs();
    }

    /**
     * Сохраняет таблицу надёжно: новый файл получает настоящее имя только после полной записи.
     * Если программа упадёт во время записи, старый файл останется целым.
     */
    @Override
    public void save(Table table) {
        File temp = new File(folder, table.getName() + ".tmp");
        File target = new File(folder, table.getName() + ".tbl");
        // Буфер пишет и читает файл большими кусками: без него это работает намного медленнее.
        try (FileOutputStream stream = new FileOutputStream(temp);
             ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(stream))) {
            out.writeObject(table);
        } catch (IOException e) {
            throw new DbException("Cannot save table " + table.getName() + ": " + e.getMessage());
        }
        target.delete();
        if (!temp.renameTo(target)) {
            throw new DbException("Cannot save table " + table.getName());
        }
    }

    /** Загружает все таблицы из папки при запуске программы. */
    @Override
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

    @Override
    public void delete(String tableName) {
        new File(folder, tableName + ".tbl").delete();
    }

    // Читает таблицу из файла. Если файл испорчен, откладывает его в сторону и возвращает null.
    private Table read(File file) {
        // Поток файла объявлен отдельным ресурсом: он закроется, даже если испорченный файл не удалось
        // открыть как ObjectInputStream. Открытый файл Windows не даст переименовать.
        try (FileInputStream stream = new FileInputStream(file);
             ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(stream))) {
            return (Table) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Recovery: file " + file.getName() + " is damaged, renamed to .bad");
        }
        file.renameTo(new File(file.getPath() + ".bad"));
        return null;
    }

    // Файл .tmp остаётся, только если программа упала во время сохранения. Возможны два случая:
    // 1) файл .tbl есть: запись оборвалась, а целая версия таблицы лежит в .tbl, удаляем .tmp;
    // 2) файла .tbl нет: сбой случился между удалением .tbl и переименованием, .tmp записан полностью.
    private void recoverAfterCrash() {
        for (File temp : folder.listFiles()) {
            if (temp.getName().endsWith(".tmp")) {
                String tableName = temp.getName().replace(".tmp", "");
                File target = new File(folder, tableName + ".tbl");
                if (target.exists()) {
                    temp.delete();
                    System.out.println("Recovery: removed unfinished file " + temp.getName());
                } else {
                    temp.renameTo(target);
                    System.out.println("Recovery: restored table " + tableName);
                }
            }
        }
    }
}
