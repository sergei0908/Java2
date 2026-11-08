import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Выполняет запросы.
 * Правило для всех изменений: сначала проверяем все данные, и только потом меняем таблицу.
 * Поэтому при ошибке таблица остаётся такой же, какой была.
 */
public class Executor {
    private final Database db;

    public Executor(Database db) {
        this.db = db;
    }

    /** Разбирает и выполняет запрос. */
    public Result run(String sql) {
        Query query = Parser.parse(sql);
        switch (query.getCommand()) {
            case "SELECT":
                return select(query);
            case "INSERT":
                return insert(query);
            case "UPDATE":
                return update(query);
            case "DELETE":
                return delete(query);
            case "CREATE_TABLE":
                return createTable(query);
            case "DROP_TABLE":
                return dropTable(query);
            default: // CREATE_INDEX
                return createIndex(query);
        }
    }

    private Result select(Query query) {
        Table table = db.getTable(query.getTable());

        // Какие столбцы выводить: для * берём все.
        List<String> names = query.getColumns();
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

        // Из каждой найденной строки оставляем только нужные столбцы.
        List<Object[]> output = new ArrayList<>();
        for (Object[] row : table.find(query.getWhere())) {
            Object[] outputRow = new Object[positions.length];
            for (int i = 0; i < positions.length; i++) {
                outputRow[i] = row[positions[i]];
            }
            output.add(outputRow);
        }
        return new Result(names, output, table.describeSearch(query.getWhere()));
    }

    private Result insert(Query query) {
        Table table = db.getTable(query.getTable());
        List<Column> columns = table.getColumns();

        // Шаг 1: собираем новые строки и проверяем их.
        List<Object[]> newRows = new ArrayList<>();
        Set<Object> newKeys = new HashSet<>();
        for (List<Object> values : query.getRows()) {
            int expected = query.getColumns() == null ? columns.size() : query.getColumns().size();
            if (values.size() != expected) {
                throw new DbException("Expected " + expected + " value(s) but got " + values.size());
            }
            Object[] row = new Object[columns.size()]; // столбцы без значения останутся NULL
            for (int i = 0; i < values.size(); i++) {
                int position = query.getColumns() == null ? i : table.columnPosition(query.getColumns().get(i));
                row[position] = columns.get(position).convert(values.get(i));
            }
            checkPrimaryKey(table, row, newKeys);
            checkForeignKeys(table, row);
            newRows.add(row);
        }

        // Шаг 2: все проверки пройдены, вставляем и сохраняем на диск.
        for (Object[] row : newRows) {
            table.insert(row);
        }
        db.save(table);
        return new Result(newRows.size() + " row(s) inserted");
    }

    private Result update(Query query) {
        Table table = db.getTable(query.getTable());
        List<Column> columns = table.getColumns();

        // Шаг 1: находим номера столбцов и приводим новые значения к их типам.
        int[] positions = new int[query.getColumns().size()];
        Object[] values = new Object[positions.length];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = table.columnPosition(query.getColumns().get(i));
            if (positions[i] == table.getPrimaryKeyPosition()) {
                throw new DbException("Primary key cannot be changed");
            }
            values[i] = columns.get(positions[i]).convert(query.getNewValues().get(i));
        }

        // Шаг 2: готовим новые версии строк и проверяем их.
        List<Object[]> oldRows = table.find(query.getWhere());
        List<Object[]> newRows = new ArrayList<>();
        for (Object[] oldRow : oldRows) {
            Object[] newRow = oldRow.clone();
            for (int i = 0; i < positions.length; i++) {
                newRow[positions[i]] = values[i];
            }
            checkForeignKeys(table, newRow);
            newRows.add(newRow);
        }

