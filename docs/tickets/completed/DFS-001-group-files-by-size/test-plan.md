# DFS-001: Group scanned files by size — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-001.AC-1` | Files with a unique size are dropped | `DFS-001.AC-1 Files with a unique size are dropped` |
| `DFS-001.AC-1` | Files of equal size form one candidate group | `DFS-001.AC-1 Files of equal size form one candidate group` |
| `DFS-001.AC-1` | Files are grouped across folders | `DFS-001.AC-1 Files are grouped across folders` |
| `DFS-001.AC-2` | Empty files are excluded by default | `DFS-001.AC-2 Empty files are excluded by default` |
| `DFS-001.AC-2` | A minimum size excludes smaller files | `DFS-001.AC-2 A minimum size excludes smaller files` |
| `DFS-001.AC-3` | Merging indexes with different minimum sizes is rejected | `DFS-001.AC-3 Merging indexes with different minimum sizes is rejected` |
| `DFS-001.AC-3` | Merging partial indexes equals one index | `DFS-001.AC-3 Merging partial indexes equals one index that received both files` |
| `DFS-001.AC-3` | Merging leaves the other index unchanged | `DFS-001.AC-3 Merging leaves the other index unchanged` |
| `DFS-001.AC-3` | Merging an index with an equal one changes nothing | `DFS-001.AC-3 Merging an index with an equal one changes nothing` |
| `DFS-001.AC-4` | The same path added twice counts once | `DFS-001.AC-4 The same path added twice counts once` |
| `DFS-001.AC-4` | A repeated path does not inflate a group | `DFS-001.AC-4 A repeated path does not inflate a group` |
| `DFS-001.AC-4` | Equal paths keep the later modification time | `DFS-001.AC-4 Equal paths keep the later modification time, regardless of insertion order` |
| `DFS-001.AC-5` | Output does not depend on insertion order | `DFS-001.AC-5 Output does not depend on insertion order` |
| `DFS-001.AC-5` | Groups are returned largest first | `DFS-001.AC-5 Groups are returned largest first` |
| `DFS-001.AC-6` | Returned groups are immutable | `DFS-001.AC-6 Returned groups are immutable after later additions` |
| `DFS-001.AC-7` | A candidate group enforces its invariants (single file) | `DFS-001.AC-7 A single file is rejected` |
| `DFS-001.AC-7` | A candidate group enforces its invariants (two files of different sizes) | `DFS-001.AC-7 Two files of different sizes are rejected` |
| `DFS-001.AC-7` | A candidate group enforces its invariants (file size differs from group size) | `DFS-001.AC-7 A file whose size differs from the group's size is rejected` |
| `DFS-001.AC-7` | A candidate group enforces its invariants (design's null-list/null-element commitment) | `DFS-001.AC-7 A null files list is rejected` |
| `DFS-001.AC-7` | A candidate group enforces its invariants (design's null-list/null-element commitment) | `DFS-001.AC-7 A null element in the files list is rejected` |
| `DFS-001.AC-7` | Invalid arguments are rejected (negative minimum size) | `DFS-001.AC-7 A negative minimum size is rejected` |
| `DFS-001.AC-7` | Invalid arguments are rejected (null entry) | `DFS-001.AC-7 A null entry is rejected` |

## Fakes and fixtures used

- No ports, no fakes: `SizeIndex` and `CandidateGroup` are pure in-memory `application` logic with no dependency on `infra`. Tests build `FileEntry` values directly in code with `Path.of(...)` and fixed `Instant`s; nothing touches the real filesystem.
- No shared fixtures from `file-scanner/src/test/resources/fixtures/` are used: this stage depends only on `FileEntry.size()`, not file content, and the fixture directory exists for content-hashing stages (DFS-002/003).

## Notes for the implementer

- The invalid-input tests (`CandidateGroupTest`'s five and `SizeIndexTest`'s two) use a `try { ...; fail(...); } catch (ExpectedType expected) {}` pattern rather than `assertThrows`, per the archetype's `test-guide.md`: the stub throws `UnsupportedOperationException` unconditionally before any precondition check, and `assertThrows` against a narrower type would mask that behind its own `AssertionFailedError`. Once real validation exists, these tests pass by catching the documented exception type (`IllegalArgumentException` or `NullPointerException`) and fail if the call instead returns normally or throws something else.
- `CandidateGroupTest`'s null-list and null-element tests are not spelled out as their own Gherkin scenario in the ticket; they come from `design.md`'s "Public API" section and its checklist-disposition item 2, which commit `CandidateGroup`'s compact constructor to rejecting a null `files` list and a null element via `List.copyOf`. Flagged in `learnings.md` as a design-doc-only commitment, not a ticket-scenario gap.
- `SizeIndex.merge(null)` (documented in `design.md`'s Public API section and the stub's Javadoc as throwing `NullPointerException`) is intentionally **not** tested here: the ticket's AC-7 scenario ("Invalid arguments are rejected") names only a negative minimum size and a null *entry* (i.e. `add`), not a null `other` for `merge`. Flagged in `learnings.md` as a gap between the ticket's scenarios and the design doc/stub Javadoc; not added per the instruction not to invent scenarios beyond what the ticket states.
- `DFS-001.AC-1 Files of equal size form one candidate group` and the AC-2/AC-3/AC-4 group-content assertions rely on `CandidateGroup`'s and `FileEntry`'s record `equals`, so once real logic exists a group is compared by value, not by identity or ordering tricks.
