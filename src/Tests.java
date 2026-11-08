import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Простые автоматические тесты. Запуск: java -cp out Tests */
public class Tests {
    private static int failed = 0;
    private static int folderCount = 0;

    /** Запускает программу. */
    public static void main(String[] args) throws IOException {
        testInsertAndPrimaryKey();
        testSelect();
        testUpdateAndDelete();
        testForeignKey();
        testCreateAndDropTable();
        testIndexIsUsed();
        testIndexGivesSameResultAsFullScan();
        testDataIsSavedOnDisk();
        testRecoveryAfterCrash();
        testBadQueries();
        deleteFolder(new File("test_data"));

        System.out.println(failed == 0 ? "All tests passed" : "Failed checks: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // База с двумя таблицами в новой временной папке.
    private static Executor carsDatabase(File folder) {
        Executor db = open(folder);
        db.run("CREATE TABLE cars (id INT PRIMARY KEY, brand STRING, price DOUBLE, available BOOLEAN);");
        db.run("CREATE TABLE sales (id INT PRIMARY KEY, car_id INT REFERENCES cars(id), sold DATE);");
        db.run("INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE), (2, 'BMW', 40000, FALSE), (3, 'Lada', 9000.5, TRUE);");
        return db;
    }

    private static void testInsertAndPrimaryKey() throws IOException {
        Executor db = carsDatabase(newFolder());
        db.run("INSERT INTO cars (id, brand) VALUES (4, 'Kia');");
        check("column without value is NULL", db.run("SELECT price FROM cars WHERE id = 4;").getRows().get(0)[0] == null);
        checkError(db, "INSERT INTO cars VALUES (1, 'Dup', 1.0, TRUE);", "Duplicate primary key");
        // Ошибка во второй строке не должна вставить и первую.
        checkError(db, "INSERT INTO cars VALUES (5, 'A', 1.0, TRUE), (5, 'B', 1.0, TRUE);", "Duplicate primary key");
        check("nothing inserted after error", db.run("SELECT * FROM cars;").getRows().size() == 4);
        checkError(db, "INSERT INTO cars (brand) VALUES ('NoId');", "cannot be NULL");
        checkError(db, "INSERT INTO cars VALUES (6, 'X', 'text', TRUE);", "does not fit");
        checkError(db, "INSERT INTO cars VALUES (6, 'X');", "Expected 4 value");
        checkError(db, "INSERT INTO sales VALUES (1, 1, '2024-13-45');", "Invalid date");
    }

    private static void testSelect() throws IOException {
        Executor db = carsDatabase(newFolder());
        Result result = db.run("SELECT brand, price FROM cars WHERE price > 20000;");
        check("two rows found", result.getRows().size() == 2);
        check("two columns", result.getHeader().equals(Arrays.asList("brand", "price")));
        check("brand is first", result.getRows().get(0)[0].equals("Toyota"));
        check("boolean condition", db.run("SELECT * FROM cars WHERE available = TRUE;").getRows().size() == 2);
        check("not equal", db.run("SELECT * FROM cars WHERE brand <> 'BMW';").getRows().size() == 2);
        check("keywords in any case", db.run("select * from CARS where ID = 1").getRows().size() == 1);
    }

    private static void testUpdateAndDelete() throws IOException {
        Executor db = carsDatabase(newFolder());
        check("one updated", db.run("UPDATE cars SET price = 30000 WHERE id = 2;").getMessage().startsWith("1 row"));
        check("new value", db.run("SELECT price FROM cars WHERE id = 2;").getRows().get(0)[0].equals(30000.0));
        checkError(db, "UPDATE cars SET id = 7 WHERE id = 2;", "cannot be changed");
        check("one deleted", db.run("DELETE FROM cars WHERE brand = 'Toyota';").getMessage().startsWith("1 row"));
        db.run("DELETE FROM cars;");
        check("all deleted", db.run("SELECT * FROM cars;").getRows().isEmpty());
    }

    private static void testForeignKey() throws IOException {
        Executor db = carsDatabase(newFolder());
        checkError(db, "INSERT INTO sales VALUES (1, 99, '2024-01-01');", "does not exist in table cars");
        db.run("INSERT INTO sales VALUES (1, 1, '2024-01-01');");
        checkError(db, "UPDATE sales SET car_id = 99 WHERE id = 1;", "does not exist in table cars");
        checkError(db, "DELETE FROM cars WHERE id = 1;", "is used in table sales");
        checkError(db, "DROP TABLE cars;", "referenced by");
        db.run("DELETE FROM sales WHERE car_id = 1;");
        check("delete works when not used", db.run("DELETE FROM cars WHERE id = 1;").getMessage().startsWith("1 row"));
    }

    private static void testCreateAndDropTable() throws IOException {
        Executor db = carsDatabase(newFolder());
        checkError(db, "CREATE TABLE cars (id INT PRIMARY KEY);", "already exists");
        checkError(db, "CREATE TABLE t (a INT);", "exactly one PRIMARY KEY");
        checkError(db, "CREATE TABLE t (a FOO PRIMARY KEY);", "Unknown type");
        db.run("DROP TABLE sales;");
        checkError(db, "SELECT * FROM sales;", "does not exist");
        check("if exists", db.run("DROP TABLE IF EXISTS sales;").getMessage().contains("skipped"));
    }

    private static void testIndexIsUsed() throws IOException {
        Executor db = carsDatabase(newFolder());
        check("full scan", db.run("SELECT * FROM cars WHERE brand = 'BMW';").getSearch().equals("full scan"));
        check("primary key", db.run("SELECT * FROM cars WHERE id = 2;").getSearch().equals("primary key"));
        db.run("CREATE INDEX idx_brand ON cars (brand);");
        check("index", db.run("SELECT * FROM cars WHERE brand = 'BMW';").getSearch().equals("index idx_brand"));
        check("!= does not use index", db.run("SELECT * FROM cars WHERE brand != 'BMW';").getSearch().equals("full scan"));
        checkError(db, "CREATE INDEX idx_brand ON cars (price);", "already exists");
        checkError(db, "CREATE INDEX idx2 ON cars (brand);", "already has an index");
    }

    // Главная проверка индекса: на случайных данных результат такой же, как при полном просмотре.
    private static void testIndexGivesSameResultAsFullScan() throws IOException {
        Executor withIndex = open(newFolder());
        Executor noIndex = open(newFolder());
        String create = "CREATE TABLE t (id INT PRIMARY KEY, a INT);";
        withIndex.run(create);
        noIndex.run(create);
        withIndex.run("CREATE INDEX idx_a ON t (a);");

        Random random = new Random(1);
        StringBuilder insert = new StringBuilder("INSERT INTO t VALUES (0, NULL)");
        for (int i = 1; i < 2000; i++) {
            insert.append(", (").append(i).append(", ").append(random.nextInt(100)).append(")");
        }
        withIndex.run(insert.toString());
        noIndex.run(insert.toString());

        // Изменяем и удаляем строки, чтобы индекс тоже обновлялся.
        String[] changes = {"UPDATE t SET a = 500 WHERE a = 7;", "DELETE FROM t WHERE a = 9;", "DELETE FROM t WHERE id = 5;"};
        for (String change : changes) {
            withIndex.run(change);
            noIndex.run(change);
        }

        String[] operators = {"=", "<", "<=", ">", ">="};
        for (String operator : operators) {
            for (int value : new int[]{-1, 0, 7, 9, 50, 99, 500, 1000}) {
                String sql = "SELECT id FROM t WHERE a " + operator + " " + value + ";";
                check(sql, sortedIds(withIndex.run(sql)).equals(sortedIds(noIndex.run(sql))));
            }
        }
    }

    private static List<Long> sortedIds(Result result) {
        List<Long> ids = new ArrayList<>();
        for (Object[] row : result.getRows()) {
            ids.add((Long) row[0]);
        }
        Collections.sort(ids);
        return ids;
    }

    private static void testDataIsSavedOnDisk() throws IOException {
        File folder = newFolder();
        Executor first = carsDatabase(folder);
        first.run("CREATE INDEX idx_brand ON cars (brand);");
        first.run("INSERT INTO sales VALUES (1, 2, '2024-12-31');");

        // Новый объект базы читает всё только с диска, как при новом запуске программы.
        Executor second = open(folder);
        check("rows restored", second.run("SELECT * FROM cars;").getRows().size() == 3);
        Result result = second.run("SELECT id FROM cars WHERE brand = 'Lada';");
        check("index restored", result.getSearch().equals("index idx_brand") && result.getRows().get(0)[0].equals(3L));
        check("date restored", second.run("SELECT sold FROM sales;").getRows().get(0)[0].toString().equals("2024-12-31"));
        checkError(second, "INSERT INTO cars VALUES (1, 'Dup', 1.0, TRUE);", "Duplicate primary key");
    }

    private static void testRecoveryAfterCrash() throws IOException {
        File folder = newFolder();
        carsDatabase(folder);

        // Имитируем сбой: запись таблицы оборвалась, остался недописанный временный файл.
        File unfinished = new File(folder, "cars.tmp");
        writeGarbage(unfinished);
        Executor db = open(folder);
        check("data survived", db.run("SELECT * FROM cars;").getRows().size() == 3);
        check("temp file removed", !unfinished.exists());

        // Имитируем испорченный файл таблицы: остальные таблицы должны работать.
        writeGarbage(new File(folder, "sales.tbl"));
        db = open(folder);
        check("damaged table skipped", db.run("SELECT * FROM cars;").getRows().size() == 3);
        check("damaged file kept", new File(folder, "sales.tbl.bad").exists());

        // Имитируем сбой между удалением старого файла и переименованием нового.
        new File(folder, "cars.tbl").renameTo(new File(folder, "cars.tmp"));
        db = open(folder);
        check("table restored from tmp", db.run("SELECT * FROM cars;").getRows().size() == 3);
    }

    private static void testBadQueries() throws IOException {
        Executor db = carsDatabase(newFolder());
        checkError(db, "SELEC * FROM cars;", "Unknown command");
        checkError(db, "SELECT * FROM cars WHERE", "Query ended");
        checkError(db, "SELECT * FROM cars WHERE;", "Invalid name");
        checkError(db, "SELECT nope FROM cars;", "Unknown column");
        checkError(db, "SELECT * FROM nope;", "does not exist");
        checkError(db, "SELECT * FROM cars WHERE price ~ 1;", "Unexpected character");
        checkError(db, "SELECT * FROM cars WHERE price LIKE 1;", "Unknown operator");
        checkError(db, "INSERT INTO cars VALUES (1, 'not closed);", "not closed");
        check("quote inside string", db.run("INSERT INTO cars VALUES (9, 'O''Brien', 1.0, TRUE);").getMessage().startsWith("1 row"));
        check("quote value", db.run("SELECT brand FROM cars WHERE id = 9;").getRows().get(0)[0].equals("O'Brien"));
    }

    // ---------- Вспомогательные методы ----------

    private static Executor open(File folder) {
        return new Executor(new Database(new FileStorage(folder)));
    }

    // Новая пустая папка для одного теста.
    private static File newFolder() {
        folderCount++;
        File folder = new File("test_data", "db" + folderCount);
        deleteFolder(folder);
        return folder;
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteFolder(file);
            }
        }
        folder.delete();
    }

    private static void writeGarbage(File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[]{1, 2, 3});
        }
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            failed++;
            System.out.println("FAILED: " + name);
        }
    }

    // Запрос обязан завершиться ошибкой с указанным текстом.
    private static void checkError(Executor db, String sql, String expectedText) {
        try {
            db.run(sql);
            check("should fail: " + sql, false);
        } catch (DbException e) {
            check(sql + " -> " + e.getMessage(), e.getMessage().contains(expectedText));
        }
    }
}
