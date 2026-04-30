/*
 * Copyright 2026 The OSHI Project Contributors
 * SPDX-License-Identifier: MIT
 */
package oshi.benchmark;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.util.GlobalConfig;

/**
 * Side-by-side benchmarks of JNA vs FFM implementations of {@link NetworkIF#updateAttributes()}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsPrepend = "--enable-native-access=ALL-UNNAMED")
public class NetworkIFBenchmark {

    private List<NetworkIF> jnaIfs;
    private List<NetworkIF> ffmIfs;

    /** Creates a new benchmark instance. Required by JMH for {@code @State} classes. */
    public NetworkIFBenchmark() {
    }

    /**
     * Initializes JNA and FFM {@link NetworkIF} lists with memoization disabled.
     */
    @Setup
    public void setup() {
        GlobalConfig.set(GlobalConfig.OSHI_UTIL_MEMOIZER_EXPIRATION, 0);
        HardwareAbstractionLayer jnaHal = new oshi.SystemInfo().getHardware();
        HardwareAbstractionLayer ffmHal = new oshi.ffm.SystemInfo().getHardware();
        jnaIfs = jnaHal.getNetworkIFs();
        ffmIfs = ffmHal.getNetworkIFs();
    }

    /**
     * Benchmarks the JNA implementation of {@link NetworkIF#updateAttributes()}.
     *
     * @param bh JMH black hole to prevent dead-code elimination
     */
    @Benchmark
    public void jna(Blackhole bh) {
        for (NetworkIF nif : jnaIfs) {
            bh.consume(nif.updateAttributes());
        }
    }

    /**
     * Benchmarks the FFM implementation of {@link NetworkIF#updateAttributes()}.
     *
     * @param bh JMH black hole to prevent dead-code elimination
     */
    @Benchmark
    public void ffm(Blackhole bh) {
        for (NetworkIF nif : ffmIfs) {
            bh.consume(nif.updateAttributes());
        }
    }

    /**
     * Standalone entry point for running this benchmark outside the fat jar.
     *
     * @param args command-line arguments (unused)
     * @throws RunnerException if the benchmark fails
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(NetworkIFBenchmark.class.getSimpleName()).build();
        new Runner(opt).run();
    }
}
