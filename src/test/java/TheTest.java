import junit.framework.TestCase;
import org.jvnet.winp.WinProcess;
import org.jvnet.winp.WinpException;

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

    public void testGetCommandLine() {
        WinProcess.enableDebugPrivilege();
        for (WinProcess p : WinProcess.all()) {
            if(p.getPid()<10)   continue;
            System.out.println(p.getCommandLine());
        }
    }

    public void testErrorHandling() {
        try {
            new WinProcess(0).getEnvironmentVariables();
            fail();
        } catch (WinpException e) {
            // exception expected
            e.printStackTrace();
        }
    }
}
