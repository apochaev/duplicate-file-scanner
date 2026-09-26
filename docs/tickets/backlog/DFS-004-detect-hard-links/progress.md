## design (architect, attempt 1)

- Wrote `design.md` and stubs: `application/HardLinkDetector.java` (constructor + `detect`, both throw), `infra/port/FileIdentity.java` (interface), `infra/adapter/NioFileIdentity.java` (`isSameFile` throws). All throw `not implemented: DFS-004`.
- No change to `domain.model`, `module-info.java` or `FullHashConfirmer`.
- `./gradlew :file-scanner:compileJava :file-scanner:compileTestJava` succeeds.
- Expected commit: `feat(file-scanner): design hard-link stage stubs [DFS-004]` (orchestrator commits).
