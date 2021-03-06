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
package io.atomix.bench.log;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.atomix.bench.BenchmarkStatus;
import io.atomix.bench.ExecutorProgress;
import io.atomix.bench.ExecutorResult;

/**
 * Log executor progress.
 */
public class LogExecutorProgress extends ExecutorProgress {
  private final int operations;
  private final BigDecimal time;

  @JsonCreator
  public LogExecutorProgress(
      @JsonProperty("member") String memberId,
      @JsonProperty("state") BenchmarkStatus state,
      @JsonProperty("operations") int operations,
      @JsonProperty("time") BigDecimal time) {
    super(memberId, state);
    this.operations = operations;
    this.time = time;
  }

  @Override
  public ExecutorResult asResult() {
    return new LogExecutorResult(getMemberId(), operations, time);
  }

  public int getOperations() {
    return operations;
  }

  public BigDecimal getTime() {
    return time;
  }
}
