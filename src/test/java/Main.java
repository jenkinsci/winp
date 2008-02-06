import org.jvnet.winp.WinProcess;
import org.jvnet.winp.Priority;

/**
 * Test program.
 * @author Kohsuke Kawaguchi
 */
public class Main {
    public static void main(String[] args) {
        WinProcess p = new WinProcess(Integer.parseInt(args[0]));
        // p.killRecursively();
        p.setPriority(Priority.BELOW_NORMAL);
    }
}
