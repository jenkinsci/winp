package org.jvnet.winp;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Test driver class
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
public class Main {
    public static void main(String[] args) {
        WinProcess.enableDebugPrivilege();

        if (args[0].equals("list")) {
            for (WinProcess p : WinProcess.all()) {
                print(p);
            }
        } else {
            WinProcess p = new WinProcess(Integer.parseInt(args[0]));
            print(p);
            if (args.length>1) {
                if (args[1].equals("kill")) {
                    p.kill();
                }
                if (args[1].equals("kill-recursive")) {
                    p.killRecursively();
                }
            }
        }
    }

    private static void print(WinProcess p) {
        System.out.printf("%4d : %s%n", p.getPid(),p.getCommandLine());
        System.out.printf("  %s%n",p.getEnvironmentVariables());
    }
}
