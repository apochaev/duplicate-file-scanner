# DFS-002: Filter candidates by head-and-tail partial hash — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-002.AC-1` | Files that differ in their first bytes are separated | `DFS-002.AC-1 Files that differ in sampled bytes are separated` |
| `DFS-002.AC-2` | Files that differ only in the middle survive the sample | `DFS-002.AC-2 Files identical in the sample but differing only in the middle survive` |
| `DFS-002.AC-3` | Identical files survive | `DFS-002.AC-3 Identical files survive the stage` |
| `DFS-002.AC-4` | Small files skip the stage | `DFS-002.AC-4 Small files bypass the sample entirely` |
| `DFS-002.AC-5` | Groups of one are dropped after the stage | `DFS-002.AC-5 A group left with fewer than two survivors is dropped` |
| `DFS-002.AC-6` | Files are hashed in path order | `DFS-002.AC-6 Members are sampled in ascending path order` |
| `DFS-002.AC-7` | An unreadable file is dropped, not its whole group | `DFS-002.AC-7 An unreadable file is dropped, not its whole group` |
| `DFS-002.AC-7` | An unreadable file leaves its group with fewer than two members | `DFS-002.AC-7 An unreadable file leaving fewer than two members drops the group` |
| `DFS-002.AC-8` | The full-hash method is stubbed for DFS-003 | `DFS-002.AC-8 fullHash throws UnsupportedOperationException naming DFS-003` |
| `DFS-002.AC-9` | A custom sample size and threshold change stage behavior | `DFS-002.AC-9 The configured sample size is passed to the hasher instead of the default` (cheap, fake hasher: verifies the parameter reaches the hasher) and `DFS-002.AC-9 A custom sample size and threshold change stage behavior` (real bytes: verifies the actual outcome at the custom threshold) |

One row per scenario; AC-9's one ticket scenario is covered by two tests (a cheap parameter check and a real-byte correctness check — CLAUDE.md's "cheap tests first" rule plus the near-duplicate-content rule), and AC-7 has two ticket scenarios, each with its own test.

## Fakes and fixtures used

- `ContentHasher`: faked with `RecordingContentHasher`, a private nested fake in `PartialHashFilterTest` that returns a digest configured per `Path` (or throws a simulated `IOException` for a path marked unreadable) and records every `sampleHash` call (path and sample size) for order/parameter assertions. Used for AC-1, AC-3, AC-4, AC-5, AC-6, AC-7 (both scenarios) and the cheap half of AC-9.
- Fixture files: none from `file-scanner/src/test/resources/fixtures/` — every test uses hand-built `FileEntry` values (fake-hasher tests) or files generated at test time in `@TempDir` (real-byte tests), since no scenario needs the shared fixture tree's shapes.
- Generated-in-code files (never committed): `PartialHashFilterRealFileTest` writes two same-size files per test with a repeating single-byte pattern, flipping one byte past the sample boundary in the second file — a 300 KB pair (AC-2, default 64 KB sample / 128 KB threshold) and a 10 KB pair (AC-9, custom 4 KB sample / 8 KB threshold). These exercise the real `NioContentHasher` adapter through `PartialHashFilter`, per CLAUDE.md's rule that content-dependent, over-threshold behavior needs real bytes, not a fake digest.

## Notes for the implementer

- `PartialHashFilterTest`'s `RecordingContentHasher.fullHash` throws unconditionally (a different message than the DFS-002 stub) because this stage must never call it; if a test ever fails there, `filter` is calling `fullHash` instead of `sampleHash`.
- `DFS-002.AC-8`'s test (`NioContentHasherTest`) checks only that `fullHash` throws `UnsupportedOperationException`. While the method still carries the generic stub (message starting `not implemented:`), it rethrows the caught exception unchanged, so it starts red with the raw stub exception. After you swap in the DFS-003 stand-in it goes green. The test does not assert the exact wording. Produce the wording that design.md's "Open design notes" specify (`"full hash is implemented by DFS-003"`, with no `not implemented:` prefix); the verifier checks it by eye.
- No test constructs `PartialHashFilter` or calls `NioContentHasher.sampleHash` with invalid arguments (null hasher, non-positive sample size, negative threshold, null `file`/`groups`). The ticket's nine acceptance criteria and scenarios do not name any invalid-input case for this stage (unlike DFS-001's dedicated AC-7), so none was added — see the test-author's final report for this flagged as a possible gap rather than invented silently.
