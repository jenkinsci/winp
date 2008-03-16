package org.jvnet.winp;

/**
 * Represents the constants for process execution priority.
 * 
 * @author Kohsuke Kawaguchi
 */
public abstract class Priority {
    public static final int IDLE = 0x40;
    public static final int BELOW_NORMAL = 0x4000;
    public static final int NORMAL = 0x20;
    public static final int ABOVE_NORMAL = 0x8000;
    public static final int HIGH = 0x80;
    public static final int REALTIME = 0x100;
}
