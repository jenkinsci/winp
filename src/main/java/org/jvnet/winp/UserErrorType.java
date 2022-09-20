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

/**
 * User-scope error codes in WinP. 
 * @author Oleg Nenashev
 */
enum UserErrorType {
    PROCESS_IS_NOT_RUNNING(1),
    QUERY_64BIT_PROC_FROM_32BIT(2);
    
    private final int shortCode;
    
    UserErrorType(/* @java.annotation.Nonnegative */ int shortCode) {
        this.shortCode = shortCode;
    }

    public int getShortCode() {
        return shortCode;
    }
    
    public int getSystemErrorCode() {
        return toSystemErrorCode(shortCode);
    }
    
    public boolean matches(int systemCode) {
        return systemCode == toSystemErrorCode(shortCode);
    }
    
    // TODO: refactor to Enum in the API
    private static int toSystemErrorCode(int minor) {
        return 0x10000000 + minor;
    }
}
