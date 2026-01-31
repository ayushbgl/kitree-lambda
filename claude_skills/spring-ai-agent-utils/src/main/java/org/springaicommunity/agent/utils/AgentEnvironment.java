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
package org.springaicommunity.agent.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AgentEnvironment {

	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

	public static final String ENVIRONMENT_INFO_KEY = "ENVIRONMENT_INFO";

	public static final String GIT_STATUS_KEY = "GIT_STATUS";

	public static final String AGENT_MODEL_KEY = "AGENT_MODEL";

	public static final String AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY = "AGENT_MODEL_KNOWLEDGE_CUTOFF";

	public static String info() {

		String workingDirectory = System.getProperty("user.dir");
		boolean isGitRepo = new File(workingDirectory, ".git").exists();
		String platform = System.getProperty("os.name").toLowerCase();
		String osVersion = System.getProperty("os.name") + " " + System.getProperty("os.version");
		String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

		StringBuilder sb = new StringBuilder();
		sb.append("Working directory: ").append(workingDirectory).append("\n");
		sb.append("Is directory a git repo: ").append(isGitRepo ? "Yes" : "No").append("\n");
		sb.append("Platform: ").append(platform).append("\n");
		sb.append("OS Version: ").append(osVersion).append("\n");
		sb.append("Today's date: ").append(todayDate).append("\n");

		return sb.toString();
	}

	public static String gitStatus() {

		// Check if git is available
		if (!isGitAvailable()) {
			System.out.println("Git is not available or not in PATH.\n");
			return "";
		}

		// Check if we're in a git repository
		String gitCheck = runGitCommand("rev-parse", "--is-inside-work-tree");
		if (!"true".equals(gitCheck)) {
			System.out.println("Not inside a git repository.\n");
			return "";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("gitStatus: This is the git status at the start of the conversation. ");
		sb.append("Note that this status is a snapshot in time, and will not update during the conversation.\n");

		// Get current branch
		String currentBranch = runGitCommand("rev-parse", "--abbrev-ref", "HEAD");
		sb.append("Current branch: ").append(currentBranch).append("\n\n");

		// Get main/master branch (for PRs)
		String mainBranch = getMainBranch();
		sb.append("Main branch (you will usually use this for PRs): ").append(mainBranch).append("\n\n");

		// Get git status
		String status = runGitCommand("status", "--short");
		sb.append("Status:\n").append(status.isEmpty() ? "Working tree clean\n\n" : status).append("\n\n");

		// Get recent commits
		String recentCommits = runGitCommand("log", "--oneline", "-n", "5");
		sb.append("Recent commits:\n").append(recentCommits);

		return sb.toString();
	}

	private static boolean isGitAvailable() {
		try {
			String result = runGitCommand("--version");
			return result != null && result.contains("git version");
		}
		catch (Exception e) {
			return false;
		}
	}

	private static String getMainBranch() {
		// Try to detect the main branch name
		String[] possibleMains = { "main", "master" };
		for (String branch : possibleMains) {
			String result = runGitCommand("rev-parse", "--verify", "--quiet", branch);
			if (result != null && !result.isEmpty() && !result.toLowerCase().contains("fatal")) {
				return branch;
			}
		}
		// Try to get from remote
		String remoteBranch = runGitCommand("symbolic-ref", "refs/remotes/origin/HEAD", "--short");
		if (remoteBranch != null && !remoteBranch.isEmpty()) {
			return remoteBranch.replace("origin/", "");
		}
		return "main";
	}

	/**
	 * Runs a git command in a cross-platform manner. On Windows, uses cmd.exe /c to
	 * ensure proper command execution. On Unix/Mac, runs git directly.
	 */
	private static String runGitCommand(String... gitArgs) {
		try {
			List<String> command = new ArrayList<>();

			if (IS_WINDOWS) {
				command.add("cmd.exe");
				command.add("/c");
				command.add("git");
			}
			else {
				command.add("git");
			}

			for (String arg : gitArgs) {
				command.add(arg);
			}

			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(System.getProperty("user.dir")));
			pb.redirectErrorStream(true);

			// Set environment to ensure consistent output
			pb.environment().put("LC_ALL", "C");
			pb.environment().put("LANG", "C");

			Process process = pb.start();

			String result;
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				result = reader.lines().collect(Collectors.joining("\n"));
			}

			// Wait with timeout to prevent hanging
			boolean finished = process.waitFor(30, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				return "";
			}

			return result.trim();
		}
		catch (Exception e) {
			return "";
		}
	}

}
