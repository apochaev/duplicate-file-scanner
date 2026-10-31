# DFS-031 progress

## design (architect, 2026-10-31)

- Wrote `design.md` (wiring archetype; checklist items 1-11 disposed, Expected breakage: None).
- New subproject `scanner-spring`: `settings.gradle.kts` includes it; `scanner-spring/build.gradle.kts` (java-library, Boot BOM 4.1.1, `api(project(":file-scanner"))`, `spring-boot-autoconfigure`; test: `spring-boot-test`, `assertj-core`).
- Stub `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java`: six `@Bean` methods, each throwing `not implemented: DFS-031`; registered in `scanner-spring/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- `cli/build.gradle.kts` adds `implementation(project(":scanner-spring"))`; `cli`'s `ScannerConfiguration.java` and `ScannerConfigurationTest.java` deleted (staged with `git rm`) so the CLI reaches the stubs at red.
- Verified: `:scanner-spring:compileJava :cli:compileJava :cli:compileTestJava` compile; existing `CliApplicationTest` fails with root cause `UnsupportedOperationException: not implemented: DFS-031` (autoconfiguration picked up through the imports file).
- Expected commit: `refactor(cli): stub scanner-spring autoconfiguration [DFS-031]` (design.md, progress.md, learnings.md, the files above).
