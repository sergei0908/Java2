import java.util.List;

/** Выводит результат запроса на экран в виде таблицы. */
public class TablePrinter {

    /** Печатает заголовок и строки в виде таблицы. */
    public static void print(List<String> header, List<Object[]> rows) {
        // Ширина столбца равна длине самого длинного значения в нём.
        int[] widths = new int[header.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = header.get(i).length();
            for (Object[] row : rows) {
                widths[i] = Math.max(widths[i], text(row[i]).length());
            }
        }

        printLine(widths);
        printRow(header.toArray(), widths);
        printLine(widths);
        for (Object[] row : rows) {
            printRow(row, widths);
        }
        printLine(widths);
    }

    private static void printLine(int[] widths) {
        StringBuilder line = new StringBuilder("+");
        for (int width : widths) {
            for (int i = 0; i < width + 2; i++) {
                line.append('-');
            }
            line.append('+');
        }
        System.out.println(line);
    }

    private static void printRow(Object[] values, int[] widths) {
        StringBuilder line = new StringBuilder("|");
        for (int i = 0; i < values.length; i++) {
            String text = text(values[i]);
            line.append(' ').append(text);
            for (int j = text.length(); j < widths[i] + 1; j++) {
                line.append(' ');
            }
            line.append('|');
        }
        System.out.println(line);
    }

    private static String text(Object value) {
        return value == null ? "NULL" : value.toString();
    }
}
