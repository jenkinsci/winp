package org.jvnet.winp;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.TreeMap;

/**
 * Represents a Windows process.
 * 
 * @author Kohsuke Kawaguchi
 */
public class WinProcess {
    private final int pid;

    // these values are lazily obtained, in a pair
    private String commandline;
    private TreeMap<String,String> envVars;

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

    /**
     * Gets the command line given to this process.
     *
     * On Windows, a command line is a single string, unlike Unix.
     * The tokenization semantics is up to applications.
     *
     * @throws WinpException
     *      If we fail to obtain the command line. For example,
     *      maybe we didn't have enough security privileges.
     */
    public synchronized String getCommandLine() {
        if(commandline==null)
            parseCmdLineAndEnvVars();
        return commandline;
    }

    /**
     * Gets the environment variables of this process.
     *
     * <p>
     * The returned map has a case-insensitive comparison semantics.
     *
     * @return
     *      Never null. 
     *
     * @throws WinpException
     *      If we fail to obtain the command line. For example,
     *      maybe we didn't have enough security privileges.
     */
    public synchronized TreeMap<String,String> getEnvironmentVariables() {
        if(envVars==null)
            parseCmdLineAndEnvVars();
        return envVars;
    }

    private void parseCmdLineAndEnvVars() {
        String s = Native.getCmdLineAndEnvVars(pid);
        int sep = s.indexOf('\0');
        commandline = s.substring(0,sep);
        envVars = new TreeMap<String,String>(CASE_INSENSITIVE_COMPARATOR);
        s = s.substring(sep+1);

        while(s.length()>0) {
            sep = s.indexOf('\0');
            if(sep==0)  return;
            
            String t;
            if(sep==-1) {
                t = s;
                s = "";
            } else {
                t = s.substring(0,sep);
                s = s.substring(sep+1);
            }

            sep = t.indexOf('=');
            envVars.put(t.substring(0,sep),t.substring(sep+1));
        }
    }

    private static final Comparator<String> CASE_INSENSITIVE_COMPARATOR = new Comparator<String>() {
        public int compare(String o1, String o2) {
            return o1.toUpperCase().compareTo(o2.toUpperCase());
        }
    };
}
