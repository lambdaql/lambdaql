package org.lambdaql.bench;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * The first call of each {@link QueryBenchmark} method in a new JVM: class loading, Hibernate's first parse of the
 * query, and for lambdaQL the first analysis of the lambda bytecode. Each method runs once in each of 5 new JVMs;
 * the JPA start-up and the test data (the {@link BenchDatabase} setup) are not measured.
 * The methods are inherited, so the measured code is the same as in {@link QueryBenchmark} and
 * {@link After1000Benchmark}.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(5)
public class FirstCallBenchmark extends QueryBenchmark {
}
