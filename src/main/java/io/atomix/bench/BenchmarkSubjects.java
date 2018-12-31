package io.atomix.bench;

/**
 * Benchmark subjects utility.
 */
public final class BenchmarkSubjects {

  /**
   * Returns the reporting message subject for the given benchmark.
   *
   * @param benchId the benchmark identifier
   * @return the benchmark reporting subject
   */
  public static String report(String benchId) {
    return String.format("%s-report", benchId);
  }
}
