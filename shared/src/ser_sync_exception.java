/**
 * Exception for Serato sync library operations.
 */
public class ser_sync_exception extends Exception {

    public ser_sync_exception(String message) {
        super(message);
    }

    public ser_sync_exception(Throwable cause) {
        super(cause);
    }

    public ser_sync_exception(String message, Throwable cause) {
        super(message, cause);
    }
}
