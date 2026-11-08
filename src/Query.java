import java.util.ArrayList;
import java.util.List;

/** Разобранный запрос. Parser заполняет только те поля, которые нужны команде. */
public class Query {
    private final String command;  // SELECT, INSERT, UPDATE, DELETE, CREATE_TABLE, DROP_TABLE, CREATE_INDEX
    private String table;
    private Condition where;       // условие WHERE (null, если его нет)

    // SELECT: выбираемые столбцы (null означает *)
    // INSERT: столбцы в скобках (null означает все столбцы)
    // UPDATE: столбцы из SET
    private List<String> columns;

    // INSERT: значения, для каждой вставляемой записи свой список
    private final List<List<Object>> rows = new ArrayList<>();

    // UPDATE: новые значения, в том же порядке, что и columns
    private final List<Object> newValues = new ArrayList<>();

    // CREATE TABLE: новые столбцы
    private final List<Column> newColumns = new ArrayList<>();

    // DROP TABLE IF EXISTS
    private boolean ifExists;

    // CREATE INDEX: имя индекса и индексируемый столбец
    private String indexName;
    private String indexColumn;

    public Query(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public Condition getWhere() {
        return where;
    }

    public void setWhere(Condition where) {
        this.where = where;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public List<Object> getNewValues() {
        return newValues;
    }

    public List<Column> getNewColumns() {
        return newColumns;
    }

    public boolean isIfExists() {
        return ifExists;
    }

    public void setIfExists(boolean ifExists) {
        this.ifExists = ifExists;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getIndexColumn() {
        return indexColumn;
    }

    public void setIndexColumn(String indexColumn) {
        this.indexColumn = indexColumn;
    }
}
