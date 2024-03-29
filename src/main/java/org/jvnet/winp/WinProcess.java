package org.jvnet.winp;

import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;

/**
 * Represents a Windows process.
 *
 * <p>
 * On Windows, there are several system pseudo-processes,
 * for which many of the getter invocations would fail.
 * This includes "system idle process" (which always seem to
 * have PID=0) and "System" (which always seem to have PID=4)
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
        long pidLong = proc.pid();
        this.pid = (int) pidLong;
        if (this.pid != pidLong) {
            throw new IllegalArgumentException("Out of range: " + pidLong);
        }
    }

    @Override
    public String toString() {
        return "WinProcess pid#" + pid + ", command line: " +  (commandline != null ? commandline : "not ready");
    }
    
    /**
     * Gets the process ID.
     */
    public int getPid() {
        return pid;
    }

    /**
     * Kills this process and all the descendant processes that
     * this process launched. 
     */
    public void killRecursively() {
        if (LOGGER.isLoggable(FINE))
            LOGGER.fine(String.format("Attempting to recursively kill pid=%d (%s)",pid,getCommandLine()));
        Native.kill(pid,true);
    }

    public void kill() {
        if (LOGGER.isLoggable(FINE))
            LOGGER.fine(String.format("Attempting to kill pid=%d (%s)",pid,getCommandLine()));
        Native.kill(pid,false);
    }

    /**
     * Sends Ctrl+C to the process.
     * Due to the Windows platform specifics, this execution will spawn a separate thread to deliver the signal.
     * This process is expected to be executed within a 5-second timeout.
     * @return {@code true} if the signal was delivered successfully
     * @throws WinpException Execution error
     */
    @CheckReturnValue
    public boolean sendCtrlC() throws WinpException {
        if (LOGGER.isLoggable(FINE))
            LOGGER.fine(String.format("Attempting to send CTRL+C to pid=%d (%s)",pid,getCommandLine()));
        return Native.sendCtrlC(pid);
    }

    public boolean isRunning() {
        return Native.isProcessRunning(pid);
    }

    public boolean isCriticalProcess() {
        return Native.isCriticalProcess(pid);
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
     *      If Winp fails to obtain the command line. 
     *      The process may be dead or there is not enough security privileges.
     */
    public synchronized String getCommandLine() {
        if(commandline == null) {
            parseCmdLine();
        }
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
     *      If Winp fails to obtain the environment variables.
     *      The process may be dead or there is not enough security privileges.
     */
    public synchronized TreeMap<String,String> getEnvironmentVariables() {
        if(envVars==null) {
            parseCmdLineAndEnvVars();
        }
        return envVars;
    }

    private void parseCmdLine() throws WinpException {
        String s = Native.getCmdLine(pid);
        if(s == null) {
            throw new WinpException("Failed to obtain command line for PID = " + pid); 
        }
        commandline = s;
    }
    
    private void parseCmdLineAndEnvVars() {
        String s = Native.getCmdLineAndEnvVars(pid);
        if(s==null)
            throw new WinpException("Failed to obtain for PID="+pid);
        // a double null indicates the end of the envvars (but the string can be longer!) so truncate here.
        int end = s.indexOf("\0\0");
        if (end != -1) {
            s = s.substring(0, end);
        }
        StringTokenizer st = new StringTokenizer(s, "\0", false);
        commandline = st.nextToken();
        envVars = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        while (st.hasMoreElements()) {
            final String kv = st.nextToken();
            int sep = kv.indexOf('=');
            if  (sep!=-1) { // be defensive. not exactly sure when this happens, but see HUDSON-4034
                envVars.put(kv.substring(0, sep), kv.substring(sep + 1));
            }
        }
    }

    /**
     * Enumerates all the processes in the system.
     *
     * @throws WinpException
     *      If the enumeration fails.
     * @return
     *      Never null.
     */
    public static Iterable<WinProcess> all() {
        return new Iterable<>() {
            @Override
            public Iterator<WinProcess> iterator() {
                return new Iterator<>() {
                    private int pos=0;
                    private int[] pids = new int[256];
                    private int total;

                    {
                        while(true) {
                            total = Native.enumProcesses(pids);
                            if(total==0)
                                throw new WinpException("Failed to enumerate processes");
                            if(total<pids.length)
                                break;
                            pids = new int[pids.length*2];
                        }
                    }

                    @Override
                    public boolean hasNext() {
                        return pos<total;
                    }

                    @Override
                    public WinProcess next() {
                        return new WinProcess(pids[pos++]);
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    /**
     * Elevates the security privilege of this process
     * so that we can obtain information about processes
     * owned by other users.
     *
     * <p>
     * Otherwise some of the getter methods may fail
     * with {@link WinpException} due to access denied error.
     */
    public static void enableDebugPrivilege() {
        Native.enableDebugPrivilege();
    }

    private static final Logger LOGGER = Logger.getLogger(WinProcess.class.getName());
}
