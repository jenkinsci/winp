import junit.framework.TestCase;
import org.jvnet.winp.WinProcess;

/**
 * @author Kohsuke Kawaguchi
 */
public class TheTest extends TestCase {
    public void testEnumProcesses() {
        for (WinProcess p : WinProcess.all()) {
            System.out.print(p.getPid());
            System.out.print(' ');
        }
        System.out.println();
    }
}
