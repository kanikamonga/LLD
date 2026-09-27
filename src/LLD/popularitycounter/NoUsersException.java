package LLD.popularitycounter;

/** Thrown when querying the most-voted user while no users are being tracked. */
public class NoUsersException extends RuntimeException {
    public NoUsersException(String message) {
        super(message);
    }
}
