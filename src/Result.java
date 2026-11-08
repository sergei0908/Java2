import java.util.List;

/** Результат запроса: либо сообщение, либо таблица (для SELECT). */
public class Result {
    private String message;
    private List<String> header;
    private List<Object[]> rows;
    private String search; // каким способом найдены строки

    /** Результат-сообщение. */
    public Result(String message) {
        this.message = message;
    }

    /** Результат-таблица. */
    public Result(List<String> header, List<Object[]> rows, String search) {
        this.header = header;
        this.rows = rows;
        this.search = search;
    }

    /** Возвращает true, если результат является таблицей. */
    public boolean isTable() {
        return header != null;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getHeader() {
        return header;
    }

    public List<Object[]> getRows() {
        return rows;
    }

    public String getSearch() {
        return search;
    }
}
