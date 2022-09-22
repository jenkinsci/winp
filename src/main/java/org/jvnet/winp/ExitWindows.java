package org.jvnet.winp;

/**
 * Exit Windows.
 * @author Kohsuke Kawaguchi
 */
public class ExitWindows {

    // ExitWindowsEx function exit codes.
    // Source&documentation: https://msdn.microsoft.com/en-us/en-en/library/windows/desktop/aa376868(v=vs.85).aspx
    private static final int EWX_FORCE = 4;
    private static final int EWX_LOGOFF = 0;
    private static final int EWX_POWEROFF = 8;
    private static final int EWX_REBOOT = 2;
    //EWX_FORCEIFHUNG is only available for _WIN32_WINNT >= 0x0500 (Windows 2000 or higher)
    private static final int EWX_FORCEIFHUNG = 10;

    private ExitWindows() {}
    
    /**
     * Logs off the current user.
     */
    public static void logOff(Flag f) {
        exit(EWX_LOGOFF, f);
    }

    /**
     * Shuts down the machine.
     */
    public static void powerOff(Flag f) {
        exit(EWX_POWEROFF, f);
    }

    /**
     * Reboots the machine.
     */
    public static void reboot(Flag f) {
        exit(EWX_REBOOT, f);
    }

    private static void exit(int ewxCode, Flag f) {
        Native.exitWindowsEx(ewxCode | f.value, 0);
    }

    /**
     * Flags to control the behavior of ExitWindows. 
     */
    public enum Flag {

        NONE(0),
        FORCE(EWX_FORCE),
        FORCEIFHUNG(EWX_FORCEIFHUNG);

        private final int value;

        Flag(int value) {
            this.value = value;
        }
    }
}
