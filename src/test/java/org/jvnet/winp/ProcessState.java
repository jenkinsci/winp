package org.jvnet.winp;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.State;

import java.io.File;
import java.io.IOException;

@State(Scope.Benchmark)
public class ProcessState {

    private static final File NULL_FILE = new File("NUL:");

    public Process p;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("ping", "-t", "localhost");
        pb.redirectError(NULL_FILE);
        pb.redirectOutput(NULL_FILE);
        p = pb.start();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws InterruptedException {
        p.destroyForcibly();
        p.waitFor();
    }
}
