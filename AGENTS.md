# Project Instructions

## Build

- Do not run Gradle compilation or build commands unless explicitly requested by the user.
- Compilation is not part of the normal code modification workflow.
- When working on code-related tasks, do not run Gradle build or compilation commands if the current Git branch does not start with `feat/`.
- Before running any Gradle build or compilation command, verify the current Git branch using `git branch --show-current`.
- Gradle build and compilation commands are permitted only when:
    1. The user explicitly requests compilation or building.
    2. The current Git branch starts with `feat/`.
- If the current branch does not start with `feat/`, skip Gradle build and compilation. Continue the requested code task using static verification.
- Do not automatically create or switch branches to bypass this restriction.
- Do not investigate, troubleshoot, repair, upgrade, or modify Gradle, Gradle Wrapper, Gradle plugins, or build configuration unless explicitly requested by the user.
- Do not spend time diagnosing Gradle environment or wrapper failures when the requested task does not require compilation.
- If an explicitly requested Gradle command fails because of the environment, report the failure and stop investigating it. Do not try alternative Gradle versions, modify wrapper files, download Gradle distributions, or change build configuration unless explicitly requested.

## Verification

- Prefer targeted static verification over compilation.
- After substantial changes, inspect `git diff`.
- Use `git diff --check` to detect whitespace errors.
- Use Spotless for formatting verification when applicable.
- Gradle-based formatting verification must respect the branch restrictions defined in the Build section.
- Run `.\gradlew spotlessCheck` only when:
    1. Formatting verification is appropriate.
    2. The current Git branch starts with `feat/`.
- If `spotlessCheck` reports formatting violations and automatic formatting is appropriate, run `.\gradlew spotlessApply`, then inspect `git diff`.
- On non-`feat/` branches, skip Gradle-based formatting tasks and use targeted static verification instead.
- If Spotless cannot run because of Gradle or environment configuration, report the failure and continue with targeted static verification.
- Do not investigate Gradle merely because Spotless cannot run.
- Do not run Gradle build or compilation commands as part of routine verification.

## Minecraft Source Code and Runtime Mixins

- The parent directory of this workspace contains Minecraft 1.21.11 source code and the corresponding runtime Mixin code.
- These sources are available for reference during development, debugging, and static verification.
- When investigating Minecraft internals, prefer inspecting the available Minecraft 1.21.11 source code instead of guessing implementation details.
- When investigating Mixin behavior, inspect the corresponding runtime Mixin code to understand actual injection points, modified methods, and potential compatibility issues.
- Use these sources to verify method signatures, mappings, execution flow, and version-specific behavior.
- Distinguish between vanilla Minecraft behavior and behavior introduced or modified by Mixins.
- Do not modify the reference Minecraft source code or runtime Mixin code unless explicitly requested by the user.
- Reading and analyzing these sources does not require Gradle compilation or execution.

## Verified Windows Build Environment

This workspace targets Java 21. The verified paths on this workstation are:

- `GRADLE_HOME=C:\gradle\gradle-9.2.0`
- `GRADLE_USER_HOME=C:\Users\mtl\.gradle` (contains the Gradle distribution and dependency caches)
- `JAVA_HOME=C:\Intellij_Idea\jetbrainRuntime\jbr_jcef-21.0.7-windows-x64-b1038.54`

For an explicitly requested build, first verify that the current Git branch starts with `feat/`, then invoke the cached Gradle installation directly:

```powershell
$branch = git branch --show-current

if (-not $branch.StartsWith('feat/')) {
    throw "Gradle builds are only permitted on feat/ branches. Current branch: $branch"
}

$env:GRADLE_HOME = 'C:\gradle\gradle-9.2.0'
$env:GRADLE_USER_HOME = 'C:\Users\mtl\.gradle'
$env:JAVA_HOME = 'C:\Intellij_Idea\jetbrainRuntime\jbr_jcef-21.0.7-windows-x64-b1038.54'

$gradleExe = Join-Path $env:GRADLE_HOME 'bin\gradle.bat'

& $gradleExe runClient
```

The workspace `gradlew.bat` redirects `distributionUrl` to the local Gradle ZIP and fails on a later invocation when the value is already identical. Direct invocation avoids that wrapper side effect.

Update these workstation-specific paths when building elsewhere.