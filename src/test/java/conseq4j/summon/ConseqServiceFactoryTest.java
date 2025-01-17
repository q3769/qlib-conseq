/*
 * MIT License
 *
 * Copyright (c) 2021 Qingtian Wang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package conseq4j.summon;

import static conseq4j.TestUtils.createSpyingTasks;
import static conseq4j.TestUtils.getAllCompleteNormal;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Range;
import conseq4j.SpyingTask;
import conseq4j.TestUtils;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.NonNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** @author Qingtian Wang */
class ConseqServiceFactoryTest {
  private static final int TASK_COUNT = 100;

  private static List<Callable<SpyingTask>> toCallables(@NonNull List<SpyingTask> tasks) {
    return tasks.stream().map(SpyingTask::toCallable).toList();
  }

  void assertSingleThread(@NonNull List<SpyingTask> tasks) {
    List<String> distinctThreads =
        tasks.stream().map(SpyingTask::getRunThreadName).distinct().toList();
    assertEquals(1, distinctThreads.size());
  }

  @Test
  void concurrencyBoundedByTotalTaskCount() {
    List<Future<SpyingTask>> futures;
    try (ConseqServiceFactory withHigherConcurrencyThanTaskCount =
        ConseqServiceFactory.instance(TASK_COUNT * 2)) {

      futures = createSpyingTasks(TASK_COUNT).stream()
          .map(task -> withHigherConcurrencyThanTaskCount
              .getExecutorService(UUID.randomUUID())
              .submit(task.toCallable()))
          .toList();
    }

    final long totalRunThreads = getAllCompleteNormal(futures).stream()
        .map(SpyingTask::getRunThreadName)
        .distinct()
        .count();
    assertTrue(totalRunThreads <= TASK_COUNT);
  }

  @Test
  void higherConcurrencyRendersBetterThroughput() {
    List<SpyingTask> sameTasks = createSpyingTasks(TASK_COUNT);
    int lowConcurrency = 2;
    int highConcurrency = lowConcurrency * 10;

    long lowConcurrencyStart;
    List<Future<SpyingTask>> lowConcurrencyFutures;
    try (ConseqServiceFactory withLowConcurrency = ConseqServiceFactory.instance(lowConcurrency)) {
      lowConcurrencyStart = System.nanoTime();
      lowConcurrencyFutures = sameTasks.stream()
          .map(t -> withLowConcurrency.getExecutorService(UUID.randomUUID()).submit(t.toCallable()))
          .toList();
    }
    TestUtils.awaitFutures(lowConcurrencyFutures);
    long lowConcurrencyTime = System.nanoTime() - lowConcurrencyStart;

    long highConcurrencyStart;
    List<Future<SpyingTask>> highConcurrencyFutures;
    try (ConseqServiceFactory withHighConcurrency =
        ConseqServiceFactory.instance(highConcurrency)) {
      highConcurrencyStart = System.nanoTime();
      highConcurrencyFutures = sameTasks.stream()
          .map(task ->
              withHighConcurrency.getExecutorService(UUID.randomUUID()).submit(task.toCallable()))
          .toList();
    }
    TestUtils.awaitFutures(highConcurrencyFutures);
    long highConcurrencyTime = System.nanoTime() - highConcurrencyStart;

    assertTrue(lowConcurrencyTime > highConcurrencyTime);
  }

  @Test
  void invokeAllRunsTasksOfSameSequenceKeyInSequence() throws InterruptedException {
    final List<Future<SpyingTask>> completedFutures;
    try (ConseqServiceFactory defaultConseqServiceFactory = ConseqServiceFactory.instance()) {
      List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
      UUID sameSequenceKey = UUID.randomUUID();

      completedFutures = defaultConseqServiceFactory
          .getExecutorService(sameSequenceKey)
          .invokeAll(toCallables(tasks));
    }

    final List<SpyingTask> doneTasks = getAllCompleteNormal(completedFutures);
    assertSingleThread(doneTasks);
  }

  @Test
  void invokeAnyChoosesTaskInSequenceRange() throws InterruptedException, ExecutionException {
    ConseqServiceFactory defaultConseqServiceFactory = ConseqServiceFactory.instance();
    List<SpyingTask> tasks = createSpyingTasks(TASK_COUNT);
    UUID sameSequenceKey = UUID.randomUUID();

    SpyingTask doneTask = defaultConseqServiceFactory
        .getExecutorService(sameSequenceKey)
        .invokeAny(toCallables(tasks));

    final Integer scheduledSequenceIndex = doneTask.getScheduledSequenceIndex();
    assertTrue(Range.closedOpen(0, TASK_COUNT).contains(scheduledSequenceIndex));
  }

  @Test
  void submitsRunAllTasksOfSameSequenceKeyInSequence() {
    List<SpyingTask> tasks;
    try (ConseqServiceFactory defaultConseqServiceFactory = ConseqServiceFactory.instance()) {
      tasks = createSpyingTasks(TASK_COUNT);
      UUID sameSequenceKey = UUID.randomUUID();

      tasks.forEach(task ->
          defaultConseqServiceFactory.getExecutorService(sameSequenceKey).execute(task));
    }

    TestUtils.awaitAllComplete(tasks);
    assertSingleThread(tasks);
  }

  @Nested
  class individualShutdownUnsupported {
    @Test
    void whenShutdownsCalled() {
      try (ConseqServiceFactory conseqServiceFactory = ConseqServiceFactory.instance()) {
        ExecutorService sequentialExecutor =
            conseqServiceFactory.getExecutorService(UUID.randomUUID());
        assertThrows(UnsupportedOperationException.class, sequentialExecutor::shutdown);
        assertThrows(UnsupportedOperationException.class, sequentialExecutor::shutdownNow);
        assertFalse(sequentialExecutor.isShutdown());
        assertFalse(sequentialExecutor.isTerminated());
      }
    }
  }

  @Nested
  class factoryClose {
    @Test
    void whenCloseCalled() {
      ConseqServiceFactory conseqServiceFactory = ConseqServiceFactory.instance();
      ExecutorService sequentialExecutor =
          conseqServiceFactory.getExecutorService(UUID.randomUUID());

      conseqServiceFactory.close();

      assertTrue(sequentialExecutor.isShutdown());
      assertTrue(sequentialExecutor.isTerminated());
    }
  }
}
