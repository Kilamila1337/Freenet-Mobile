package org.freenetproject.mobile.installer;

/**
 * Thrown when installing the node fails, for example because the node directory
 * cannot be created or written to.
 */
public class InstallerException extends Exception {
    public InstallerException(String message) {
        super(message);
    }

    public InstallerException(String message, Throwable cause) {
        super(message, cause);
    }
}
