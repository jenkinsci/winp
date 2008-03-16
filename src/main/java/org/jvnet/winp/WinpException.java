package org.jvnet.winp;

/**
 * Indicates a problem in Winp.
 * 
 * @author Kohsuke Kawaguchi
 */
public class WinpException extends RuntimeException {
    public WinpException() {
    }

    public WinpException(String message) {
        super(message);
    }

    public WinpException(String message, Throwable cause) {
        super(message, cause);
    }

    public WinpException(Throwable cause) {
        super(cause);
    }
}
