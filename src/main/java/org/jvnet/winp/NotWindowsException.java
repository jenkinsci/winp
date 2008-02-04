package org.jvnet.winp;

/**
 * Indicates that the JVM is not running on Windows.
 * @author Kohsuke Kawaguchi
 */
public class NotWindowsException extends RuntimeException {
    public NotWindowsException(Throwable cause) {
        super(cause);
    }
}
