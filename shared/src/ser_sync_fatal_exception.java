/**
 * Fatal exception thrown when the application encounters an unrecoverable
 * error.
 * Replaces System.exit() calls to allow proper cleanup and testability.
 */
public class ser_sync_fatal_exception extends RuntimeException {

    public ser_sync_fatal_exception(String message) {
        super(message);
    }

    public ser_sync_fatal_exception(String message, Throwable cause) {
        super(message, cause);
    }
}
