package org.jvnet.winp;

import java.io.IOException;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import org.junit.Assert;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Basic tests of the native library.
 * @author Kohsuke Kawaguchi
 */
public class NativeAPITest extends Assert {

    @Before
    public void runOnWindowsOnly() {
        TestHelper.assumeIsWindows();
    }
    
    @Test
    public void testEnumProcesses() {
        for (WinProcess p : WinProcess.all()) {
            System.out.print(p.getPid());
            System.out.print(' ');
        }
        System.out.println();
    }

    @Test
    public void testCriticalProcess() {
        boolean found=false;
        for (WinProcess p : WinProcess.all()) {
            final String commandLine;
            try {
                commandLine = p.getCommandLine();
            } catch (WinpException e) {
                System.out.println("Cannot determine the process name for process " + p + ": " + e.toString());
                continue;
            }
            
            if (commandLine.contains("csrss")) {
                try {
                    found = true;
                    assertTrue(p.isCriticalProcess());
                    p.kill();   // this should fail (but if the test fails, then we'll see BSoD
                } catch (WinpException e) {
                    System.out.println("Ignoring exception for process " + p + ": " + e.toString());
                    e.printStackTrace(System.out);
                }
            }
        }

        assumeThat("The test was unable to find the csrss process. Likely there is no permission allowing to read the command line", 
                found, equalTo(true));
    }

    @Test
    public void testGetCommandLine() {
        int failed = 0;
        int total = 0;
        
        // Generally this test is unreliable, 299 does not always happen even on Vista+ platforms
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
                    Assert.fail("Unexpected failure getting command line for process " + p.getPid() +
                            ": (" + e.getWin32ErrorCode() + ") " + e.getMessage());
                }
            }
        }
        if (failed != 0) {
            System.out.println("Failed to get command lines for " + failed + " of " + total + " processes");
        }
    }

    @Test(expected = WinpException.class)
    public void testErrorHandling() {
        new WinProcess(0).getEnvironmentVariables();
    }

    @Test
    public void testKill() throws Exception {
        Process p = spawnNotepad();
        
        WinProcess wp = new WinProcess(p);
        System.out.println(wp.getCommandLine());
        assertTrue(wp.getCommandLine().contains("notepad"));

        System.out.println(wp.getEnvironmentVariables());
        assertEquals("foobar", wp.getEnvironmentVariables().get("TEST"));

        Thread.sleep(100);
        wp.killRecursively();
    }
    
    @Test
    public void getCommandLine_shouldNotFailIfTheProcessIsDead() throws Exception {
        Process p = spawnNotepad();
        new WinProcess(p).killRecursively();
        Thread.sleep(1000);
        assertFalse("The process has not been stopped yet", p.isAlive());

        try {
            new WinProcess(p).getCommandLine();
        } catch (WinpException ex) {
            assertThat(ex.getMessage(), containsString("error=299 at envvar-cmdline"));
            return;
        }
        
        Assert.fail("Expected WinpException since the process is killed");
    }
    
    @Test
    public void getEnvironmentVariables_shouldFailIfTheProcessIsDead() throws Exception {
        Process p = spawnNotepad();
        new WinProcess(p).killRecursively();
        Thread.sleep(1000);
        assertFalse("The process has not been stopped yet", p.isAlive());
        
        try {
            new WinProcess(p).getEnvironmentVariables();
        } catch (WinpException ex) {
            assertThat(ex.getMessage(), containsString("error=299 at envvar-cmdline"));
            return;
        }
        
        Assert.fail("Expected WinpException since the process is killed");
    }
    
    /**
     * Starts notepad process with the TEST environment variable.
     */
    private Process spawnNotepad() throws AssertionError, InterruptedException, IOException {
        ProcessBuilder pb = new ProcessBuilder("notepad");
        pb.environment().put("TEST", "foobar");
        Process p = pb.start();
        Thread.sleep(100); // Try to give the process a little time to start or getting the command line fails randomly

        // Asserts the process status
        WinProcess wp = new WinProcess(p);
        System.out.println("pid=" + wp.getPid());
        assertThat("Failed to determine the command line of the running process", 
                wp.getCommandLine(), containsString("notepad"));
        assertTrue("Failed to read Environment Variables, no PATH discovered",
                wp.getEnvironmentVariables().containsKey("PATH"));
        return p;
    }

    @BeforeClass
    public static void enableDebug() {
        WinProcess.enableDebugPrivilege();
    }
}
