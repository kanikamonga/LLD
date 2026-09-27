package LLD.popularitycounter;

/** Thrown when adding a userId that is already being tracked. */
public class DuplicateUserException extends RuntimeException {
    public DuplicateUserException(String userId) {
        super("User '" + userId + "' already exists");
    }
}
