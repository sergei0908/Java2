import java.util.ArrayList;
import java.util.List;

/** Разобранный запрос */
public class Query {
    private final String command;
    private String table;
    private List<String> columns;
    private final List<List<Object>> rows = new ArrayList<>();
    private final List<Object> newValues = new ArrayList<>();
    private final List<Column> newColumns = new ArrayList<>();
    private boolean ifExists;
    private String indexName;
    private String indexColumn;
    private String whereColumn;
    private String whereOperator;
    private Object whereValue;

    /** Создаёт пустой запрос с указанной командой */
    public Query(String command) {
        this.command = command;
    }

    /** Возвращает команду: SELECT, INSERT, UPDATE и т. д. */
    public String getCommand() {
        return command;
    }

    /** Возвращает имя таблицы */
    public String getTable() {
        return table;
    }

    /** Задаёт имя таблицы */
    public void setTable(String table) {
        this.table = table;
    }

    /** Возвращает столбцы: для SELECT и INSERT null означает все столбцы */
    public List<String> getColumns() {
        return columns;
    }

    /** Задаёт столбцы */
    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    /** Возвращает значения для INSERT: по списку на каждую строку */
    public List<List<Object>> getRows() {
        return rows;
    }

    /** Возвращает новые значения для UPDATE */
    public List<Object> getNewValues() {
        return newValues;
    }

    /** Возвращает столбцы новой таблицы для CREATE TABLE */
    public List<Column> getNewColumns() {
        return newColumns;
    }

    /** Возвращает true, если в DROP TABLE указано IF EXISTS */
    public boolean isIfExists() {
        return ifExists;
    }

    /** Задаёт признак IF EXISTS */
    public void setIfExists(boolean ifExists) {
        this.ifExists = ifExists;
    }

    /** Возвращает имя индекса для CREATE INDEX */
    public String getIndexName() {
        return indexName;
    }

    /** Задаёт имя индекса */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    /** Возвращает индексируемый столбец */
    public String getIndexColumn() {
        return indexColumn;
    }

    /** Задаёт индексируемый столбец */
    public void setIndexColumn(String indexColumn) {
        this.indexColumn = indexColumn;
    }

    /** Возвращает столбец из WHERE или null, если условия нет */
    public String getWhereColumn() {
        return whereColumn;
    }

    /** Возвращает оператор из WHERE */
    public String getWhereOperator() {
        return whereOperator;
    }

    /** Возвращает значение из WHERE */
    public Object getWhereValue() {
        return whereValue;
    }

    /** Задаёт условие WHERE */
    public void setWhere(String column, String operator, Object value) {
        this.whereColumn = column;
        this.whereOperator = operator;
        this.whereValue = value;
    }
}
