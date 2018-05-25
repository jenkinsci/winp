package org.jvnet.winp;

import java.io.IOException;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import org.hamcrest.core.StringContains;
import org.junit.Assert;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;
import org.junit.Test;
import org.jvnet.winp.util.ProcessSpawningTest;
import org.jvnet.winp.util.TestHelper;

/**
 * Basic tests of the native library.
 * @author Kohsuke Kawaguchi
 */
public class NativeAPITest extends ProcessSpawningTest {

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
                
                int errorCode = e.getWin32ErrorCode();
                if (errorCode == 5) { //ERROR_ACCESS_DENIED
                    continue;
                }
                
                if (UserErrorType.QUERY_64BIT_PROC_FROM_32BIT.matches(errorCode) && TestHelper.is32BitJVM()) {
                    // Skipping warnings for 64 bit processes when running in 32bit Java
                    continue;
                }
                
                if (UserErrorType.PROCESS_IS_NOT_RUNNING.matches(errorCode)) {
                    // Sometimes the process may just dies till we get to its check
                    continue;
                }
                
                // On Vista and higher, the most common error here is 299, ERROR_PARTIAL_COPY. A bit of
                // research online seems to suggest that's related to how those versions of Windows use
                // randomized memory locations for process's
                Assert.fail("Unexpected failure getting command line for process " + p.getPid() +
                            ": (" + e.getWin32ErrorCode() + ") " + e.getMessage());
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
        assertTrue(wp.getCommandLine().contains("notepad"));
        assertEquals("foobar", wp.getEnvironmentVariables().get("TEST"));

        Thread.sleep(100);
        wp.killRecursively();
    }

    @Test
    public void testPingAsDelay() throws Exception {
        Process p = spawnProcess("PING", "-n", "10", "127.0.0.1"); // run for 10 secs
        
        WinProcess wp = new WinProcess(p);
        assertTrue(wp.isRunning());
        
        Thread.sleep(4000); // just wait, don't send Ctrl+C

        assertTrue(wp.isRunning());
        wp.killRecursively();
    }

    @Test
    public void testSendCtrlC() throws Exception {
        Process p = spawnProcess("PING", "-n", "10", "127.0.0.1"); // run for 10 secs
        
        WinProcess wp = new WinProcess(p);
        assertTrue(wp.isRunning());
        
        // send Ctrl+C, then wait for a max of 4 secs
        boolean sent = wp.sendCtrlC();
        assertTrue(sent);
        for (int i = 0; i < 40; ++i) {
            if (!wp.isRunning()) {
                break;
            }
            Thread.sleep(100);
        }

        assertTrue(!wp.isRunning());
        wp.killRecursively();
    }

    @Test
    public void shouldFailForNonExistentProcess() {
        int nonExistentPid = Integer.MAX_VALUE;
        try {
            new WinProcess(nonExistentPid).getCommandLine();
        } catch(WinpException ex) {
            assertThat(ex.getMessage(), containsString("Failed to open process"));
            return;
        }
        Assert.fail("Expected WinpException due to the non-existent process");
    }
    
    /**
     * Starts notepad process with the TEST environment variable.
     * Notepad process may be either 64bit or 32bit depending on the OS Platform.
     * 
     */
    private Process spawnNotepad() throws AssertionError, InterruptedException, IOException {
        return spawnProcess("notepad");
    }

}
