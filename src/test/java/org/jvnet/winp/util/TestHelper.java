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

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import org.hamcrest.core.StringContains;
import org.junit.Assume;

/**
 * Test helpers for the WinP library.
 * @author Oleg Nenashev
 */
public class TestHelper {
    
    /**
     * Checks if current system is Windows and skips the test otherwise.
     */
    public static void assumeIsWindows() {
        Assume.assumeThat("The test utilizes the native WinP Library. It can be executed on Windows only.", 
                System.getProperty("os.name"), StringContains.containsString("Windows"));
    }
    
    /**
     * Checks if current system is 64bit and skips the test otherwise.
     */
    public static void assumeIs64BitHost() {
        Assume.assumeThat("This test can run ony on 64-bit platforms.", 
                System.getProperty("sun.arch.data.model"), equalTo("64"));
    }

    /**
     * Checks if current system may run ARM64 binaries and skips the test otherwise.
     */
    public static void assumeIsArm64Host() {
        Assume.assumeThat("This test can run ony on ARM64-compatible platforms.",
                System.getProperty("os.arch"), anyOf(equalTo("aarch64"), equalTo("arm64")));
    }

    public static boolean is64BitJVM() {
        return "64".equals(System.getProperty("sun.arch.data.model"));
    }
    
    public static boolean is32BitJVM() {
        return "32".equals(System.getProperty("sun.arch.data.model"));
    }
}
