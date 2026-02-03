import org.jvnet.winp.WinProcess;

/**
 * @author Kohsuke Kawaguchi
 */
public class Main2 {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("notepad");
        Process p = pb.start();
        Thread.sleep(3000);
        new WinProcess(p).killRecursively();
    }
}
