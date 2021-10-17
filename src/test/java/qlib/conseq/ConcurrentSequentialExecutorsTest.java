/*
 * The MIT License
 *
 * Copyright 2021 Qingtian Wang.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package qlib.conseq;

import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author q3769
 */
public class ConcurrentSequentialExecutorsTest {

    @org.junit.jupiter.api.Test
    public void defaultConcurrencyShouldBeUnbound() {
        ConcurrentSequentialExecutors target = ConcurrentSequentialExecutors.newBuilder().build();
        assertEquals(Integer.MAX_VALUE, target.getMaxConcurrency());
    }

    @org.junit.jupiter.api.Test
    public void shouldHonorSpecifiedConcurrency() {
        int stubConcurrency = 5;
        ConcurrentSequentialExecutors target = ConcurrentSequentialExecutors.newBuilder().withMaxConcurrency(stubConcurrency).build();
        assertEquals(stubConcurrency, target.getMaxConcurrency());
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnSameExcecutorOnSameName() {
        Object sequenceKey = new Object();
        ConcurrentSequentialExecutors target = ConcurrentSequentialExecutors.newBuilder().build();

        Executor e1 = target.getSequentialExecutor(sequenceKey);
        Executor e2 = target.getSequentialExecutor(sequenceKey);

        assertSame(e1, e2);
    }
}