package org.jvnet.winp;

import java.lang.reflect.Field;

/**
 * Represents a Windows process.
 * 
 * @author Kohsuke Kawaguchi
 */
public class WinProcess {
    private final int pid;

    /**
     * Wraps a process ID.
     */
    public WinProcess(int pid) {
        this.pid = pid;
    }

    /**
     * Wraps {@link Process} into {@link WinProcess}.
     */
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

    /**
     * Kills this process and all the descendant processes that
     * this process launched. 
     */
    public void killRecursively() {
        Native.kill(pid,true);
    }

    /**
     * Sets the execution priority of this thread.
     *
     * @param priority
     *      One of the values from {@link Priority}.
     */
    public void setPriority(int priority) {
        Native.setPriority(pid,priority);
    }
}
