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
package org.springaicommunity.agent.tools.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;
import org.springaicommunity.agent.tools.task.repository.TaskRepository;

import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TaskOutputTool}.
 *
 * @author Christian Tzolov
 */
@DisplayName("TaskOutputTool Tests")
class TaskOutputToolTest {

	private TaskRepository taskRepository;

	private TaskOutputTool.TaskOutputFunction taskOutputFunction;

	@BeforeEach
	void setUp() {
		this.taskRepository = new DefaultTaskRepository();
		this.taskOutputFunction = new TaskOutputTool.TaskOutputFunction(taskRepository);
	}

	@Nested
	@DisplayName("Builder Tests")
	class BuilderTests {

		@Test
		@DisplayName("Should build tool with valid repository")
		void shouldBuildToolWithValidRepository() {
			ToolCallback tool = TaskOutputTool.builder().taskRepository(taskRepository).build();

			assertThat(tool).isNotNull();
		}

		@Test
		@DisplayName("Should fail when taskRepository is null")
		void shouldFailWhenTaskRepositoryIsNull() {
			assertThatThrownBy(() -> TaskOutputTool.builder().taskRepository(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("taskRepository must not be null");
		}

		@Test
		@DisplayName("Should fail when building without taskRepository")
		void shouldFailWhenBuildingWithoutTaskRepository() {
			assertThatThrownBy(() -> TaskOutputTool.builder().build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("taskRepository must be provided");
		}

		@Test
		@DisplayName("Should allow custom task description template")
		void shouldAllowCustomTaskDescriptionTemplate() {
			String customTemplate = "Custom task output description";

			ToolCallback tool = TaskOutputTool.builder()
				.taskRepository(taskRepository)
				.taskDescriptionTemplate(customTemplate)
				.build();

			assertThat(tool).isNotNull();
		}

	}

	@Nested
	@DisplayName("Task Not Found Tests")
	class TaskNotFoundTests {

		@Test
		@DisplayName("Should return error when task ID does not exist")
		void shouldReturnErrorWhenTaskIdDoesNotExist() {
			TaskOutputTool.TaskOutputCall call = new TaskOutputTool.TaskOutputCall("non-existent-task", null, null);

			String result = taskOutputFunction.apply(call);

			assertThat(result).contains("Error: No background task found with ID: non-existent-task");
		}

	}

	@Nested
	@DisplayName("Completed Task Tests")
	class CompletedTaskTests {

		@Test
		@DisplayName("Should retrieve result from completed task")
		void shouldRetrieveResultFromCompletedTask() {
			String taskId = "completed-task";
			String expectedResult = "Task completed successfully";
			taskRepository.putTask(taskId, () -> expectedResult);
			taskRepository.getTasks(taskId).setResult(expectedResult);

			TaskOutputTool.TaskOutputCall call = new TaskOutputTool.TaskOutputCall(taskId, true, null);

			String result = taskOutputFunction.apply(call);

			assertThat(result).contains("Task ID: " + taskId);
			assertThat(result).contains("Status: Completed");
			assertThat(result).contains("Result:\n" + expectedResult);
		}

		@Test
		@DisplayName("Should handle completed task with null result")
		void shouldHandleCompletedTaskWithNullResult() throws InterruptedException {
			String taskId = "null-result-task";
			taskRepository.putTask(taskId, () -> null).waitForCompletion(1000);

			TaskOutputTool.TaskOutputCall call = new TaskOutputTool.TaskOutputCall(taskId, true, null);

			String result = taskOutputFunction.apply(call);

			assertThat(result).contains("Task ID: " + taskId);
			assertThat(result).contains("Status: Completed");
			assertThat(result).doesNotContain("Result:");
		}

	}

	@Nested
	@DisplayName("Running Task Tests")
	class RunningTaskTests {

		@Test
		@DisplayName("Should return running status for incomplete task with block=false")
		void shouldReturnRunningStatusForIncompleteTaskWithBlockFalse() {
			String taskId = "running-task";
			taskRepository.putTask(taskId, () -> {
				try {
					Thread.sleep(10000);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return "done";
			});

			TaskOutputTool.TaskOutputCall call = new TaskOutputTool.TaskOutputCall(taskId, false, null);

			String result = taskOutputFunction.apply(call);

			assertThat(result).contains("Task ID: " + taskId);
			assertThat(result).contains("Status: Running");
			assertThat(result).contains("Task still running...");
		}

		@Test
		@DisplayName("Should wait for completion when block=true")
		void shouldWaitForCompletionWhenBlockTrue() throws InterruptedException {
			String taskId = "wait-task";
			String expectedResult = "Completed after wait";

			taskRepository.putTask(taskId, () -> {
				try {
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return expectedResult;
			});

			TaskOutputTool.TaskOutputCall call = new TaskOutputTool.TaskOutputCall(taskId, true, 2000L);

			String result = taskOutputFunction.apply(call);

			assertThat(result).contains("Task ID: " + taskId);
			assertThat(result).contains("Status: Completed");
			assertThat(result).contains("Result:\n" + expectedResult);
		}

		@Test
		@DisplayName("Should use default block=true when block is null")
		void shouldUseDefaultBlockTrueWhenBlockIsNull() throws InterruptedException {
			String taskId = "default-block-task";
			String expectedResult = "Result";

			taskRepository.putTask(taskId, () -> {
				try {
					Thread.sleep(50);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return expectedResult;
			});

			TaskOutputTool.TaskOutputCall call = new TaskOutputTool.TaskOutputCall(taskId, null, 1000L);

			String result = taskOutputFunction.apply(call);

			assertThat(result).contains("Status: Completed");
		}

	}

	@Nested
	@DisplayName("Timeout Tests")
	class TimeoutTests {

		@Test
		@DisplayName("Should use default timeout when timeout is null")
		void shouldUseDefaultTimeoutWhenTimeoutIsNull() {
			String taskId = "default-timeout-task";
			taskRepository.putTask(taskId, () -> {
				try {
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return "done";
			});

			TaskOutputTool.TaskOutputCall call = new TaskOutputTool.TaskOutputCall(taskId, true, null);

			String result = taskOutputFunction.apply(call);

			assertThat(result).contains("Status: Completed");
		}

		@Test
		@DisplayName("Should cap timeout at 600000ms")
		void shouldCapTimeoutAt600000ms() {
			String taskId = "capped-timeout-task";
			taskRepository.putTask(taskId, () -> {
				try {
					Thread.sleep(50);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return "done";
			});

			TaskOutputTool.TaskOutputCall call = new TaskOutputTool.TaskOutputCall(taskId, true, 999999L);

			String result = taskOutputFunction.apply(call);

			assertThat(result).contains("Status: Completed");
		}

	}

	@Nested
	@DisplayName("Error Handling Tests")
	class ErrorHandlingTests {

		@Test
		@DisplayName("Should return error message when task fails")
		void shouldReturnErrorMessageWhenTaskFails() throws InterruptedException {
			String taskId = "failed-task";
			String errorMessage = "Task execution failed";

			taskRepository.putTask(taskId, () -> {
				throw new RuntimeException(errorMessage);
			}).waitForCompletion(1000);

			TaskOutputTool.TaskOutputCall call = new TaskOutputTool.TaskOutputCall(taskId, true, null);

			String result = taskOutputFunction.apply(call);

			assertThat(result).contains("Task ID: " + taskId);
			assertThat(result).contains("Status: Failed");
			assertThat(result).contains("Error:\n" + errorMessage);
		}

		@Test
		@DisplayName("Should include cause when error has nested exception")
		void shouldIncludeCauseWhenErrorHasNestedException() throws InterruptedException {
			String taskId = "nested-error-task";
			String rootCause = "Root cause message";
			String wrapperMessage = "Wrapper exception";

			taskRepository.putTask(taskId, () -> {
				throw new RuntimeException(wrapperMessage, new IllegalArgumentException(rootCause));
			}).waitForCompletion(1000);

			TaskOutputTool.TaskOutputCall call = new TaskOutputTool.TaskOutputCall(taskId, true, null);

			String result = taskOutputFunction.apply(call);

			assertThat(result).contains("Error:\n" + wrapperMessage);
			assertThat(result).contains("Cause: " + rootCause);
		}

	}
}
