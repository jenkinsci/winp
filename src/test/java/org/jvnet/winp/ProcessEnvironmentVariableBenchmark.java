package org.jvnet.winp;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class ProcessEnvironmentVariableBenchmark {

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Fork(1)
    public void testOriginal(ProcessState state, Blackhole blackhole) {
        WinProcess wp = new WinProcess(state.p);
        Map<String, String> env = wp.getEnvironmentVariables();
        //System.out.println(env.get("USERNAME"));
        blackhole.consume(env);
    }
    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Fork(1)
    public void testNew(ProcessState state, Blackhole blackhole) {
        WinProcess wp = new WinProcess(state.p);
        Map<String, String> env = wp.getEnvironmentVariablesNew();
        //System.out.println(env.get("USERNAME"));
        blackhole.consume(env);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Fork(1)
    public void testNativeRaw(ProcessState state, Blackhole blackhole) {
        WinProcess wp = new WinProcess(state.p);
        String str = Native.getCmdLineAndEnvVars(wp.getPid());
        blackhole.consume(str);
    }
}
