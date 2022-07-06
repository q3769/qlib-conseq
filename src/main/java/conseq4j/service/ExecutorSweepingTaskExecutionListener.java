/*
 * The MIT License
 * Copyright 2022 Qingtian Wang.
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package conseq4j.service;

import lombok.ToString;
import lombok.extern.java.Log;
import org.apache.commons.pool2.ObjectPool;

import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.logging.Level;

/**
 * Returns in-service executor back to pool when there is no more submitted tasks pending to run.
 *
 * @author Qingtian Wang
 */
@Log @ToString class ExecutorSweepingTaskExecutionListener implements TaskExecutionListener {

    private final Object sequenceKey;
    private final ConcurrentMap<Object, SingleThreadTaskExecutionListenableExecutor> servicingSequentialExecutors;
    private final ObjectPool<SingleThreadTaskExecutionListenableExecutor> executorPool;

    /**
     * @param sequenceKey                  the sequence key whose corresponding executor is under operation.
     * @param servicingSequentialExecutors map of all in-service executors keyed on their sequence keys.
     * @param executorPool                 executor pool.
     */
    public ExecutorSweepingTaskExecutionListener(Object sequenceKey,
            ConcurrentMap<Object, SingleThreadTaskExecutionListenableExecutor> servicingSequentialExecutors,
            ObjectPool<SingleThreadTaskExecutionListenableExecutor> executorPool) {
        this.sequenceKey = sequenceKey;
        this.servicingSequentialExecutors = servicingSequentialExecutors;
        this.executorPool = executorPool;
    }

    /**
     * This should return accurate (rather than approximate) count when invoked synchronously from inside
     * {@link ConcurrentMap#compute(Object, BiFunction)}
     */
    private static long pendingTaskCountOf(SingleThreadTaskExecutionListenableExecutor presentExecutor) {
        long pendingTaskCount = presentExecutor.getTaskCount() - presentExecutor.getCompletedTaskCount();
        log.log(Level.FINE, () -> pendingTaskCount + " task(s) submitted to yet not finished by " + presentExecutor);
        return pendingTaskCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override public void beforeExecute(Thread taskExecutionThread, Runnable task) {
        // no-op
    }

    /**
     * Check and return in-service executor back to pool. Invoked at the end of each task execution to ensure no idle
     * executors linger in the active-servicing map.
     */
    @Override public void afterExecute(Runnable task, Throwable taskExecutionError) {
        sweepOrKeepSequentialExecutorInService(task, taskExecutionError);
    }

    private void sweepOrKeepSequentialExecutorInService(Runnable task, Throwable taskExecutionError) {
        log.log(Level.FINEST,
                () -> "start sweeping-check executor after servicing task " + task + " with execution error "
                        + taskExecutionError + " in " + this);
        servicingSequentialExecutors.compute(sequenceKey, (presentSequenceKey, presentExecutor) -> {
            if (presentExecutor == null) {
                log.log(Level.FINE, () -> "executor for sequence key " + sequenceKey
                        + " already swept off of service by another listener");
                return null;
            }
            if (pendingTaskCountOf(presentExecutor) == 0) {
                log.log(Level.FINE, () -> "sweeping " + presentExecutor + " off of service");
                try {
                    executorPool.returnObject(presentExecutor);
                } catch (Exception ex) {
                    log.log(Level.WARNING, "error returning " + presentExecutor + " back to pool " + executorPool, ex);
                }
                return null;
            }
            log.log(Level.FINE, () -> "keeping " + presentExecutor + " in service");
            return presentExecutor;
        });
        log.log(Level.FINEST, () -> "done sweeping-check executor for sequence key in " + this);
    }
}