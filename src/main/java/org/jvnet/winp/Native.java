package org.jvnet.winp;

/**
 * @author Kohsuke Kawaguchi
 */
class Native {
    native static boolean kill(int pid, boolean recursive);
}
