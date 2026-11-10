import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/** Консоль базы данных тренажёрного зала */
public class Main {
    private static final char BOM = 0xFEFF;

    /** Запускает консоль или выполняет запросы из файла, если он указан */
    public static void main(String[] args) throws IOException {
        Executor executor = new Executor(new Database(new File("data")));
        boolean fromFile = args.length > 0;
        BufferedReader reader;
        if (fromFile) {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8"));
        } else {
            reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("GymDB. Каждый запрос заканчивается ';'. Для выхода введите exit или выход.");
        }

        StringBuilder buffer = new StringBuilder();
        while (true) {
            if (!fromFile) {
                System.out.print(buffer.length() == 0 ? "gym> " : "  -> ");
            }
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            line = line.replace(String.valueOf(BOM), "").trim();
            if (fromFile) {
                System.out.println(line);
            }
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("выход")) {
                break;
            }
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            buffer.append(line).append(' ');
            int start = 0;
            boolean inString = false;
            for (int i = 0; i < buffer.length(); i++) {
                char c = buffer.charAt(i);
                if (c == '\'') {
                    inString = !inString;
                } else if (c == ';' && !inString) {
                    runQuery(executor, buffer.substring(start, i + 1));
                    start = i + 1;
                }
            }
            buffer.delete(0, start);
            if (buffer.toString().trim().isEmpty()) {
                buffer.setLength(0);
            }
        }
        reader.close();
    }

    private static void runQuery(Executor executor, String sql) {
        long start = System.nanoTime();
        try {
            String result = executor.run(sql);
            System.out.println(result);
            System.out.printf("Время: %.3f мс%n", (System.nanoTime() - start) / 1000000.0);
        } catch (IllegalArgumentException e) {
            System.out.println("ОШИБКА: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("НЕОЖИДАННАЯ ОШИБКА: " + e);
        }
        System.out.println();
    }
}
