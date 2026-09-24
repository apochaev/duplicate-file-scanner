# DFS-003: Confirm duplicates with full SHA-256 hash — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-003.AC-1` | Identical files form a duplicate set | `DFS-003.AC-1 Identical files form a duplicate set` |
| `DFS-003.AC-1` | Small files go straight to the full hash | `DFS-003.AC-1 Small files go straight to the full hash` |
| `DFS-003.AC-1`, `DFS-003.AC-3` | Only the identical files form a set | `DFS-003.AC-1, DFS-003.AC-3 Only the identical files form a set` |
| `DFS-003.AC-2`, `DFS-003.AC-3` | Files differing only in the middle are separated | `DFS-003.AC-2, DFS-003.AC-3 Files differing only in the middle are separated` |
| `DFS-003.AC-2` | A group splits into two sets | `DFS-003.AC-2 A group splits into two sets` |
| `DFS-003.AC-4` | Files are hashed in path order | `DFS-003.AC-4 Files are hashed in path order` |
| `DFS-003.AC-5` | Output order follows the input and the smallest path | `DFS-003.AC-5 Output order follows the input and the smallest path` |
| `DFS-003.AC-6` | A duplicate set rejects inputs that break its definition | `DFS-003.AC-6 A blank digest is rejected` |
| `DFS-003.AC-6` | A duplicate set rejects inputs that break its definition | `DFS-003.AC-6 A null digest is rejected` |
| `DFS-003.AC-6` | A duplicate set rejects inputs that break its definition | `DFS-003.AC-6 A single file is rejected` |
| `DFS-003.AC-6` | A duplicate set rejects inputs that break its definition | `DFS-003.AC-6 A file whose size differs from the set's size is rejected` |
| `DFS-003.AC-6` | A duplicate set rejects inputs that break its definition | `DFS-003.AC-6 A null files list is rejected` |
| `DFS-003.AC-6` | A duplicate set rejects inputs that break its definition | `DFS-003.AC-6 A null element in the files list is rejected` |
| `DFS-003.AC-6` | A duplicate set rejects inputs that break its definition | `DFS-003.AC-6 A set constructed from a mutable list is unaffected when that list is changed afterwards` |
| `DFS-003.AC-7` | An unreadable file is dropped, not its whole group | `DFS-003.AC-7 An unreadable file is dropped, not its whole group` |
| `DFS-003.AC-7` | An unreadable file leaves its group with fewer than two members | `DFS-003.AC-7 An unreadable file leaves its group with fewer than two members` |
| `DFS-003.AC-8` | The real adapter computes the SHA-256 of the whole file | `DFS-003.AC-8 The real adapter computes the SHA-256 of the whole file` |
| `DFS-003.AC-8` | The real adapter tells files apart by a single byte anywhere | `DFS-003.AC-8 The real adapter tells files apart by a single byte anywhere` |
| `DFS-003.AC-9` | DFS-002's fullHash stand-in test no longer blocks the gates | `DFS-003.AC-9 fullHash returns a digest instead of throwing` (`NioContentHasherTest`, real `@TempDir` file; red against the stub, green once `fullHash` returns a 64-char lowercase hex digest). Also removed `NioContentHasherTest.fullHashIsStubbedForDfs003` (`DFS-002.AC-8 fullHash throws UnsupportedOperationException naming DFS-003`), which the scenario's first Then requires to be absent. |
| `DFS-003.AC-10` | Copies that all changed identically since the scan still form a set with the scan-time size | `DFS-003.AC-10 Copies that all changed identically since the scan still form a set with the scan-time size` |

## Fakes and fixtures used

- `ContentHasher`: faked with `FullHashConfirmerTest.FakeContentHasher`, an in-memory fake returning digests configured per path and recording every `fullHash` call's path in order; `sampleHash` throws if reached, since the full-hash stage never calls it. Follows the `RecordingContentHasher` style already used by `PartialHashFilterTest`.
- Fixture files: none from `file-scanner/src/test/resources/fixtures/`. `FullHashConfirmerTest` uses only hand-built `FileEntry` values (no real bytes needed; the fake supplies digests directly). `NioContentHasherTest`'s two new AC-8 tests generate real bytes in a `@TempDir` at test time (a 3-byte "abc" file for the known-digest case, and 300 KB pattern files differing only in the middle byte for the discrimination case), per design.md's checklist item 12; nothing is committed.

## Notes for the implementer

- `FullHashConfirmerTest`'s AC-2 near-duplicate scenario is proven with a fake returning different configured digests, not real bytes: design.md's checklist item 12 keeps digests for stage tests coming from the fake, and reserves real bytes for `NioContentHasherTest`'s AC-8 adapter tests.
- `DuplicateSetTest`'s last test (`A set constructed from a mutable list is unaffected when that list is changed afterwards`) constructs a *valid* `DuplicateSet`, so today it fails red because the stub's compact constructor throws unconditionally on every construction, not because of any invalid input — same stub reason as every other test here.
- `NioContentHasherTest.java` was edited in place (not replaced): DFS-002's stand-in test and its Javadoc paragraph were removed, the class Javadoc was rewritten to describe both `sampleHash` (DFS-002) and `fullHash` (DFS-003), and the two DFS-002 `sampleHash` tests were left untouched (they still pass; they are not story tests for DFS-003).
