package org.jvnet.winp;

import java.math.BigInteger;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URISyntaxException;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Functions defined in the DLL.
 * 
 * @author Kohsuke Kawaguchi
 */
class Native {
    native static boolean kill(int pid, boolean recursive);
    native static boolean isCriticalProcess(int pid);
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
    // system property holding the preferred folder for copying the dll file to.
    private static final String DLL_TARGET = "winp.folder.preferred";
    private static final String UNPACK_DLL_TO_PARENT_DIR = "winp.unpack.dll.to.parent.dir";

    static {
        load();
    }

    private static String md5(URL res) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            InputStream in = res.openStream();
            try {
                byte[] buf = new byte[8192];
                int len;
                while((len=in.read(buf))>=0)
                    md5.update(buf, 0, len);
                return toHex32(md5.digest());
            } finally {
                in.close();
            }
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        } catch (IOException e) {
            throw new Error("failed to checksum " + res + ": " + e, e);
        }
    }

    private static void load() {
        // are we on win32 or win64? err on 32bit side
        boolean win64 = "64".equals(System.getProperty("sun.arch.data.model"));
        String dllName = win64? "winp.x64" : "winp";

        // try loading winp.dll in the same directory as winp.jar
        final URL res = Native.class.getClassLoader().getResource(dllName+".dll");
        if(res!=null) {
            String url = res.toExternalForm();

          //patched by JetBrains: do not try to unpack the dll file to the directory containing the jar file by default.
          // It can fail because the process has no rights to write to that directory and also pollutes the project directories if the jar is used in development mode.
          boolean unpackToParentDir = Boolean.parseBoolean(System.getProperty(UNPACK_DLL_TO_PARENT_DIR, "true"));

          if(unpackToParentDir && (url.startsWith("jar:") || url.startsWith("wsjar:"))) {
                int idx = url.lastIndexOf('!');
                String filePortion = url.substring(url.indexOf(':')+1,idx);
                while(filePortion.startsWith("/"))
                    filePortion = filePortion.substring(1);

                if(filePortion.startsWith("file:")) {
                    filePortion = filePortion.substring(5);
                    if(filePortion.startsWith("///")) {
                        // JDK on Unix uses file:/home/kohsuke/abc, whereas
                        // I believe RFC says file:///home/kohsuke/abc/... is correct.
                        filePortion = filePortion.substring(2);
                    } else
                    if(filePortion.startsWith("//")) {
                        // this indicates file://host/path-in-host format
                        // Windows maps UNC path to this. On Unix, there's no well defined
                        // semantics for  this.
                    }

                    filePortion = URLDecoder.decode(filePortion);
                    String preferred = System.getProperty(DLL_TARGET);
                    File jarFile = new File(preferred != null ? preferred : filePortion.replace('/',File.separatorChar));
                    File dllFile = new File(jarFile.getParentFile(),dllName+'.'+md5(res)+".dll");
                    if(!dllFile.exists()) {
                        // try to extract from within the jar
                        try {
                            copyStream(
                                res.openStream(),
                                new FileOutputStream(dllFile));
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Failed to write "+dllFile, e);
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

            File dll=null;
            try {
                dll = File.createTempFile(dllName, ".dll");
                dll.deleteOnExit();
                copyStream(res.openStream(),new FileOutputStream(dll));
                loadDll(dll);
                return;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to write "+dllName+".dll", e);
                // report the UnsatisfiedLinkError below, to encourage the user to put winp.dll to
                // java.library.path
            } catch (LinkageError e) {
                LOGGER.log(Level.WARNING, "Failed to load winp.dll from "+dll, e);
                // ditto
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
                    return;
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

    /**
     * Convert 128bit data into hex string.
     */
    private static String toHex32(byte[] b) {
        return String.format("%032X",new BigInteger(1,b));
    }
}
