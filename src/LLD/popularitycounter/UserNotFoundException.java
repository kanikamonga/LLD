package LLD.popularitycounter;

/** Thrown when an operation references a userId that has not been added (or was removed). */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String userId) {
        super("User '" + userId + "' not found");
    }
}
