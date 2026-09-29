# Project Instructions

## Build

- Do not run Gradle compilation or build commands unless explicitly requested by the user.
- Compilation is not part of the normal modification workflow for this project.
- Do not investigate, troubleshoot, repair, upgrade, or modify Gradle, Gradle Wrapper, Gradle plugins, or build configuration unless explicitly requested by the user.
- Do not spend time diagnosing Gradle environment or wrapper failures when the requested task does not require compilation.
- If a Gradle command is run explicitly and fails because of the environment, report the failure and stop investigating it. Do not try alternative Gradle versions, modify wrapper files, download Gradle distributions, or change build configuration unless explicitly requested.

## Verification

- Prefer targeted static verification over compilation.
- After substantial changes, inspect `git diff`.
- Use `git diff --check` to detect whitespace errors.
- Use Spotless for formatting verification when applicable.
- Run `.\gradlew spotlessCheck` when formatting verification is appropriate.
- If `spotlessCheck` reports formatting violations and automatic formatting is appropriate, run `.\gradlew spotlessApply`, then inspect `git diff`.
- If Spotless cannot run because of Gradle or environment configuration, report the failure and continue with targeted static verification.
- Do not investigate Gradle merely because Spotless cannot run.