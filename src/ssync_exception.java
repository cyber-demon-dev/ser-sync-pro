/**
 * Exception for Serato sync library operations.
 */
public class ssync_exception extends Exception {

    public ssync_exception(String message) {
        super(message);
    }

    public ssync_exception(Throwable cause) {
        super(cause);
    }

    public ssync_exception(String message, Throwable cause) {
        super(message, cause);
    }
}
