/*
 * Copyright 2025 - 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springaicommunity.agent.tools.task.repository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BackgroundTask}.
 *
 * @author Christian Tzolov
 */
@DisplayName("BackgroundTask Tests")
class BackgroundTaskTest {

	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		executor = Executors.newCachedThreadPool();
	}

	@AfterEach
	void tearDown() {
		if (executor != null) {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			}
			catch (InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Helper method to create a BackgroundTask with the new constructor pattern.
	 * @param taskId the task ID
	 * @param supplier the task execution logic
	 * @return a new BackgroundTask
	 */
	private BackgroundTask createTask(String taskId, java.util.function.Supplier<String> supplier) {
		CompletableFuture<String> future = CompletableFuture.supplyAsync(supplier, executor);
		return new BackgroundTask(taskId, future);
	}

	@Nested
	@DisplayName("Task Creation and Execution")
	class TaskCreationTests {

		@Test
		@DisplayName("Should create and auto-start task")
		void shouldCreateAndAutoStartTask() throws InterruptedException {
			// Given
			AtomicBoolean executed = new AtomicBoolean(false);
			String taskId = "test-task-1";

			// When
			BackgroundTask task = createTask(taskId, () -> {
				executed.set(true);
				return "completed";
			});

			// Then
			assertThat(task.getTaskId()).isEqualTo(taskId);

			// Wait for execution
			task.waitForCompletion(1000);
			assertThat(executed).isTrue();
			assertThat(task.isCompleted()).isTrue();
		}

		@Test
		@DisplayName("Should execute task asynchronously")
		void shouldExecuteTaskAsynchronously() throws InterruptedException {
			// Given
			CountDownLatch latch = new CountDownLatch(1);
			AtomicBoolean started = new AtomicBoolean(false);

			// When
			BackgroundTask task = createTask("async-task", () -> {
				started.set(true);
				try {
					latch.await();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return "done";
			});

			// Then - task should start immediately but not complete
			Thread.sleep(100); // Give time for task to start
			assertThat(started).isTrue();
			assertThat(task.isCompleted()).isFalse();

			// Complete the task
			latch.countDown();
			task.waitForCompletion(1000);
			assertThat(task.isCompleted()).isTrue();
		}

	}

	@Nested
	@DisplayName("Task Completion and Status")
	class TaskCompletionTests {

		@Test
		@DisplayName("Should track completion status correctly")
		void shouldTrackCompletionStatus() throws InterruptedException {
			// Given
			BackgroundTask task = createTask("completion-test", () -> {
				return "done";
			});

			// When - wait for completion
			boolean completed = task.waitForCompletion(1000);

			// Then
			assertThat(completed).isTrue();
			assertThat(task.isCompleted()).isTrue();
			assertThat(task.getStatus()).isEqualTo("Completed");
		}

		@Test
		@DisplayName("Should handle task timeout")
		void shouldHandleTaskTimeout() throws InterruptedException {
			// Given
			CountDownLatch latch = new CountDownLatch(1);
			BackgroundTask task = createTask("timeout-test", () -> {
				try {
					latch.await(); // Never completes
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return "done";
			});

			// When - wait with short timeout
			boolean completed = task.waitForCompletion(100);

			// Then
			assertThat(completed).isFalse();
			assertThat(task.isCompleted()).isFalse();
			assertThat(task.getStatus()).isEqualTo("Running");

			// Cleanup
			latch.countDown();
		}

	}

	@Nested
	@DisplayName("Result Handling")
	class ResultHandlingTests {

		@Test
		@DisplayName("Should store and retrieve result")
		void shouldStoreAndRetrieveResult() throws InterruptedException {
			// Given
			String expectedResult = "Task completed successfully";
			BackgroundTask task = createTask("result-test", () -> expectedResult);

			// When
			task.waitForCompletion(1000);

			// Then
			assertThat(task.getResult()).isEqualTo(expectedResult);
		}

		@Test
		@DisplayName("Should set result early and complete task")
		void shouldSetResultEarlyAndCompleteTask() throws InterruptedException {
			// Given
			CountDownLatch latch = new CountDownLatch(1);
			BackgroundTask task = createTask("update-result", () -> {
				try {
					latch.await();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return null;
			});

			// When - set result before task completes (CompletableFuture only completes
			// once)
			task.setResult("Early result");

			// Then - task should be completed with the early result
			assertThat(task.isCompleted()).isTrue();
			assertThat(task.getResult()).isEqualTo("Early result");

			// Cleanup
			latch.countDown();
		}

	}

	@Nested
	@DisplayName("Error Handling")
	class ErrorHandlingTests {

		@Test
		@DisplayName("Should capture exception during execution")
		void shouldCaptureException() throws InterruptedException {
			// Given
			String errorMessage = "Task failed!";

			// When
			BackgroundTask task = createTask("error-task", () -> {
				throw new RuntimeException(errorMessage);
			});

			task.waitForCompletion(1000);

			// Then
			assertThat(task.isCompleted()).isTrue();
			assertThat(task.hasError()).isTrue();
			assertThat(task.getError()).isInstanceOf(RuntimeException.class);
			assertThat(task.getErrorMessage()).isEqualTo(errorMessage);
			assertThat(task.getStatus()).isEqualTo("Failed: " + errorMessage);
		}

		@Test
		@DisplayName("Should handle error with cause")
		void shouldHandleErrorWithCause() throws InterruptedException {
			// Given
			String rootCause = "Root cause message";
			String wrapperMessage = "Wrapper exception";

			// When
			BackgroundTask task = createTask("nested-error", () -> {
				throw new RuntimeException(wrapperMessage, new IllegalArgumentException(rootCause));
			});

			task.waitForCompletion(1000);

			// Then
			assertThat(task.getError()).isNotNull();
			assertThat(task.getError().getCause()).isInstanceOf(IllegalArgumentException.class);
			assertThat(task.getError().getCause().getMessage()).isEqualTo(rootCause);
		}

	}

	@Nested
	@DisplayName("Thread Interruption")
	class InterruptionTests {

		@Test
		@DisplayName("Should handle thread interruption during wait")
		void shouldHandleThreadInterruption() throws InterruptedException {
			// Given
			CountDownLatch latch = new CountDownLatch(1);
			BackgroundTask task = createTask("interrupt-test", () -> {
				try {
					latch.await();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return "done";
			});

			AtomicBoolean wasInterrupted = new AtomicBoolean(false);
			AtomicBoolean interrupted = new AtomicBoolean(false);

			Thread testThread = new Thread(() -> {
				try {
					task.waitForCompletion(10000);
					// Check if the interrupt status was set
					if (Thread.interrupted()) {
						interrupted.set(true);
					}
				}
				catch (InterruptedException e) {
					wasInterrupted.set(true);
				}
			});

			// When
			testThread.start();
			Thread.sleep(50); // Give time for thread to start waiting
			testThread.interrupt();
			testThread.join(1000);

			// Then - either the InterruptedException was thrown, or the interrupt flag was
			// set
			assertThat(wasInterrupted.get() || interrupted.get()).isTrue();

			// Cleanup
			latch.countDown();
		}

	}

	@Nested
	@DisplayName("Concurrent Access")
	class ConcurrencyTests {

		@Test
		@DisplayName("Should handle concurrent completion checks")
		void shouldHandleConcurrentCompletionChecks() throws InterruptedException {
			// Given
			CountDownLatch taskLatch = new CountDownLatch(1);
			BackgroundTask task = createTask("concurrent-check", () -> {
				try {
					taskLatch.await();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return "done";
			});

			int threadCount = 5;
			CountDownLatch checkLatch = new CountDownLatch(threadCount);
			AtomicInteger completedCount = new AtomicInteger(0);

			// When - multiple threads check completion
			for (int i = 0; i < threadCount; i++) {
				new Thread(() -> {
					try {
						boolean completed = task.waitForCompletion(5000);
						if (completed) {
							completedCount.incrementAndGet();
						}
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					finally {
						checkLatch.countDown();
					}
				}).start();
			}

			// Complete the task
			Thread.sleep(100);
			taskLatch.countDown();

			// Then
			checkLatch.await(6, TimeUnit.SECONDS);
			assertThat(completedCount.get()).isEqualTo(threadCount);
		}

	}
}
