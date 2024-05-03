/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.; 2022 JetBrains s.r.o.
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
package org.jvnet.winp.profiling;

import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.winp.WinProcess;
import org.jvnet.winp.util.ExecutablePlatform;
import org.jvnet.winp.util.ProcessSpawningTest;
import org.jvnet.winp.util.TestHelper;

/**
 * Runs profiling for a single ARM64 application.
 */
public class ProfileSingleEnvVars_ARM64 extends ProcessSpawningTest {

    @Before
    public void assumeArm64() {
        TestHelper.assumeIsArm64Host();
    }

    @Test
    public void doProfile() throws Exception {
        File executable = getTestAppExecutable(ExecutablePlatform.ARM64);
        Process proc = spawnProcess(false, false, executable.getAbsolutePath());
        WinProcess wp = new WinProcess(proc);
        wp.getEnvironmentVariables();
        killSpawnedProcess();
    }
}
