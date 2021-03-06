/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.bench.messaging;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.atomix.bench.BenchmarkConstants;
import io.atomix.bench.BenchmarkExecutor;
import io.atomix.bench.BenchmarkStatus;
import io.atomix.bench.ExecutorProgress;
import io.atomix.bench.map.MapBenchmarkExecutor;
import io.atomix.cluster.Member;
import io.atomix.core.Atomix;
import io.atomix.utils.net.Address;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.atomix.utils.concurrent.Threads.namedThreads;

/**
 * Messaging benchmark executor.
 */
public class MessagingBenchmarkExecutor extends BenchmarkExecutor<MessagingBenchmarkConfig> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MapBenchmarkExecutor.class);

  private static final double NANOSECONDS_PER_SECOND = 1_000_000_000.0;

  private final Atomix atomix;
  private MessagingBenchmarkConfig config;

  private final ExecutorService runnerExecutor = Executors.newSingleThreadExecutor(
      namedThreads("atomix-bench-runner", LOGGER));
  private final ExecutorService responderExecutor = Executors.newSingleThreadScheduledExecutor(
      namedThreads("atomix-bench-responder", LOGGER));
  private long startTime;
  private long endTime;
  private byte[] message;
  private volatile boolean running;
  private final AtomicInteger requestCounter = new AtomicInteger();
  private final AtomicInteger responseCounter = new AtomicInteger();
  private final AtomicInteger failureCounter = new AtomicInteger();
  private SynchronizedDescriptiveStatistics statistics;

  public MessagingBenchmarkExecutor(Atomix atomix) {
    super(atomix);
    this.atomix = atomix;
  }

  @Override
  public ExecutorProgress getProgress() {
    BigDecimal[] percentiles = new BigDecimal[]{
        new BigDecimal(statistics.getN() == 0 ? 0 : statistics.getPercentile(.05) / NANOSECONDS_PER_SECOND).setScale(10, RoundingMode.HALF_UP),
        new BigDecimal(statistics.getN() == 0 ? 0 : statistics.getPercentile(.25) / NANOSECONDS_PER_SECOND).setScale(10, RoundingMode.HALF_UP),
        new BigDecimal(statistics.getN() == 0 ? 0 : statistics.getPercentile(.5) / NANOSECONDS_PER_SECOND).setScale(10, RoundingMode.HALF_UP),
        new BigDecimal(statistics.getN() == 0 ? 0 : statistics.getPercentile(.75) / NANOSECONDS_PER_SECOND).setScale(10, RoundingMode.HALF_UP),
        new BigDecimal(statistics.getN() == 0 ? 0 : statistics.getPercentile(.95) / NANOSECONDS_PER_SECOND).setScale(10, RoundingMode.HALF_UP)
    };
    return new MessagingExecutorProgress(
        getMemberId().id(),
        running ? BenchmarkStatus.RUNNING : BenchmarkStatus.COMPLETE,
        requestCounter.get(),
        responseCounter.get(),
        failureCounter.get(),
        new BigDecimal((endTime > 0 ? endTime : System.currentTimeMillis()) - startTime)
            .setScale(3, RoundingMode.HALF_UP)
            .divide(new BigDecimal(1000.0)),
        percentiles);
  }

  @Override
  public void start(MessagingBenchmarkConfig config) {
    super.start(config);
    this.config = config;
    LOGGER.info("Running benchmark {}", config.getBenchId());

    LOGGER.debug("operations: {}", config.getOperations());
    LOGGER.debug("concurrency: {}", config.getConcurrency());
    LOGGER.debug("messageSize: {}", config.getMessageSize());
    LOGGER.debug("windowSize: {}", config.getWindowSize());

    if (config.getWindowSize() != null) {
      statistics = new SynchronizedDescriptiveStatistics(config.getWindowSize());
    } else {
      statistics = new SynchronizedDescriptiveStatistics();
    }

    running = true;

    message = new byte[config.getMessageSize()];
    for (int i = 0; i < config.getMessageSize(); i++) {
      message[i] = (byte) i;
    }

    atomix.getMessagingService().registerHandler(config.getBenchId(), this::respond, responderExecutor);
    startTime = System.currentTimeMillis();
    for (int i = 0; i < config.getConcurrency(); i++) {
      runnerExecutor.execute(this::run);
    }
  }

  /**
   * Handles and responds to a message.
   *
   * @param address the address of the node that sent the message
   * @param message the sent message
   * @return the response
   */
  private byte[] respond(Address address, byte[] message) {
    return message;
  }

  /**
   * Runs the benchmark.
   */
  private void run() {
    List<Address> benchMembers = atomix.getMembershipService().getMembers().stream()
        .filter(member -> !member.id().equals(atomix.getMembershipService().getLocalMember().id()))
        .filter(member -> member.properties().getProperty(BenchmarkConstants.BENCH_NODE_TYPE, Boolean.FALSE.toString()).equals(Boolean.TRUE.toString()))
        .map(Member::address)
        .collect(Collectors.toList());

    if (!benchMembers.isEmpty()) {
      run(message, benchMembers);
    } else {
      stop();
    }
  }

  /**
   * Recursively sends the given message to the given set of nodes.
   *
   * @param message the message to send
   * @param members the members to which to send messages
   */
  private void run(byte[] message, List<Address> members) {
    run(message, members, 0);
  }

  /**
   * Sends the given message to the next node.
   *
   * @param message the message to send
   * @param members the members to which to send messages
   * @param index the index of the member to which to send the message
   */
  private void run(byte[] message, List<Address> members, int index) {
    if (!running) {
      return;
    } else if (requestCounter.incrementAndGet() > config.getOperations()) {
      requestCounter.set(config.getOperations());
      stop();
      return;
    }

    Address address = members.get(index % members.size());
    long requestTime = System.nanoTime();
    atomix.getMessagingService().sendAndReceive(address, config.getBenchId(), message)
        .whenCompleteAsync((r, e) -> {
          if (e == null) {
            long responseTime = System.nanoTime();
            responseCounter.incrementAndGet();
            statistics.addValue(responseTime - requestTime);
          } else {
            failureCounter.incrementAndGet();
          }
          run(message, members, index + 1);
        }, runnerExecutor);
  }

  @Override
  public void stop() {
    running = false;
    endTime = System.currentTimeMillis();
    runnerExecutor.shutdownNow();
    responderExecutor.shutdownNow();
    atomix.getMessagingService().unregisterHandler(config.getBenchId());
    super.stop();
  }
}
