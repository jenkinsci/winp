package org.jvnet.winp;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

import java.io.IOException;
import org.junit.Assert;
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
                System.out.println("Cannot determine the process name for process " + p + ": " + e);
                continue;
            }
            
            if (commandLine.contains("csrss")) {
                try {
                    found = true;
                    assertTrue(p.isCriticalProcess());
                    p.kill();   // this should fail (but if the test fails, then we'll see BSoD
                } catch (WinpException e) {
                    System.out.println("Ignoring exception for process " + p + ": " + e);
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
                
                // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--500-999-
                // This special error code was found for a privileged process on
                // windows-arm64, coming from Qualcomm driver. Ignore this.
                if (errorCode == 998) { //ERROR_NOACCESS
                    continue;
                }

                // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
                // This happens for some privileged process, skip error.
                if (errorCode == 299) { //ERROR_PARTIAL_COPY
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
        Process p = spawnProcess("PING", "-n", "20", "127.0.0.1"); // run for 20 secs
        
        WinProcess wp = new WinProcess(p);
        assertTrue("Process is not running: " + p, wp.isRunning());
        
        // send Ctrl+C, then wait for a max of 4 secs
        boolean sent = wp.sendCtrlC();
        assertTrue("Failed to send the Ctrl+C signal to the process: " + p, sent);
        for (int i = 0; i < 40; ++i) {
            if (!wp.isRunning()) {
                break;
            }
            Thread.sleep(100);
        }

        assertFalse("Process has not been terminated after Ctrl+C", wp.isRunning());
        wp.killRecursively();
    }

    @Test
    public void testSendCtrlC_nonExistentPID() throws Exception {
        WinProcess wp = new WinProcess(Integer.MAX_VALUE);
        assertFalse("Process is running when it should not: " + wp, wp.isRunning());

        // send Ctrl+C, then wait for a max of 4 secs
        assertThrows(WinpException.class, wp::sendCtrlC);
    }

    @Test
    public void shouldFailForNonExistentProcess() {
        int nonExistentPid = Integer.MAX_VALUE;
        WinpException e = assertThrows(
                "Expected WinpException due to the non-existent process",
                WinpException.class,
                () -> new WinProcess(nonExistentPid).getCommandLine());
        assertThat(e.getMessage(), containsString("Failed to open process"));
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