        // Шаг 3: применяем изменения и сохраняем на диск.
        for (int i = 0; i < oldRows.size(); i++) {
            table.update(oldRows.get(i), newRows.get(i));
        }
        db.save(table);
        return new Result(oldRows.size() + " row(s) updated");
    }

    private Result delete(Query query) {
        Table table = db.getTable(query.getTable());
        List<Object[]> rows = table.find(query.getWhere());
        for (Object[] row : rows) {
            checkNotReferenced(table, row);
        }
        for (Object[] row : rows) {
            table.delete(row);
        }
        db.save(table);
        return new Result(rows.size() + " row(s) deleted");
    }

    private Result createTable(Query query) {
        if (db.hasTable(query.getTable())) {
            throw new DbException("Table '" + query.getTable() + "' already exists");
        }
        int primaryKeys = 0;
        Set<String> names = new HashSet<>();
        for (Column column : query.getNewColumns()) {
            if (!names.add(column.getName())) {
                throw new DbException("Duplicate column '" + column.getName() + "'");
            }
            if (column.isPrimaryKey()) {
                primaryKeys++;
            }
            if (column.getRefTable() != null) {
                checkReference(column);
            }
        }
        if (primaryKeys != 1) {
            throw new DbException("Table must have exactly one PRIMARY KEY column");
        }
        db.addTable(new Table(query.getTable(), query.getNewColumns()));
        return new Result("Table '" + query.getTable() + "' created");
    }

    private Result dropTable(Query query) {
        if (query.isIfExists() && !db.hasTable(query.getTable())) {
            return new Result("Table '" + query.getTable() + "' does not exist, skipped");
        }
        Table table = db.getTable(query.getTable());

        // Нельзя удалить таблицу, на которую ссылается другая таблица.
        for (Table other : db.getTables()) {
            for (Column column : other.getColumns()) {
                if (table.getName().equals(column.getRefTable())) {
                    throw new DbException("Table is referenced by " + other.getName() + "." + column.getName());
                }
            }
        }
        db.removeTable(query.getTable());
        return new Result("Table '" + query.getTable() + "' dropped");
    }

    private Result createIndex(Query query) {
        Table table = db.getTable(query.getTable());
        for (Table other : db.getTables()) {
            for (Index index : other.getIndexes().values()) {
                if (index.getName().equals(query.getIndexName())) {
                    throw new DbException("Index '" + query.getIndexName() + "' already exists");
                }
            }
        }
        if (table.getIndexes().containsKey(query.getIndexColumn())) {
            throw new DbException("Column '" + query.getIndexColumn() + "' already has an index");
        }
        table.createIndex(query.getIndexName(), query.getIndexColumn());
        db.save(table);
        return new Result("Index '" + query.getIndexName() + "' created");
    }

    // ---------- Проверки ----------

    // Первичный ключ не должен быть NULL и не должен повторяться.
    private void checkPrimaryKey(Table table, Object[] row, Set<Object> newKeys) {
        Object key = row[table.getPrimaryKeyPosition()];
        if (key == null) {
            throw new DbException("Primary key cannot be NULL");
        }
        if (table.hasKey(key) || !newKeys.add(key)) {
            throw new DbException("Duplicate primary key: " + key);
        }
    }

    // Значение внешнего ключа должно существовать в родительской таблице.
    private void checkForeignKeys(Table table, Object[] row) {
        List<Column> columns = table.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            if (column.getRefTable() != null && row[i] != null && !db.getTable(column.getRefTable()).hasKey(row[i])) {
                throw new DbException("Value " + row[i] + " of " + column.getName()
                        + " does not exist in table " + column.getRefTable());
            }
        }
    }

    // Нельзя удалить строку, на которую ссылаются строки другой таблицы.
    private void checkNotReferenced(Table table, Object[] row) {
        Object key = row[table.getPrimaryKeyPosition()];
        for (Table other : db.getTables()) {
            List<Column> columns = other.getColumns();
            for (int i = 0; i < columns.size(); i++) {
                if (table.getName().equals(columns.get(i).getRefTable())) {
                    for (Object[] otherRow : other) {
                        if (key.equals(otherRow[i])) {
                            throw new DbException("Row with key " + key + " is used in table " + other.getName());
                        }
                    }
                }
            }
        }
    }

    // REFERENCES должен указывать на первичный ключ существующей таблицы того же типа.
    private void checkReference(Column column) {
        Column parentKey = db.getTable(column.getRefTable()).getPrimaryKey();
        if (!parentKey.getName().equals(column.getRefColumn())) {
            throw new DbException("REFERENCES must point to the primary key of " + column.getRefTable());
        }
        if (parentKey.getType() != column.getType()) {
            throw new DbException("Column '" + column.getName() + "' must have type " + parentKey.getType());
        }
    }
}
