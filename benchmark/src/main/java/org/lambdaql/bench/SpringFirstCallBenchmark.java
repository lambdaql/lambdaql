package org.lambdaql.bench;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * The first call of each {@link SpringDataBenchmark} method in a new JVM (see {@link FirstCallBenchmark}).
 * The Spring context start-up ({@link SpringDataState}) is not measured.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(5)
public class SpringFirstCallBenchmark extends SpringDataBenchmark {
}
