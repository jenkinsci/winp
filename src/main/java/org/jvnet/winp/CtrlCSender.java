package org.jvnet.winp;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Locale;

class CtrlCSender {

  private static final int TIMEOUT_MILLIS = 5000;

  static boolean sendCtrlC(int pid, String ctrlCExePath) {
    ProcessBuilder builder = new ProcessBuilder(ctrlCExePath, String.valueOf(pid));
    builder.redirectErrorStream(true);
    Process process;
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new WinpException(e);
    }
    StreamGobbler stdout = new StreamGobbler(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()));
    Integer exitCode = null;
    try {
      exitCode = waitFor(process);
    } catch (InterruptedException ignored) {
    }
    stdout.stop();
    if (exitCode == null) {
      process.destroy();
      throw new WinpException("Failed to send Ctrl+C to " + pid + ": " + TIMEOUT_MILLIS + " ms timeout exceeded");
    }
    if (exitCode == 0) {
      return true;
    }
    throw new WinpException("Failed to send Ctrl+C, " + new File(ctrlCExePath).getName() +
            " terminated with exit code " + stringifyExitCode(exitCode) + ", output: " + stdout.getText());
  }

  private static String stringifyExitCode(int exitCode) {
    if (exitCode >= 0xC0000000 && exitCode < 0xD0000000) {
      // http://support.microsoft.com/kb/308558:
      //   If the result code has the "C0000XXX" format, the task did not complete successfully (the "C" indicates an error condition).
      //   The most common "C" error code is "0xC000013A: The application terminated as a result of a CTRL+C".
      return exitCode + " (0x" + Integer.toHexString(exitCode).toUpperCase(Locale.ENGLISH) + ")";
    }
    return String.valueOf(exitCode);
  }

  private static Integer waitFor(Process process) throws InterruptedException {
    long endTime = System.currentTimeMillis() + TIMEOUT_MILLIS;
    int i = 0;
    do {
      try {
        return process.exitValue();
      }
      catch (IllegalThreadStateException ignore) {
        Thread.sleep(i++ < 3 ? 10 : i < 5 ? 30 : 100);
      }
    } while (System.currentTimeMillis() < endTime);
    return null;
  }

  private static class StreamGobbler implements Runnable {

    private final Reader reader;
    private final StringBuilder myBuffer = new StringBuilder();
    private final Thread thread;
    private boolean isStopped = false;

    private StreamGobbler(Reader reader) {
      this.reader = reader;
      this.thread = new Thread(this, "sendctrlc.exe output reader");
      this.thread.start();
    }

    @Override
    public void run() {
      char[] buf = new char[8192];
      try {
        int readCount;
        while (!isStopped && (readCount = reader.read(buf)) >= 0) {
          myBuffer.append(buf, 0, readCount);
        }
        if (isStopped) {
          myBuffer.append("Failed to read output: force stopped");
        }
      }
      catch (Exception e) {
        myBuffer.append("Failed to read output: ").append(e.getClass().getName()).append(" raised");
      }
    }

    private void stop() {
      try {
        this.thread.join(1000); // await to read whole buffered output
      } catch (InterruptedException ignored) {
      }
      this.isStopped = true;
    }

    private String getText() {
      return myBuffer.toString();
    }
  }
}
