package org.jvnet.winp;

/**
 * Indicates a problem in Winp.
 * 
 * @author Kohsuke Kawaguchi
 */
public class WinpException extends RuntimeException {
    private int win32ErrorCode = -1;

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

    /**
     * Used by JNI to report an error.
     */
    public WinpException(String message, int lastError, String file, int line) {
        this(message+" error="+lastError+" at "+file+":"+line);
        win32ErrorCode = lastError;
    }

    /**
     * Win32 error code, if this error originated in a failed Win32 API call.
     *
     * @return
     *      -1 if the exception was not due to the failed Win32 API call.
     */
    public int getWin32ErrorCode() {
        return win32ErrorCode;
    }
}
