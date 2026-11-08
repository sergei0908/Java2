import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

/**
 * Точка входа: консоль, в которой пользователь вводит SQL-запросы.
 * Запуск без аргументов: интерактивный режим.
 * Запуск с именем файла: выполнить все запросы из файла.
 */
public class Main {
    private static final Executor executor = new Executor(new Database(new FileStorage(new File("data"))));

    // Сюда копим строки, пока запрос не закончится точкой с запятой.
    private static final StringBuilder buffer = new StringBuilder();

    /** Запускает программу. */
    public static void main(String[] args) {
        if (args.length > 0) {
            runFile(args[0]);
        } else {
            runConsole();
        }
    }

    private static void runFile(String fileName) {
        // Файл с запросами читаем в кодировке UTF-8, чтобы русский текст не испортился.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"))) {
            String line = reader.readLine();
            while (line != null) {
                System.out.println(line);
                handleLine(line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            System.out.println("ERROR: cannot read file " + fileName + ": " + e.getMessage());
        }
    }

    private static void runConsole() {
        System.out.println("MiniDB. Each query ends with ';'. Type exit to quit.");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print(buffer.length() == 0 ? "minidb> " : "     -> ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String line = scanner.nextLine();
            if (line.trim().equalsIgnoreCase("exit")) {
                return;
            }
            handleLine(line);
        }
    }

    private static void handleLine(String line) {
        String text = line.trim();
        if (text.isEmpty() || text.startsWith("--")) {
            return; // пустая строка или комментарий
        }
        buffer.append(text).append(' ');
        if (text.endsWith(";")) {
            String sql = buffer.toString();
            buffer.setLength(0);
            runQuery(sql);
        }
    }

    private static void runQuery(String sql) {
        long start = System.nanoTime();
        try {
            Result result = executor.run(sql);
            double milliseconds = (System.nanoTime() - start) / 1000000.0;
            if (result.isTable()) {
                TablePrinter.print(result.getHeader(), result.getRows());
                System.out.printf("%d row(s), %.3f ms, search: %s%n", result.getRows().size(), milliseconds, result.getSearch());
            } else {
                System.out.printf("%s (%.3f ms)%n", result.getMessage(), milliseconds);
            }
        } catch (DbException e) {
            System.out.println("ERROR: " + e.getMessage());
        } catch (Exception e) {
            // Аварийный выход: неожиданная ошибка не должна закрывать программу.
            System.out.println("UNEXPECTED ERROR: " + e);
        }
        System.out.println();
    }
}
