package org.jvnet.winp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import edu.umd.cs.findbugs.annotations.CheckReturnValue;
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

    public static final String DLL_NAME = "64".equals(System.getProperty("sun.arch.data.model")) ? "winp.x64" : "winp";
    public static final String CTRLCEXE_NAME = "64".equals(System.getProperty("sun.arch.data.model")) ? "sendctrlc.x64" : "sendctrlc";

    native static boolean kill(int pid, boolean recursive);
    native static boolean isCriticalProcess(int pid);
    native static boolean isProcessRunning(int pid);
    native static int setPriority(int pid, int value);
    native static int getProcessId(long handle);
    native static boolean exitWindowsEx(int flags,int reasonCode);

    /**
     * Gets the command line and environment variables of the process
     * identified by the process ID.
     * If the environment variables are not required, consider using {@link #getCmdLine(int)}.
     *
     * <p>
     * To simplify the JNI side, the resulting string is structured to
     * "cmdlineargs\0env1=val1\0env2=val2\0..."
     */
    native static String getCmdLineAndEnvVars(int pid);
    
    /**
     * Gets the command line of the process identified by the process ID.
     * @param pid Process ID
     * @return Command line or {@code null} if it cannot be retrieved
     * @throws WinpException Operation failure
     */
    native static String getCmdLine(int pid) throws WinpException;

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

    private static volatile String ctrlCExePath;

    /**
     * Sends Ctrl+C to the process.
     * Due to the Windows platform specifics, this execution will spawn a separate process to deliver the signal.
     * This process is expected to be executed within a 5-second timeout.
     * @param pid PID to receive the signal
     * @return {@code true} if the signal was delivered successfully
     * @throws WinpException Execution error
     */
    @CheckReturnValue
    public static boolean sendCtrlC(int pid) throws WinpException {
        if (loadFailure != null) {
            throw new WinpException("Cannot send the CtrlC signal to the process: winp init failed", loadFailure);
        }
        if (ctrlCExePath == null) {
            LOGGER.log(Level.WARNING, "Cannot send the CtrlC signal to the process. Cannot find the executable {0}.dll", CTRLCEXE_NAME);
            return false;
        }
        return CtrlCSender.sendCtrlC(pid, ctrlCExePath);
    }

    private static volatile Throwable loadFailure;

    static {
        try {
            File exeFile = load();
            ctrlCExePath = (exeFile == null) ? null : exeFile.getPath();
        } catch (Throwable t) {
            loadFailure = t;
            LOGGER.log(Level.SEVERE, "Cannot init winp native", t);
        }
    }

    @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_MD5", justification = "TODO needs triage")
    private static String md5(URL res) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            try (InputStream in = res.openStream()) {
                byte[] buf = new byte[8192];
                int len;
                while((len=in.read(buf))>=0)
                    md5.update(buf, 0, len);
                return toHex32(md5.digest());
            }
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        } catch (IOException e) {
            throw new Error("failed to checksum " + res + ": " + e, e);
        }
    }

    private static File load() {

        final URL dllRes = Native.class.getClassLoader().getResource(DLL_NAME + ".dll");

        try {
            if (dllRes != null) {
                final URL exeRes = Native.class.getClassLoader().getResource(CTRLCEXE_NAME + ".exe");
                return loadByUrl(dllRes, exeRes);
            } else {
                // we don't know where winp.dll is, so let's just hope the user put it somewhere
                System.loadLibrary(DLL_NAME);
                return null;
            }
        } catch (Throwable cause) {

            UnsatisfiedLinkError error = new UnsatisfiedLinkError("Unable to load " + DLL_NAME + ".dll");
            error.initCause(cause);
            throw error;
        }
    }

    private static File loadByUrl(URL dllRes, URL exeRes) throws IOException {

        String dllUrl = dllRes.toExternalForm();
        if (dllUrl.startsWith("file:")) {
            // during debug the files are on disk and not in a jar
            if (!exeRes.toExternalForm().startsWith("file:")) {
                LOGGER.log(Level.WARNING, "DLL and EXE are inconsistenly present on disk");
            }

            File f;
            try {
                f = new File(dllRes.toURI());
            } catch (URISyntaxException e) {
                f = new File(dllRes.getPath());
            }
            loadDll(f);

            File exeFile = new File(f.getParentFile(), CTRLCEXE_NAME + ".exe");
            return exeFile;
        }

        try {
            File dllFile = extractToStaticLocation(dllRes);
            File exeFile = extractExe(exeRes, dllFile.getParentFile());
            loadDll(dllFile);
            return exeFile;
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to load DLL from static location", e);
        }

        File dllFile = extractToTmpLocation(dllRes);
        File exeFile = extractExe(exeRes, dllFile.getParentFile());
        loadDll(dllFile);
        return exeFile;
    }

    private static File extractToStaticLocation(URL url) throws IOException {

        File jarFile = getJarFile(url);
        if (jarFile == null) {
            throw new RuntimeException("Failed to locate JAR file by URL " + url);
        }

        String preferred = System.getProperty(DLL_TARGET);
        File destFile = new File(preferred != null ? new File(preferred) : jarFile.getParentFile(), DLL_NAME + '.' + md5(url) + ".dll");
        if (!destFile.exists()) {
            copyStream(url.openStream(), new FileOutputStream(destFile));
        }
        return destFile;
    }

    private static File extractToTmpLocation(URL res) throws IOException {

        File tmpFile = File.createTempFile(DLL_NAME, ".dll");
        tmpFile.deleteOnExit();
        copyStream(res.openStream(), new FileOutputStream(tmpFile));
        return tmpFile;
    }

    private static File extractExe(URL res, File dir) throws IOException {
        File destFile = new File(dir, CTRLCEXE_NAME + '.' + md5(res) + ".exe");
        if (!destFile.exists()) {
            copyStream(res.openStream(), new FileOutputStream(destFile));
        }
        return destFile;
    }

    private static File getJarFile(URL res) {

        String url = res.toExternalForm();
        if (!(url.startsWith("jar:") || url.startsWith("wsjar:") || url.startsWith("zip:"))) {
            return null;
        }

        int idx = url.lastIndexOf('!');
        String filePortion = url.substring(url.indexOf(':') + 1, idx);
        while (filePortion.startsWith("/")) {
            filePortion = filePortion.substring(1);
        }

        if (filePortion.startsWith("file:")) {
        	filePortion = filePortion.substring(5);
        }
        
        if (filePortion.startsWith("///")) {
            // JDK on Unix uses file:/home/kohsuke/abc, whereas
            // I believe RFC says file:///home/kohsuke/abc/... is correct.
            filePortion = filePortion.substring(2);
        } /*else if (filePortion.startsWith("//")) {
            // this indicates file://host/path-in-host format
            // Windows maps UNC path to this. On Unix, there's no well defined
            // semantics for  this.
        }*/

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
    private static String toHex32(byte[] b) {
        return String.format("%032X",new BigInteger(1,b));
    }
}
