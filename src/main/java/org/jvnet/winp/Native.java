package org.jvnet.winp;

import java.net.URL;
import java.net.URLDecoder;
import java.net.URISyntaxException;
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

    /**
     * Enumerate all processes.
     *
     * @param result
     *      Receives process IDs.
     * @return
     *      The number of processes obtained. If this is the same
     *      as <tt>result.length</tt>, a bigger buffer was necessary.
     *      In case of error, 0.
     */
    native static int enumProcesses(int[] result);

    native static void enableDebugPrivilege();

    native static void noop();

    private static final Logger LOGGER = Logger.getLogger(Native.class.getName());

    static {
        load();
    }

    private static void load() {
        // are we on win32 or win64? err on 32bit side
        boolean win64 = "64".equals(System.getProperty("sun.arch.data.model"));
        String dllName = win64? "winp.x64" : "winp";

        // try loading winp.dll in the same directory as winp.jar
        final URL res = Native.class.getClassLoader().getResource(dllName+".dll");
        if(res!=null) {
            String url = res.toExternalForm();
            if(url.startsWith("jar:") || url.startsWith("wsjar:")) {
                int idx = url.lastIndexOf('!');
                String filePortion = url.substring(url.indexOf(':')+1,idx);
                while(filePortion.startsWith("/"))
                    filePortion = filePortion.substring(1);

                if(filePortion.startsWith("file:/")) {
                    filePortion = filePortion.substring(6);
                    if(filePortion.startsWith("//"))
                        filePortion = filePortion.substring(2);
                    filePortion = URLDecoder.decode(filePortion);
                    File jarFile = new File(filePortion);
                    File dllFile = new File(jarFile.getParentFile(),dllName+".dll");
                    if(!dllFile.exists() || jarFile.lastModified()>dllFile.lastModified()) {
                        // try to extract from within the jar
                        try {
                            copyStream(
                                res.openStream(),
                                new FileOutputStream(dllFile));
                            dllFile.setLastModified(jarFile.lastModified());
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Failed to write "+dllName+".dll", e);
                        }
                    }

                    loadDll(dllFile);
                    return;
                }
            }
            if(url.startsWith("file:")) {
                // during debug
                File f;
                try {
                    f = new File(res.toURI());
                } catch(URISyntaxException e) {
                    f = new File(res.getPath());
                }
                loadDll(f);
                return;
            }
        }

        // we don't know where winp.dll is, so let's just hope the user put it somewhere
        try {
            // load the native part of the code.
            // first try java.library.path
            System.loadLibrary(dllName);
        } catch( Throwable cause ) {
            // try to put winp.dll into a temporary directory
            if(res!=null) {
                File dll=null;
                try {
                    dll = File.createTempFile(dllName, "dll");
                    copyStream(res.openStream(),new FileOutputStream(dll));
                    loadDll(dll);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to write "+dllName+".dll", e);
                    // report the UnsatisfiedLinkError below, to encourage the user to put winp.dll to
                    // java.library.path
                } catch (LinkageError e) {
                    LOGGER.log(Level.WARNING, "Failed to load winp.dll from "+dll, e);
                    // ditto
                }
            }

            UnsatisfiedLinkError error = new UnsatisfiedLinkError("Unable to load "+dllName+".dll");
            error.initCause(cause);
            throw error;
        }
    }

    /**
     * Loads a DLL with a precaution for multi-classloader situation.
     */
    private static void loadDll(File dllFile) {
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
                    return;
                } catch (InterruptedException x) {
                    throw e; // throw the original exception
                } catch (LinkageError x) {
                    // retry
                }
            }
            // still failing after retry.
            throw e;
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
