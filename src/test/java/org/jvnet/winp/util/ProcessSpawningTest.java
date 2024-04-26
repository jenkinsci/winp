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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.File;
import java.io.IOException;
import org.junit.After;
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
        if (spawnedProcess != null && spawnedProcess.isAlive()) {
            System.err.println("Killing process " + spawnedProcess.toString());
            spawnedProcess.destroyForcibly();
        }
        
        spawnedProcess = null;
    }
    
    protected Process spawnProcess(String ... command) throws AssertionError, InterruptedException, IOException {
        return spawnProcess(true, true, command);
    }
    
    public Process spawnProcess(boolean delayAfterCreate, boolean spotcheckProcess, String ... command) throws AssertionError, InterruptedException, IOException {
        assertTrue("Command is undefined", command.length >= 1);
        assertNull("Test implementation error: The process has been already spawned", spawnedProcess);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("TEST", "foobar");
        spawnedProcess = pb.start();     
        
        if (delayAfterCreate) {
            Thread.sleep(100); // Try to give the process a little time to start or getting the command line fails randomly
        }
        
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
    
    protected static File getTestAppExecutable(ExecutablePlatform executablePlatform) {
        final String configuration = System.getProperty("native.configuration", "Release");
        final String executable;
        switch (executablePlatform) {
            case X64:
                executable = "native_test/testapp/x64/" + configuration + "/testapp.x64.exe";
                break;
            case X86:
                executable = "native_test/testapp/Win32/" + configuration + "/testapp.exe";
                break;
            default:
                throw new IllegalArgumentException("Unsupported platform: " + executablePlatform);
        }
        return new File(executable);
    }

}
