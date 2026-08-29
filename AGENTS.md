# Repository Guidelines

## Project Structure & Module Organization

CareLipik is a Kotlin clinical documentation assistant, not an autonomous diagnosis or prescription system. The main consultation flow must work offline.

This is intentionally a single-module app. Do not add modules or dependencies without a concrete current need.

- Kotlin, Jetpack Compose, and Material 3 UI: `app/src/main/java/com/carelipik/app/`
- Theme code: `app/src/main/java/com/carelipik/app/ui/theme/`
- Android resources and manifest: `app/src/main/res/` and `app/src/main/AndroidManifest.xml`
- Local unit tests: `app/src/test/`
- Device/emulator tests: `app/src/androidTest/`
- Dependency and plugin versions: `gradle/libs.versions.toml`

Use MVVM with `ViewModel` and `StateFlow`. Separate Compose UI, domain logic, and infrastructure. Place feature code under `com.carelipik.app`.

## Development Approach

Build one independently testable component at a time. Define interfaces before real AI engines. Supply fake audio-transcription and clinical-extraction implementations so UI work never depends on models.

Do not silently change dependency or plugin versions. Do not implement a cloud backend unless explicitly requested.

## Build, Test, and Verification

Run commands from the repository root, using Android Studio's configured Gradle JDK or a configured `JAVA_HOME`.

```sh
./gradlew test             # Required for domain/business-logic changes
./gradlew lint             # Required after Android changes
./gradlew assembleDebug    # Required before a component is complete
./gradlew connectedDebugAndroidTest  # Device/emulator verification
```

Add tests for new business logic. Explain any verification that still requires a physical phone; `connectedDebugAndroidTest` needs an emulator or device.

## Coding Style & Naming Conventions

Use four-space Kotlin indentation and the official style in `gradle.properties`. Use `PascalCase` for classes, Compose functions, and test classes; use `camelCase` for functions, properties, and parameters. Name composables for what they render, for example `ConsultationSummaryCard`.

No dedicated formatter is configured. Follow existing Kotlin/Compose patterns and run lint before submitting changes.

## Testing Guidelines

Use JUnit 4 locally and AndroidX JUnit/Espresso for instrumented tests. Name files `*Test.kt` and methods for expected behavior, for example `extraction_returnsFallback_whenEngineUnavailable`. Never include real patient information in tests, screenshots, fixtures, or sample data.

## Security, Git, and Task Reporting

Never commit secrets, tokens, `local.properties`, signing keys, model binaries, patient data, or consultation recordings.

Never commit directly to `main`. Do not push, merge, force-push, or delete branches without explicit permission. Keep each change limited to the current component.

After every task, summarize changed files and test results. Pull requests should describe the component, link issues, and include UI screenshots.
