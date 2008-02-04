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

    native static int getProcessId(int handle);

    private static final Logger LOGGER = Logger.getLogger(Native.class.getName());

    static {
        load();
    }

    private static void load() {
        Throwable cause;
        try {
            // load the native part of the code.
            // first try java.library.path
            System.loadLibrary("winp");
            return;
        } catch( Throwable t ) {
            cause = t;
        }

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
                if(!dllFile.exists()) {
                    // try to extract from within the jar
                    try {
                        copyStream(
                            res.openStream(),
                            new FileOutputStream(dllFile));
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to write winp.dll", e);
                    }
                }
                System.load(dllFile.getPath());
                return;
            }
        }

        UnsatisfiedLinkError error = new UnsatisfiedLinkError("Unable to load winp.dll");
        error.initCause(cause);
        throw error;
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
