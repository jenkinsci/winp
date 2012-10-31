import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.winp.WinProcess;
import org.jvnet.winp.WinpException;

/**
 * @author Kohsuke Kawaguchi
 */
public class TheTest extends Assert {

    @Test
    public void testEnumProcesses() {
        for (WinProcess p : WinProcess.all()) {
            System.out.print(p.getPid());
            System.out.print(' ');
        }
        System.out.println();
    }

    @Test
    public void testGetCommandLine() {
        int failed = 0;
        int total = 0;
        for (WinProcess p : WinProcess.all()) {
            if (p.getPid() < 10) {
                continue;
            }

            try {
                ++total;
                System.out.println(p.getPid() + ": " + p.getCommandLine());
            } catch (WinpException e) {
                // Note: On newer (is it really still "newer" to say Vista and higher? In 2012?) versions of
                // Windows, "protected processes" have been introduced. Unless this test is run with full
                // administrative privileges (and we're not that stupid, are we?), it's bound to run across
                // some processes in the full list it's not allowed to tinker with, even if we skip past the
                // first ten. So, if the error code is ERROR_ACCESS_DENIED (see winerror.h), ignore it
                ++failed;
                if (e.getWin32ErrorCode() != 5) { //ERROR_ACCESS_DENIED
                    // On Vista and higher, the most common error here is 299, ERROR_PARTIAL_COPY. A bit of
                    // research online seems to suggest that's related to how those versions of Windows use
                    // randomized memory locations for process's
                    System.out.println("Unexpected failure getting command line for process " + p.getPid() +
                            ": (" + e.getWin32ErrorCode() + ") " + e.getMessage());
                }
            }
        }
        System.out.println("Failed to get command lines for " + failed + " of " + total + " processes");
    }

    @Test(expected = WinpException.class)
    public void testErrorHandling() {
        new WinProcess(0).getEnvironmentVariables();
    }

    @Test
    public void testKill() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("notepad");
        pb.environment().put("TEST", "foobar");
        Process p = pb.start();
        Thread.sleep(100); // Try to give the process a little time to start or getting the command line fails randomly

        WinProcess wp = new WinProcess(p);
        System.out.println("pid=" + wp.getPid());

        System.out.println(wp.getCommandLine());
        assertTrue(wp.getCommandLine().contains("notepad"));

        System.out.println(wp.getEnvironmentVariables());
        assertEquals("foobar", wp.getEnvironmentVariables().get("TEST"));

        Thread.sleep(100);
        wp.killRecursively();
    }

    @BeforeClass
    public static void enableDebug() {
        WinProcess.enableDebugPrivilege();
    }
}
