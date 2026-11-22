# Project Instructions

## Default Language and Localization

- `src/main/resources/assets/slimefunhelper/lang/en_us.json` is used to record Chinese localization. Despite its filename, translation values default to Chinese.
- Module names (`widget.click-gui.module-name.*`) and module list names (`module-toggle.*`) default to English.
- These English exceptions apply only to name values. Tooltips, descriptions, and all other text default to Chinese, including tooltip keys under these prefixes (such as `widget.click-gui.module-name.*.tooltips`).
- Follow this convention when adding or updating translations; do not infer English as the default from the `en_us.json` filename.

## Verification

- Prefer targeted static verification when appropriate.
- After substantial changes, inspect `git diff`.
- Use `git diff --check` to detect whitespace errors.

## Minecraft Source Code and Runtime Mixins

- The parent directory of this workspace contains Minecraft 1.21.11 source code and the corresponding runtime Mixin code.
- These sources are available for reference during development, debugging, and static verification.
- When investigating Minecraft internals, prefer inspecting the available Minecraft 1.21.11 source code instead of guessing implementation details.
- When investigating Mixin behavior, inspect the corresponding runtime Mixin code to understand actual injection points, modified methods, and potential compatibility issues.
- Use these sources to verify method signatures, mappings, execution flow, and version-specific behavior.
- Distinguish between vanilla Minecraft behavior and behavior introduced or modified by Mixins.
- Do not modify the reference Minecraft source code or runtime Mixin code unless explicitly requested by the user.
- Reading and analyzing these sources does not require compilation or execution.