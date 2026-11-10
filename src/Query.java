import java.util.ArrayList;
import java.util.List;

/** Разобранный запрос */
public class Query {
    final String command;
    String table;
    List<String> columns;
    final List<List<Object>> rows = new ArrayList<>();
    final List<Object> newValues = new ArrayList<>();
    final List<Column> newColumns = new ArrayList<>();
    boolean ifExists;
    String indexName;
    String indexColumn;
    String whereColumn;
    String whereOperator;
    Object whereValue;

    /** Создаёт пустой запрос с указанной командой */
    public Query(String command) {
        this.command = command;
    }
}
