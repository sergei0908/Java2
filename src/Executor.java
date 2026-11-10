import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Выполнение запросов и вывод результата в виде таблицы */
public class Executor {
    private final Database db;

    /** Создаёт исполнитель запросов для базы данных */
    public Executor(Database db) {
        this.db = db;
    }

    /** Выполняет запрос и возвращает ответ в виде текста */
    public String run(String sql) {
        Query query = Parser.parse(sql);
        switch (query.command) {
            case "SELECT":
                return select(query);
            case "INSERT":
                return insert(query);
            case "UPDATE":
                return update(query);
            case "DELETE":
                return delete(query);
            case "CREATE TABLE":
                return createTable(query);
            case "DROP TABLE":
                return dropTable(query);
            default:
                return createIndex(query);
        }
    }

    private String select(Query query) {
        Table table = db.getTable(query.table);
        List<String> names = query.columns;
        if (names == null) {
            names = new ArrayList<>();
            for (Column column : table.getColumns()) {
                names.add(column.getName());
            }
        }
        int[] positions = new int[names.size()];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = table.columnPosition(names.get(i));
        }

        List<Object[]> output = new ArrayList<>();
        for (Object[] row : table.find(query)) {
            Object[] outputRow = new Object[positions.length];
            for (int i = 0; i < positions.length; i++) {
                outputRow[i] = row[positions[i]];
            }
            output.add(outputRow);
        }
        return formatTable(names, output) + "Строк: " + output.size() + ", поиск: " + table.searchMethod(query);
    }

    private String insert(Query query) {
        Table table = db.getTable(query.table);
        List<Column> columns = table.getColumns();
        int expected = query.columns == null ? columns.size() : query.columns.size();

        List<Object[]> newRows = new ArrayList<>();
        Set<Object> newKeys = new HashSet<>();
        for (List<Object> values : query.rows) {
            if (values.size() != expected) {
                throw new IllegalArgumentException("Ожидалось значений: " + expected + ", получено: " + values.size());
            }
            Object[] row = new Object[columns.size()];
            for (int i = 0; i < values.size(); i++) {
                int position = query.columns == null ? i : table.columnPosition(query.columns.get(i));
                row[position] = columns.get(position).convert(values.get(i));
            }
            Object key = row[table.getKeyPosition()];
            if (key == null) {
                throw new IllegalArgumentException("Первичный ключ не может быть NULL");
            }
            if (table.hasKey(key) || !newKeys.add(key)) {
                throw new IllegalArgumentException("Первичный ключ повторяется: " + key);
            }
            checkForeignKeys(table, row);
            newRows.add(row);
        }

        for (Object[] row : newRows) {
            table.insert(row);
        }
        db.save(table);
        return "Вставлено строк: " + newRows.size();
    }

    private String update(Query query) {
        Table table = db.getTable(query.table);
        int[] positions = new int[query.columns.size()];
        Object[] values = new Object[positions.length];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = table.columnPosition(query.columns.get(i));
            if (positions[i] == table.getKeyPosition()) {
                throw new IllegalArgumentException("Первичный ключ нельзя изменить");
            }
            values[i] = table.getColumns().get(positions[i]).convert(query.newValues.get(i));
        }

        List<Object[]> oldRows = table.find(query);
        List<Object[]> newRows = new ArrayList<>();
        for (Object[] oldRow : oldRows) {
            Object[] newRow = oldRow.clone();
            for (int i = 0; i < positions.length; i++) {
                newRow[positions[i]] = values[i];
            }
            checkForeignKeys(table, newRow);
            newRows.add(newRow);
        }

        for (int i = 0; i < oldRows.size(); i++) {
            table.update(oldRows.get(i), newRows.get(i));
        }
        db.save(table);
        return "Обновлено строк: " + oldRows.size();
    }

    private String delete(Query query) {
        Table table = db.getTable(query.table);
        List<Object[]> rows = table.find(query);
        for (Object[] row : rows) {
            checkNotReferenced(table, row);
        }
        for (Object[] row : rows) {
            table.delete(row);
        }
        db.save(table);
        return "Удалено строк: " + rows.size();
    }

    private String createTable(Query query) {
        if (db.hasTable(query.table)) {
            throw new IllegalArgumentException("Таблица '" + query.table + "' уже существует");
        }
        int primaryKeys = 0;
        Set<String> names = new HashSet<>();
        for (Column column : query.newColumns) {
            if (!names.add(column.getName())) {
                throw new IllegalArgumentException("Столбец '" + column.getName() + "' повторяется");
            }
            if (column.isPrimaryKey()) {
                primaryKeys++;
            }
            if (column.getRefTable() != null) {
                Column parentKey = db.getTable(column.getRefTable()).getPrimaryKey();
                if (!parentKey.getName().equals(column.getRefColumn()) || !parentKey.getType().equals(column.getType())) {
                    throw new IllegalArgumentException("REFERENCES должен указывать на первичный ключ "
                            + column.getRefTable() + " того же типа");
                }
            }
        }
        if (primaryKeys != 1) {
            throw new IllegalArgumentException("В таблице должен быть ровно один столбец PRIMARY KEY");
        }
        db.addTable(new Table(query.table, query.newColumns));
        return "Таблица '" + query.table + "' создана";
    }

    private String dropTable(Query query) {
        if (query.ifExists && !db.hasTable(query.table)) {
            return "Таблицы '" + query.table + "' нет, пропущено";
        }
        db.getTable(query.table);
        for (Table other : db.getTables()) {
            for (Column column : other.getColumns()) {
                if (query.table.equals(column.getRefTable())) {
                    throw new IllegalArgumentException("На таблицу ссылается столбец " + other.getName() + "." + column.getName());
                }
            }
        }
        db.removeTable(query.table);
        return "Таблица '" + query.table + "' удалена";
    }

    private String createIndex(Query query) {
        Table table = db.getTable(query.table);
        for (Table other : db.getTables()) {
            for (Index index : other.getIndexes().values()) {
                if (index.getName().equals(query.indexName)) {
                    throw new IllegalArgumentException("Индекс '" + query.indexName + "' уже существует");
                }
            }
        }
        if (table.getIndexes().containsKey(query.indexColumn)) {
            throw new IllegalArgumentException("У столбца '" + query.indexColumn + "' уже есть индекс");
        }
        table.createIndex(query.indexName, query.indexColumn);
        db.save(table);
        return "Индекс '" + query.indexName + "' создан";
    }

    private void checkForeignKeys(Table table, Object[] row) {
        List<Column> columns = table.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            if (column.getRefTable() != null && row[i] != null && !db.getTable(column.getRefTable()).hasKey(row[i])) {
                throw new IllegalArgumentException("Значения " + row[i] + " столбца " + column.getName()
                        + " нет в таблице " + column.getRefTable());
            }
        }
    }

    private void checkNotReferenced(Table table, Object[] row) {
        Object key = row[table.getKeyPosition()];
        for (Table other : db.getTables()) {
            List<Column> columns = other.getColumns();
            for (int i = 0; i < columns.size(); i++) {
                if (table.getName().equals(columns.get(i).getRefTable())) {
                    for (Object[] otherRow : other) {
                        if (key.equals(otherRow[i])) {
                            throw new IllegalArgumentException("Строка с ключом " + key
                                    + " используется в таблице " + other.getName());
                        }
                    }
                }
            }
        }
    }

    private static String formatTable(List<String> header, List<Object[]> rows) {
        int[] widths = new int[header.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = header.get(i).length();
            for (Object[] row : rows) {
                widths[i] = Math.max(widths[i], text(row[i]).length());
            }
        }
        StringBuilder line = new StringBuilder("+");
        for (int width : widths) {
            for (int i = 0; i < width + 2; i++) {
                line.append('-');
            }
            line.append('+');
        }
        line.append('\n');

        StringBuilder result = new StringBuilder();
        result.append(line);
        appendRow(result, header.toArray(), widths);
        result.append(line);
        for (Object[] row : rows) {
            appendRow(result, row, widths);
        }
        result.append(line);
        return result.toString();
    }

    private static void appendRow(StringBuilder result, Object[] values, int[] widths) {
        result.append('|');
        for (int i = 0; i < values.length; i++) {
            String text = text(values[i]);
            result.append(' ').append(text);
            for (int j = text.length(); j < widths[i] + 1; j++) {
                result.append(' ');
            }
            result.append('|');
        }
        result.append('\n');
    }

    private static String text(Object value) {
        return value == null ? "NULL" : value.toString();
    }
}
