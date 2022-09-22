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
package org.jvnet.winp;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.jvnet.winp.util.ExecutablePlatform;
import org.jvnet.winp.util.ProcessSpawningTest;
import org.jvnet.winp.util.TestHelper;

/**
 * WinP tests, which target platform-dependent logic.
 * These tests will take platform as a parameter though they run on the target platform anyway.
 * Only one of winp.dll and winp-64.dll will be tested.
 * @author Oleg Nenashev
 */
@RunWith(Parameterized.class)
public class PlatformSpecificProcessTest extends ProcessSpawningTest {
    
    private final ExecutablePlatform executablePlatform; 
    
    public PlatformSpecificProcessTest(ExecutablePlatform p) {
        executablePlatform = p;
    }
    
    @Before
    public void verifyTargetPlatform() {
        
        // Run 64bit tests only if the platform supports it
        if (executablePlatform == ExecutablePlatform.X64) {
            TestHelper.assumeIs64BitHost();
        }
        
        File exec = getTestAppExecutable(executablePlatform);
        System.out.println("Target executable: " + exec.getAbsolutePath());
        Assert.assertTrue("Cannot locate the required executable: " + exec.getAbsolutePath(), exec.exists());
    }
    
    @Test
    public void shouldKillProcessCorrectly() throws Exception {
        Process p = spawnTestApp();
        WinProcess wp = new WinProcess(p);
        assertEquals("foobar", wp.getEnvironmentVariables().get("TEST"));

        Thread.sleep(100);
        wp.killRecursively();
    }
    
    @Test
    public void shouldNotBeCritical() throws Exception {
        Process p = spawnTestApp();
        WinProcess wp = new WinProcess(p);
        assertFalse("The spawned process should not be critical to the system", wp.isCriticalProcess());
    }
    
    @Test
    public void getCommandLine_shouldNotFailIfTheProcessIsDead() throws Exception {
        Process p = spawnTestApp();
        WinProcess wp = new WinProcess(p);
        int pid = wp.getPid();
        wp.killRecursively();
        Thread.sleep(1000);
        assertFalse("The process has not been stopped yet", p.isAlive());

        WinpException e = assertThrows(
                "Expected WinpException since the process is killed",
                WinpException.class,
                () -> new WinProcess(p).getCommandLine());
        assertThat(
                e.getMessage(),
                containsString("Process with pid=" + pid + " has already stopped. Exit code is -1"));
        assertThat(
                e.getWin32ErrorCode(),
                equalTo(UserErrorType.PROCESS_IS_NOT_RUNNING.getSystemErrorCode()));
    }
    
    @Test
    public void getEnvironmentVariables_shouldFailIfTheProcessIsDead() throws Exception {
        Process p = spawnTestApp();
        WinProcess wp = new WinProcess(p);
        int pid = wp.getPid();
        wp.killRecursively();
        Thread.sleep(1000);
        assertFalse("The process has not been stopped yet", p.isAlive());

        WinpException e = assertThrows(
                "Expected WinpException since the process is killed",
                WinpException.class,
                () -> new WinProcess(p).getEnvironmentVariables());
        assertThat(
                e.getMessage(),
                containsString("Process with pid=" + pid + " has already stopped. Exit code is -1"));
        assertThat(
                e.getWin32ErrorCode(),
                equalTo(UserErrorType.PROCESS_IS_NOT_RUNNING.getSystemErrorCode()));
    }
    
    private Process spawnTestApp() throws IOException, InterruptedException {
        return spawnProcess(getTestAppExecutable(executablePlatform).getAbsolutePath());
    }
    
    private String getExpectedPEBName(boolean processIsRunning) {
        // We cannot read Wow64 Process info from the terminated process, hence PEB32 structure won't be discovered
        return executablePlatform == ExecutablePlatform.X86 && processIsRunning ? "PEB32" : "PEB";
    }
    
    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] { {ExecutablePlatform.X64}, {ExecutablePlatform.X86}});  
    }
}
