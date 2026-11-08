import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Сравнивает скорость запросов без индекса и с индексом.
 * Запуск: java -cp out Benchmark
 */
public class Benchmark {
    private static final String[] QUERIES = {
            "SELECT * FROM items WHERE category = 'cat_500';", // примерно 0.1% строк
            "SELECT * FROM items WHERE price > 99900;",        // примерно 0.1% строк
            "SELECT * FROM items WHERE price > 99000;",        // примерно 1% строк
            "SELECT * FROM items WHERE price < 20000;",        // примерно 20% строк
    };

    /** Запускает программу. */
    public static void main(String[] args) {
        runTest(10000);
        runTest(100000);
        runTest(1000000);
    }

    private static void runTest(int rowCount) {
        System.out.println("=== " + rowCount + " rows ===");
        File folder = new File("bench_data");
        deleteFolder(folder);
        Database database = new Database(new FileStorage(folder));
        Executor executor = new Executor(database);
        executor.run("CREATE TABLE items (id INT PRIMARY KEY, category STRING, price DOUBLE, created DATE, active BOOLEAN);");

        // Заполняем таблицу напрямую, чтобы не сохранять файл после каждой строки.
        Table table = database.getTable("items");
        Random random = new Random(42);
        for (long i = 0; i < rowCount; i++) {
            table.insert(new Object[]{
                    i,
                    "cat_" + random.nextInt(1000),
                    random.nextInt(10000000) / 100.0,
                    LocalDate.of(2020, 1, 1).plusDays(random.nextInt(1500)),
                    random.nextBoolean()
            });
        }

        // Замеры без индекса.
        long start = System.nanoTime();
        database.save(table);
        System.out.printf("save table: %.0f ms%n", millisecondsSince(start));
        List<Double> withoutIndex = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        for (String sql : QUERIES) {
            withoutIndex.add(measure(executor, sql));
            sizes.add(executor.run(sql).getRows().size());
        }

        // Создаём индексы (время включает сохранение таблицы на диск).
        start = System.nanoTime();
        executor.run("CREATE INDEX idx_category ON items (category);");
        System.out.printf("create index on category: %.0f ms%n", millisecondsSince(start));
        start = System.nanoTime();
        executor.run("CREATE INDEX idx_price ON items (price);");
        System.out.printf("create index on price: %.0f ms%n", millisecondsSince(start));

        // Замеры с индексом. Число найденных строк обязано совпасть.
        for (int i = 0; i < QUERIES.length; i++) {
            double withIndex = measure(executor, QUERIES[i]);
            Result result = executor.run(QUERIES[i]);
            if (result.getRows().size() != sizes.get(i)) {
                throw new IllegalStateException("Index gave a different result: " + QUERIES[i]);
            }
            System.out.printf("%s | rows: %d | no index: %.3f ms | index: %.3f ms | search: %s%n",
                    QUERIES[i], sizes.get(i), withoutIndex.get(i), withIndex, result.getSearch());
        }

        // Загрузка с диска, как при запуске программы.
        start = System.nanoTime();
        new Database(new FileStorage(folder));
        System.out.printf("load from disk: %.0f ms%n", millisecondsSince(start));
        File file = new File(folder, "items.tbl");
        System.out.printf("file size: %.1f MB%n%n", file.length() / 1024.0 / 1024.0);
        deleteFolder(folder);
    }

    // Выполняет запрос 25 раз: первые 5 для разогрева Java, по остальным берём медиану.
    private static double measure(Executor executor, String sql) {
        double[] times = new double[20];
        for (int i = -5; i < times.length; i++) {
            long start = System.nanoTime();
            executor.run(sql);
            if (i >= 0) {
                times[i] = millisecondsSince(start);
            }
        }
        Arrays.sort(times);
        return times[times.length / 2];
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        folder.delete();
    }

    private static double millisecondsSince(long start) {
        return (System.nanoTime() - start) / 1000000.0;
    }
}
