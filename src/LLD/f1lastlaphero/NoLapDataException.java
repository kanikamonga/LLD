package LLD.f1lastlaphero;

/** Thrown when querying for a hero before any lap data has been recorded. */
public class NoLapDataException extends RuntimeException {
    public NoLapDataException(String message) {
        super(message);
    }
}
