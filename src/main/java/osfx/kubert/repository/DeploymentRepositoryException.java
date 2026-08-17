package osfx.kubert.repository;

public final class DeploymentRepositoryException extends Exception {
    private static final long serialVersionUID = 1L;

    public DeploymentRepositoryException(String message) {
        super(message);
    }

    public DeploymentRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
