package org.jvnet.winp;

/**
 * @author Kohsuke Kawaguchi
 */
public enum Priority {
    IDLE(0x40),
    BELOW_NORMAL(0x4000),
    NORMAL(0x20),
    ABOVE_NORMAL(0x8000),
    HIGH(0x80),
    REALTIME(0x100);

    /*package*/ final int value;

    Priority(int value) {
        this.value = value;
    }
}
