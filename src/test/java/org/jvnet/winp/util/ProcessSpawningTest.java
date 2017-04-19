/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.winp.util;

import java.io.File;
import java.io.IOException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import static org.hamcrest.CoreMatchers.containsString;
import org.junit.After;
import org.junit.Assert;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import org.jvnet.winp.WinProcess;

/**
 * Base class for tests, which spawn processes.
 * This class automatically kills runaway ones.
 * @author Oleg Nenashev
 */
public class ProcessSpawningTest extends NativeWinpTest {
    
    @CheckForNull
    private Process spawnedProcess = null;
    
    @After
    public void killSpawnedProcess() {
        if (spawnedProcess != null && isAlive(spawnedProcess)) {
            System.err.println("Killing process " + spawnedProcess.toString());
            //TODO: destroyForcibly() in Java8
            spawnedProcess.destroy();
        }
    }
       
    /**
     * Spawns test process, which is guaranteed to be a 32-bit one.
     */
    private Process spawnTestApp32() throws AssertionError, InterruptedException, IOException {
        return spawnProcess("C:\\Users\\Oleg\\Documents\\jenkins\\windows\\winp\\native\\Release\\testapp32.exe");
    }
    
    public Process spawnProcess(String ... command) throws AssertionError, InterruptedException, IOException {
        return spawnProcess(true, command);
    }
    
    public Process spawnProcess(boolean spotcheckProcess, String ... command) throws AssertionError, InterruptedException, IOException {
        assertTrue("Command is undefined", command.length >= 1);
        assertNull("Test implementation error: The process has been already spawned", spawnedProcess);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("TEST", "foobar");
        spawnedProcess = pb.start();     
        Thread.sleep(100); // Try to give the process a little time to start or getting the command line fails randomly

        // Asserts the process status
        if (spotcheckProcess) {
            WinProcess wp = new WinProcess(spawnedProcess);
            System.out.println("pid=" + wp.getPid());
            assertThat("Failed to determine the command line of the running process", 
                    wp.getCommandLine(), containsString(command[0]));
            assertTrue("Failed to read Environment Variables, no PATH discovered",
                    wp.getEnvironmentVariables().containsKey("PATH"));
        }
        
        return spawnedProcess;
    }
    
    //TODO: replace by Process#isAlive() in Java8
    public static boolean isAlive(@Nonnull Process proc) {
        try {
            int exitCode = proc.exitValue();
            return false;
        } catch (IllegalThreadStateException ex) {
           return true;
        }
    }
    
    protected static File getTestAppExecutable(ExecutablePlatform executablePlatform) {
        final String executable;
        switch (executablePlatform) {
            case X64:
                executable = "native_test/testapp/x64/Release/testapp.exe";
                break;
            case X86:
                executable = "native_test/testapp/Win32/Release/testapp.exe";
                break;
            default:
                Assert.fail("Unsupported platform: " + executablePlatform);
                throw new IllegalStateException();
        }
        return new File(executable);
    }

}
