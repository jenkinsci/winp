package org.jvnet.winp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URISyntaxException;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Functions defined in the DLL.
 * 
 * @author Kohsuke Kawaguchi
 */
class Native {

    public static final String DLL_NAME = "64".equals(System.getProperty("sun.arch.data.model")) ? "winp.x64" : "winp";

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
    //TODO: usage of this field has been removed in https://github.com/kohsuke/winp/pull/27
    // Likely it needs to be fixed
    private static final String UNPACK_DLL_TO_PARENT_DIR = "winp.unpack.dll.to.parent.dir";

    static {
        load();
    }

    @Nonnull
    private static String md5(@Nonnull URL res) throws IOException {
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
            throw new IOException("Cannot find MD5 algorithm", e);
        } catch (IOException e) {
            throw new IOException("failed to checksum " + res + ": " + e, e);
        }
    }

    private static void load() throws UnsatisfiedLinkError {

        final URL res = Native.class.getClassLoader().getResource(DLL_NAME + ".dll");

        try {
            if (res != null) {
                loadByUrl(res);
            } else {
                // we don't know where winp.dll is, so let's just hope the user put it somewhere
                System.loadLibrary(DLL_NAME);
            }
        } catch (Throwable cause) {

            UnsatisfiedLinkError error = new UnsatisfiedLinkError("Unable to load " + DLL_NAME + ".dll");
            error.initCause(cause);
            throw error;
        }
    }

    private static void loadByUrl(@Nonnull URL res) throws IOException {

        String url = res.toExternalForm();

        if (url.startsWith("file:")) {
            // during debug
            File f;
            try {
                f = new File(res.toURI());
            } catch (URISyntaxException e) {
                f = new File(res.getPath());
            }
            loadDll(f);
            return;
        }

        try {
            File dllFile = extractToStaticLocation(res);
            loadDll(dllFile);
            return;
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to load DLL from static location", e);
        }

        File dllFile = extractToTmpLocation(res);
        loadDll(dllFile);
    }

    @Nonnull
    private static File extractToStaticLocation(@Nonnull URL url) throws IOException {

        File jarFile = getJarFile(url);
        if (jarFile == null) {
            throw new IOException("Failed to locate JAR file by URL " + url);
        }

        String preferred = System.getProperty(DLL_TARGET);
        File destFile = new File(preferred != null ? new File(preferred) : jarFile.getParentFile(), DLL_NAME + '.' + md5(url) + ".dll");
        if (!destFile.exists()) {
            copyStream(url.openStream(), new FileOutputStream(destFile));
        }
        return destFile;
    }

    @Nonnull
    private static File extractToTmpLocation(@Nonnull URL res) throws IOException {

        File tmpFile = File.createTempFile(DLL_NAME, ".dll");
        tmpFile.deleteOnExit();
        copyStream(res.openStream(), new FileOutputStream(tmpFile));
        return tmpFile;
    }

    @CheckForNull
    private static File getJarFile(@Nonnull URL res) {

        String url = res.toExternalForm();
        if (!(url.startsWith("jar:") || url.startsWith("wsjar:"))) {
            return null;
        }

        int idx = url.lastIndexOf('!');
        String filePortion = url.substring(url.indexOf(':') + 1, idx);
        while (filePortion.startsWith("/"))
            filePortion = filePortion.substring(1);

        if (!filePortion.startsWith("file:")) {
            return null;
        }
        filePortion = filePortion.substring(5);
        if (filePortion.startsWith("///")) {
            // JDK on Unix uses file:/home/kohsuke/abc, whereas
            // I believe RFC says file:///home/kohsuke/abc/... is correct.
            filePortion = filePortion.substring(2);
        } else if (filePortion.startsWith("//")) {
            // this indicates file://host/path-in-host format
            // Windows maps UNC path to this. On Unix, there's no well defined
            // semantics for  this.
        }

        filePortion = URLDecoder.decode(filePortion);
        return new File(filePortion.replace('/', File.separatorChar));
    }

    /**
     * Loads a DLL with a precaution for multi-classloader situation.
     */
    @SuppressFBWarnings(value = "DM_GC", justification = "Fallback in the case of linkage errors, see details in the code")
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
    @Nonnull
    private static String toHex32(@Nonnull byte[] b) {
        return String.format("%032X",new BigInteger(1,b));
    }
}
