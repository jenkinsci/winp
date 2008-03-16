import org.jvnet.winp.WinProcess;

/**
 * @author Kohsuke Kawaguchi
 */
public class Main3 {
    public static void main(String[] args) {
        WinProcess p = new WinProcess(2512);
        System.out.println(p.getCommandLine());
        System.out.println(p.getEnvironmentVariables());
    }
}
