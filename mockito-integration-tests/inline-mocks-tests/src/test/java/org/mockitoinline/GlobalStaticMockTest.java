/*
 * Copyright (c) 2026 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitoinline;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.mock.MockScope;

// Regression test for issue #3643
public class GlobalStaticMockTest {

    /**
     * Mimics production code that processes documents on worker threads: each {@link Runnable}
     * invokes a static utility that would normally not be visible to a thread-local static mock.
     */
    private static class ProcessorThread implements Runnable {

        private final String inputFile;

        private ProcessorThread(String inputFile) {
            this.inputFile = inputFile;
        }

        @Override
        public void run() {
            DocumentUtility.process(inputFile);
        }
    }

    static class DocumentUtility {

        static final List<String> processed = new ArrayList<>();

        static String process(String inputFile) {
            processed.add(inputFile);
            return "processed " + inputFile;
        }
    }

    @Test
    public void global_static_mock_intercepts_calls_from_worker_threads() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            try (MockedStatic<DocumentUtility> mocked =
                    Mockito.mockStatic(
                            DocumentUtility.class, withSettings().scope(MockScope.GLOBAL))) {
                mocked.when(() -> DocumentUtility.process("input")).thenReturn("mocked input");

                // The production code path from issue #3643.
                Future<?> submitted = executorService.submit(new ProcessorThread("input"));
                submitted.get();

                mocked.verify(() -> DocumentUtility.process("input"));
            }
            assertEquals("processed input", DocumentUtility.process("input"));
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    public void global_static_mock_can_be_created_and_verified_from_creating_thread_only()
            throws Exception {
        try (MockedStatic<DocumentUtility> mocked =
                Mockito.mockStatic(DocumentUtility.class, withSettings().scope(MockScope.GLOBAL))) {
            mocked.when(() -> DocumentUtility.process("input")).thenReturn("mocked input");

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            try {
                Future<String> result =
                        executorService.submit(() -> DocumentUtility.process("input"));
                assertEquals("mocked input", result.get());
            } finally {
                executorService.shutdownNow();
            }

            // Verification stays on the thread that owns the mock.
            mocked.verify(() -> DocumentUtility.process("input"));
        }
    }

    @Test
    public void global_static_mock_is_safe_under_concurrent_invocations() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        try {
            try (MockedStatic<DocumentUtility> mocked =
                    Mockito.mockStatic(
                            DocumentUtility.class, withSettings().scope(MockScope.GLOBAL))) {
                mocked.when(() -> DocumentUtility.process("input")).thenReturn("mocked input");

                List<Future<String>> results = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    results.add(executorService.submit(() -> DocumentUtility.process("input")));
                }

                for (Future<String> result : results) {
                    assertEquals("mocked input", result.get());
                }

                mocked.verify(() -> DocumentUtility.process("input"), Mockito.times(100));
            }
        } finally {
            executorService.shutdownNow();
        }
    }
}
