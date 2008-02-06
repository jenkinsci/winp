package org.jvnet.winp;

import java.lang.reflect.Field;

/**
 * Represents a Windows process.
 * 
 * @author Kohsuke Kawaguchi
 */
public class WinProcess {
    private final int pid;

    public WinProcess(int pid) {
        this.pid = pid;
    }

    public WinProcess(Process proc) {
        try {
            Field f = proc.getClass().getDeclaredField("handle");
            f.setAccessible(true);
            int handle = ((Number)f.get(proc)).intValue();
            pid = Native.getProcessId(handle);
        } catch (NoSuchFieldException e) {
            throw new NotWindowsException(e);
        } catch (IllegalAccessException e) {
            throw new NotWindowsException(e);
        }
    }

    public void killRecursively() {
        Native.kill(pid,true);
    }

    public void setPriority(Priority p) {
        Native.setPriority(pid,p.value);
    }
}
