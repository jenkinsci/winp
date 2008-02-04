package org.jvnet.winp;

import java.lang.reflect.Field;

/**
 * Represents a Windows process.
 * 
 * @author Kohsuke Kawaguchi
 */
public class WinProcess {
    private final Process proc;
    private final int handle;

    public WinProcess(Process proc) {
        this.proc = proc;

        try {
            Field f = proc.getClass().getDeclaredField("handle");
            f.setAccessible(true);
            handle = ((Number)f.get(proc)).intValue();
        } catch (NoSuchFieldException e) {
            throw new NotWindowsException(e);
        } catch (IllegalAccessException e) {
            throw new NotWindowsException(e);
        }
    }

    public void killRecursively() {
        int pid = Native.getProcessId(handle);
        Native.kill(pid,true);
    }
}
