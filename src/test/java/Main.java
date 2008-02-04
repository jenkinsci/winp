import org.jvnet.winp.WinProcess;

/**
 * Test program.
 * @author Kohsuke Kawaguchi
 */
public class Main {
    public static void main(String[] args) {
        new WinProcess(Integer.valueOf(args[0])).killRecursively();
    }
}
