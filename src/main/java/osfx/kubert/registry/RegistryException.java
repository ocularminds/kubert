package osfx.kubert.registry;

public final class RegistryException extends Exception {
    private static final long serialVersionUID = 1L;

    public RegistryException(String message) {
        super(message);
    }

    public RegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
