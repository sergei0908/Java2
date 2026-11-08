/** Ошибка, которую мы показываем пользователю (например, "таблицы не существует"). */
public class DbException extends RuntimeException {
    public DbException(String message) {
        super(message);
    }
}
