package org.jvnet.winp;

import java.net.URL;
import java.net.URLDecoder;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Functions defined in the DLL.
 * 
 * @author Kohsuke Kawaguchi
 */
class Native {
    native static boolean kill(int pid, boolean recursive);
    native static int setPriority(int pid, int value);
    native static int getProcessId(int handle);
    native static boolean exitWindowsEx(int flags,int reasonCode);

    /**
     * Gets the command line and environment variables of the process
     * identified by the process ID.
     *
     * <p>
     * To simplify the JNI side, the resulting string is structured to
     * "cmdlineargs\0env1=val1\0env2=val2\0..."
     */
    native static String getCmdLineAndEnvVars(int pid);

    private static final Logger LOGGER = Logger.getLogger(Native.class.getName());

    static {
        load();
    }

    private static void load() {
        // try loading winp.dll in the same directory as winp.jar
        final URL res = Native.class.getClassLoader().getResource("winp.dll");
        String url = res.toExternalForm();
        if(url.startsWith("jar:")) {
            int idx = url.lastIndexOf('!');
            String filePortion = url.substring(4,idx);
            while(filePortion.startsWith("/"))
                filePortion = filePortion.substring(1);

            if(filePortion.startsWith("file:/")) {
                filePortion = filePortion.substring(6);
                if(filePortion.startsWith("//"))
                    filePortion = filePortion.substring(2);
                filePortion = URLDecoder.decode(filePortion);
                File jarFile = new File(filePortion);
                File dllFile = new File(jarFile.getParentFile(),"winp.dll");
                if(!dllFile.exists() || jarFile.lastModified()>dllFile.lastModified()) {
                    // try to extract from within the jar
                    try {
                        copyStream(
                            res.openStream(),
                            new FileOutputStream(dllFile));
                        dllFile.setLastModified(jarFile.lastModified());
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to write winp.dll", e);
                    }
                }

                try {
                    System.load(dllFile.getPath());
                } catch (LinkageError e) {
                    // see http://forum.java.sun.com/thread.jspa?threadID=618431&messageID=3462466
                    // if another ClassLoader loaded winp, loading may fail
                    // even if the classloader is no longer in use, due to GC delay.
                    // this is a poor attempt to see if we can force GC early on.
                    for( int i=0; i<5; i++ ) {
                        try {
                            System.gc();
                            System.gc();
                            Thread.sleep(1000);
                            System.load(dllFile.getPath());
                            return; // succeedef
                        } catch (InterruptedException x) {
                            throw e; // throw the original exception
                        } catch (LinkageError x) {
                            // retry
                        }
                    }
                    // still failing after retry.
                    throw e;
                }
                return;
            }
        }
        if(url.startsWith("file:")) {
            // during debug
            String p = res.getPath();
            while(p.startsWith("/"))    p=p.substring(1);
            System.load(p);
            return;
        }

        // we don't know where winp.dll is, so let's just hope the user put it somewhere
        try {
            // load the native part of the code.
            // first try java.library.path
            System.loadLibrary("winp");
        } catch( Throwable cause ) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError("Unable to load winp.dll");
            error.initCause(cause);
            throw error;
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buf = new byte[8192];
            int len;
            while((len=in.read(buf))>=0)
                out.write(buf,0,len);
        } finally {
            in.close();
            out.close();
        }
    }
}
