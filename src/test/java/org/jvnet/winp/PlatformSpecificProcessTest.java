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
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;
import org.jvnet.winp.util.ExecutablePlatform;
import org.jvnet.winp.util.ProcessSpawningTest;
import org.jvnet.winp.util.TestHelper;

/**
 * WinP tests, which target platform-dependent logic.
 * These tests will take platform as a parameter though they run on the target platform anyway.
 * Only one of winp.dll and winp-64.dll will be tested.
 * @author Oleg Nenashev
 */
@ParameterizedClass
@EnumSource(ExecutablePlatform.class)
class PlatformSpecificProcessTest extends ProcessSpawningTest {

    private static final Set<String> ARCHITECTURE_DEPENDANT_ENVIRONMENT_VARS;

    static {
        ARCHITECTURE_DEPENDANT_ENVIRONMENT_VARS = new HashSet<>();
        ARCHITECTURE_DEPENDANT_ENVIRONMENT_VARS.add("PROCESSOR_ARCHITEW6432");
        ARCHITECTURE_DEPENDANT_ENVIRONMENT_VARS.add("PROCESSOR_ARCHITECTURE");
        ARCHITECTURE_DEPENDANT_ENVIRONMENT_VARS.add("PROGRAMFILES");
        ARCHITECTURE_DEPENDANT_ENVIRONMENT_VARS.add("COMMONPROGRAMFILES");
    }

    @Parameter(0)
    private ExecutablePlatform executablePlatform;

    @BeforeEach
    void beforeEach() {
        // Run 64bit tests only if the platform supports it
        if (executablePlatform == ExecutablePlatform.X64) {
            TestHelper.assumeIs64BitHost();
        }
        
        File exec = getTestAppExecutable(executablePlatform);
        System.out.println("Target executable: " + exec.getAbsolutePath());
        assertTrue(exec.exists(), "Cannot locate the required executable: " + exec.getAbsolutePath());
    }

    @Test
    void shouldKillProcessCorrectly() throws Exception {
        Process p = spawnTestApp();
        WinProcess wp = new WinProcess(p);
        assertEquals("foobar", wp.getEnvironmentVariables().get("TEST"));

        Thread.sleep(100);
        wp.killRecursively();
    }

    @Test
    void shouldNotBeCritical() throws Exception {
        Process p = spawnTestApp();
        WinProcess wp = new WinProcess(p);
        assertFalse(wp.isCriticalProcess(), "The spawned process should not be critical to the system");
    }

    @Test
    void getCommandLine_shouldNotFailIfTheProcessIsDead() throws Exception {
        Process p = spawnTestApp();
        WinProcess wp = new WinProcess(p);
        int pid = wp.getPid();
        wp.killRecursively();
        Thread.sleep(1000);
        assertFalse(p.isAlive(), "The process has not been stopped yet");

        WinpException e = assertThrows(
                WinpException.class,
                () -> new WinProcess(p).getCommandLine(),
                "Expected WinpException since the process is killed");
        assertThat(
                e.getMessage(),
                containsString("Process with pid=" + pid + " has already stopped. Exit code is -1"));
        assertThat(
                e.getWin32ErrorCode(),
                equalTo(UserErrorType.PROCESS_IS_NOT_RUNNING.getSystemErrorCode()));
    }

    @Test
    void getEnvironmentVariables_shouldReturnCorrectValues() throws Exception {
        Process p = spawnTestApp();
        WinProcess wp = new WinProcess(p);
        // spawned processes should inherit our environment variables
        Map<String, String> inheritedEnv = System.getenv();
        Map<String, String> processEnv = wp.getEnvironmentVariables();

        // environment variable that start with = is just some funky stuff!
        // and there are some that start with "=" that won't show up in set e.g. =ExitCode =CLINK.SCRIPTS
        for (Map.Entry<String, String> entry : inheritedEnv.entrySet()) {
            if (!(entry.getKey().isEmpty() || entry.getKey().startsWith("=") || // :-o  special
                    ARCHITECTURE_DEPENDANT_ENVIRONMENT_VARS.contains(entry.getKey())))  {
                assertThat(processEnv, hasEntry(entry.getKey(), entry.getValue()));
            }
        }
        // the extra env added by spawnTestApp
        assertThat(processEnv, hasEntry("TEST", "foobar"));

        // what remains?
        Map<String, String> remaining = new HashMap<>();
        for (Map.Entry<String, String>  kv : processEnv.entrySet()) {
            if (! (inheritedEnv.containsKey(kv.getKey()) || kv.getKey().equals("TEST") || kv.getKey().isEmpty()
                    || kv.getKey().startsWith("="))) {
                // some vars are changed by windows depending on if you are a 32bit process running in a 64 bit os or 64 on 64.
                // just filter those out
                if (!ARCHITECTURE_DEPENDANT_ENVIRONMENT_VARS.contains(kv.getKey())) {
                    remaining.put(kv.getKey(), kv.getValue());
                }
            }
        }
        assertThat(remaining, anEmptyMap());
    }

    @Test
    void getEnvironmentVariables_shouldFailIfTheProcessIsDead() throws Exception {
        Process p = spawnTestApp();
        WinProcess wp = new WinProcess(p);
        int pid = wp.getPid();
        wp.killRecursively();
        Thread.sleep(1000);
        assertFalse(p.isAlive(), "The process has not been stopped yet");

        WinpException e = assertThrows(
                WinpException.class,
                () -> new WinProcess(p).getEnvironmentVariables(),
                "Expected WinpException since the process is killed");
        assertThat(
                e.getMessage(),
                containsString("Process with pid=" + pid + " has already stopped. Exit code is -1"));
        assertThat(
                e.getWin32ErrorCode(),
                equalTo(UserErrorType.PROCESS_IS_NOT_RUNNING.getSystemErrorCode()));
    }
    
    private Process spawnTestApp() throws Exception {
        return spawnProcess(getTestAppExecutable(executablePlatform).getAbsolutePath());
    }
    
    private String getExpectedPEBName(boolean processIsRunning) {
        // We cannot read Wow64 Process info from the terminated process, hence PEB32 structure won't be discovered
        return executablePlatform == ExecutablePlatform.X86 && processIsRunning ? "PEB32" : "PEB";
    }
}
