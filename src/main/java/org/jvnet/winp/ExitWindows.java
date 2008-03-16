package org.jvnet.winp;

/**
 * Exit Windows.
 * @author Kohsuke Kawaguchi
 */
public class ExitWindows {
    private ExitWindows() {}
    
    /**
     * Logs off the current user.
     */
    public static void logOff(Flag f) {
        Native.exitWindowsEx(0/*EWX_LOGOFF*/|f.value,0);
    }

    /**
     * Shuts down the machine.
     */
    public static void powerOff(Flag f) {
        Native.exitWindowsEx(8/*EWX_LOGOFF*/|f.value,0);
    }

    /**
     * Reboots the machine.
     */
    public static void reboot(Flag f) {
        Native.exitWindowsEx(2/*EWX_LOGOFF*/|f.value,0);
    }

    /**
     * Flags to control the behavior of ExitWindows. 
     */
    public enum Flag {
        NONE(0), FORCE(0x4), FORCEIFHUNG(0x10);

        Flag(int value) {
            this.value = value;
        }

        final int value;
    }
}
