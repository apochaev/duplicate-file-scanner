# DFS-004: Detect hard links within candidate groups — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-004.AC-2` | Two hard links to one file are not duplicates | `DFS-004.AC-2 Two hard links to one file are not duplicates` |
| `DFS-004.AC-1`, `DFS-004.AC-3` | A hard-linked pair counts as one copy | `DFS-004.AC-1, DFS-004.AC-3 A hard-linked pair counts as one copy` |
| `DFS-004.AC-3` | Independent identical files remain separate copies | `DFS-004.AC-3 Independent identical files remain separate copies` |
| `DFS-004.AC-4` | Identity is only checked inside a set, against each copy's first link | `DFS-004.AC-4 Identity is only checked inside a set, against each copy's first link` |
| `DFS-004.AC-5` | Output order follows the input, the first path and the path order | `DFS-004.AC-5 Output order follows the input, the first path and the path order` |
| `DFS-004.AC-8` | A file whose identity cannot be checked is dropped, not its set | `DFS-004.AC-8 A file whose identity cannot be checked is dropped, not its set` |
| `DFS-004.AC-8` | Dropping an unreadable file leaves fewer than two copies | `DFS-004.AC-8 Dropping an unreadable file leaves fewer than two copies` |
| `DFS-004.AC-9` | The real adapter recognises a hard link | `DFS-004.AC-9 Two hard links to one file are recognised as the same file` |
| `DFS-004.AC-9` | The real adapter tells independent identical files apart | `DFS-004.AC-9 Independent identical files are told apart` |

AC-6 and AC-7 are retired (moved to DFS-017); no tests here.

## Fakes and fixtures used

- `FileIdentity`: faked in `HardLinkDetectorTest` with a hand-written `FakeFileIdentity` (nested, private). It is told which unordered path pairs are the same physical file (`sameFile(a, b)`, symmetric) and which paths are unreadable (`unreadable(path)`, throwing a simulated `IOException` from any call naming that path). It records every call, in argument order, as `(a, b)` pairs for the AC-4 ordering assertion.
- `NioFileIdentityTest` uses real files in a `@TempDir`: a hard link created at test time with `Fixtures.createHardLinkOrSkip` (already present in `infra.adapter`'s test `Fixtures`, from an earlier ticket), which calls `Assumptions.abort` if `Files.createLink` fails. No fixture files from `src/test/resources/fixtures` are used; the stage reads no content, only identity, so bytes are generated at test time only to give the files real inode-backed existence.

## Notes for the implementer

- AC-8's two scenarios deliberately make the unreadable file a *non-first* path within its set (e.g. `b.bin` among `a.bin`, `b.bin`, `c.bin`), per design.md's "Open design notes": the algorithm attributes a thrown `IOException` to the candidate being placed, never to an existing copy's first link, so an unreadable *first* link would behave differently (every later file checked against it would be dropped instead, rather than the first-link path itself). This ticket's scenarios only exercise the candidate-side failure.
- AC-5's expected outputs are compared with `assertEquals` on whole `DuplicateSet`/`PhysicalCopy` values (both records), rather than field-by-field, since the canonical 4-argument constructor's structural equality is exactly what the scenario describes (input order, first-path copy order, ascending links).
- No null-argument tests are included: the ticket's live criteria (AC-1..AC-5, AC-8, AC-9) do not tag any null-rejection scenario, so none was added, per the instruction not to invent scenarios beyond the ticket. `design.md`'s public API section does document `NullPointerException` on null arguments; that is left for a future ticket or an explicit scope decision if it needs coverage.
