package org.lambdaql.bench;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Each {@link QueryBenchmark} method after exactly 1,000 calls in a new JVM: the score is the average time of calls
 * 1,001 to 1,200, each call timed on its own. The lambda analysis and Hibernate's query plan are cached by then;
 * the JIT compiler may still be working (compare with the long run of {@link QueryBenchmark}).
 * The methods are inherited, so the measured code is the same as in {@link QueryBenchmark}.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, batchSize = 1000)
@Measurement(iterations = 200, batchSize = 1)
@Fork(2)
public class After1000Benchmark extends QueryBenchmark {
}
