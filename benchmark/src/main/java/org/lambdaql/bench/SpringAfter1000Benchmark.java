package org.lambdaql.bench;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Each {@link SpringDataBenchmark} method after exactly 1,000 calls in a new JVM (see {@link After1000Benchmark}).
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, batchSize = 1000)
@Measurement(iterations = 200, batchSize = 1)
@Fork(2)
public class SpringAfter1000Benchmark extends SpringDataBenchmark {
}
