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

import org.hamcrest.core.StringContains;
import org.junit.Assume;

/**
 * Test helpers for the WinP library
 * @author Oleg Nenashev
 */
class TestHelper {
    
    public static boolean isWindows() throws AssertionError {
        String property = System.getProperty("os.name");
        if (property == null || property.trim().isEmpty()) {
            return false;
        }
        
        return property.contains("Windows");
    }
    
    public static void assumeIsWindows() {
        Assume.assumeThat("The test utilizes the native WinP Library. It can be executed on Windows only.", 
                System.getProperty("os.name"), StringContains.containsString("Windows"));
    }
    
}
