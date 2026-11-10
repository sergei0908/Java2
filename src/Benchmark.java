import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Замер скорости поиска с индексом и без. */
public class Benchmark {
    private static final String[] QUERIES = {
            "SELECT * FROM visits WHERE exercise = 'ex_500';",
            "SELECT * FROM visits WHERE weight > 999;",
            "SELECT * FROM visits WHERE weight > 990;",
            "SELECT * FROM visits WHERE weight < 200;",
    };

    public static void main(String[] args) {
        for (int rowCount : new int[]{10000, 100000, 1000000}) {
            runTest(rowCount);
        }
    }

    private static void runTest(int rowCount) {
        System.out.println("=== Строк: " + rowCount + " ===");
        File folder = new File("bench_data");
        deleteFolder(folder);
        Database db = new Database(folder);
        Executor executor = new Executor(db);
        executor.run("CREATE TABLE visits (id INT PRIMARY KEY, exercise STRING, weight DOUBLE, day DATE, done BOOLEAN);");

        Table table = db.getTable("visits");
        Random random = new Random(42);
        for (long i = 0; i < rowCount; i++) {
            table.insert(new Object[]{i, "ex_" + random.nextInt(1000), random.nextInt(100000) / 100.0,
                    LocalDate.of(2020, 1, 1).plusDays(random.nextInt(1500)), random.nextBoolean()});
        }

        long start = System.nanoTime();
        db.save(table);
        System.out.printf("сохранение таблицы: %.0f мс%n", since(start));

        double[] withoutIndex = new double[QUERIES.length];
        int[] sizes = new int[QUERIES.length];
        for (int i = 0; i < QUERIES.length; i++) {
            withoutIndex[i] = measure(table, QUERIES[i]);
            sizes[i] = table.find(Parser.parse(QUERIES[i])).size();
        }

        start = System.nanoTime();
        executor.run("CREATE INDEX idx_exercise ON visits (exercise);");
        System.out.printf("индекс по exercise: %.0f мс%n", since(start));
        start = System.nanoTime();
        executor.run("CREATE INDEX idx_weight ON visits (weight);");
        System.out.printf("индекс по weight: %.0f мс%n", since(start));

        for (int i = 0; i < QUERIES.length; i++) {
            double withIndex = measure(table, QUERIES[i]);
            Query query = Parser.parse(QUERIES[i]);
            List<Object[]> found = table.find(query);
            if (found.size() != sizes[i]) {
                throw new IllegalStateException("С индексом получен другой результат: " + QUERIES[i]);
            }
            System.out.printf("%s | строк: %d | без индекса: %.3f мс | с индексом: %.3f мс | %s%n",
                    QUERIES[i], sizes[i], withoutIndex[i], withIndex, table.searchMethod(query));
        }

        start = System.nanoTime();
        new Database(folder);
        System.out.printf("загрузка с диска: %.0f мс%n", since(start));
        System.out.printf("размер файла: %.1f МБ%n%n", new File(folder, "visits.tbl").length() / 1024.0 / 1024.0);
        deleteFolder(folder);
    }

    private static double measure(Table table, String sql) {
        Query query = Parser.parse(sql);
        double[] times = new double[20];
        for (int i = -5; i < times.length; i++) {
            long start = System.nanoTime();
            table.find(query);
            if (i >= 0) {
                times[i] = since(start);
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

    private static double since(long start) {
        return (System.nanoTime() - start) / 1000000.0;
    }
}
