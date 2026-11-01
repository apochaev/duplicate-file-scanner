---
id: DFS-032
title: List one directory level
status: in-review
type: feature
scope: [file-scanner]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [done]
autorun: true
priority: medium
lane: backend
depends_on: [DFS-022]
touches:
  - us.pochaev.dfs.scanner.domain.usecases.query
  - us.pochaev.dfs.scanner.domain.model
  - us.pochaev.dfs.scanner.application
  - us.pochaev.dfs.scanner.domain.factory
  - us.pochaev.dfs.scanner.infra.port
  - us.pochaev.dfs.scanner.infra.adapter
  - scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java
  - scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java
blocked_reason:
created: 2026-10-31
updated: 2026-11-01
completed:
---

# DFS-032: List one directory level

## Story

As a user exploring a scan in the local UI
I want to see every entry of one folder, including files that have no duplicate
So that I can judge a folder as a whole before deciding what to delete in it

## Context

A scan report lists only duplicated files, so the UI's folder detail view (`docs/design/ui.md` U4, server ticket 8) needs to read a folder itself. The use case is `ListDirectory(path)`: one level deep, read-only, following the same rules as `FileTreeWalker`/`NioFileTreeWalker`: regular files and plain directories only; symlinks, junctions and other reparse points reported, never followed; unreadable entries reported (CLAUDE.md safety invariants). A query port in `domain.usecases.query` (`ui.md`, "What the UI needs from the core").

U4 also marks each entry against the scan (duplicate, unique, skipped, excluded, new since scan, missing since scan). The scan does not record what all six marks need: it names only duplicated and skipped files, plus a direct count per folder (`FolderTotal`), so a file on disk in no set cannot be told apart as "unique" or "new since scan"; and `ScanResult.excluded` is only a count. The marks are therefore left to a follow-up, once U4 is reconciled with what the scan records (decision 1).

What exists: `SkipReason` (`SYMLINK`, `SPECIAL_FILE`, `UNREADABLE`), `NioFileTreeWalker`'s classification of an entry (regular file, plain directory, symlink, Windows junction reported as `isOther()` directory), `Result`/`ErrorCode` with `LOCATION_NOT_FOUND`, `NOT_A_DIRECTORY` and `LOCATION_UNREADABLE`, and, from DFS-022, the pattern of a stateless query exposed by its own `ScannerFactories` method and a `ScannerAutoConfiguration` bean.

### Decisions

1. **The query lists the disk only; matching against the scan is a follow-up.** `ListDirectory.list(Path folder)` returns `Result<DirectoryListing>` and takes no `ScanResult`. Each entry carries what the disk says; marking entries against a scan is a separate pure function in a later ticket, after U4's marks are reconciled with what the scan records (Context).
2. **Entries are a sealed type, ordered by name.** `DirectoryListing(Path folder, List<DirectoryEntry> entries)`. `DirectoryEntry` is a sealed interface with `FileItem(String name, Path path, long size, Instant lastModified)`, `FolderItem(String name, Path path, Instant lastModified)` and `SkippedItem(String name, Path path, SkipReason reason, String detail)`. `path` is `folder.resolve(name)`, so it has the form the caller passed in (the scan's paths, when the caller passes a scanned folder). Entries are ordered by `Path.compareTo` of their names (the platform's case rule), kinds mixed. Every regular file is listed, including empty files and names the scan leaves out by default (`.DS_Store`, `._*`): the listing shows the disk, and the scan's size and name filters belong to the marks (decision 1). Hidden files are listed like any other.
3. **Same classification as the walker, nothing followed, never fails for one child.** A symlink child is `SkippedItem(SYMLINK)`; a junction or other reparse point, device, FIFO or socket is `SkippedItem(SPECIAL_FILE)`; a child whose attributes cannot be read (access denied, removed while listing) is `SkippedItem(UNREADABLE)` with the exception's message as `detail`. A link child's target is never read. Attributes are read without following links. One bad child never fails the listing.
4. **Failures are values; a link given as the folder is refused.** Checked before listing, on the caller's thread: a folder that does not exist gives `Failure(LOCATION_NOT_FOUND)`; a regular file or other non-directory gives `Failure(NOT_A_DIRECTORY)`; a symlink, junction or other reparse point given as the folder gives `Failure(LOCATION_IS_LINK)` (new `ErrorCode`), because the listing never follows links, unlike a scan root (the UI only navigates into `FolderItem`s, so a link reaches `list` only by a typed path); a folder that cannot be opened or read to the end gives `Failure(LOCATION_UNREADABLE)` with the exception's message. A `null` argument throws `NullPointerException`. The existing codes' Javadoc is widened from "start location" to cover a listed folder.
5. **A new secondary port, not the walker.** `FileTreeWalker` reports only regular files and skips, never a directory as an entry, and walks recursively; bending it to one level would change a port every scan uses. So `infra.port.DirectoryReader` (one method, reading one folder's children with their attributes) and its adapter `infra.adapter.NioDirectoryReader` (`Files.newDirectoryStream`, attributes read with `NOFOLLOW_LINKS`). The classification is shared with `NioFileTreeWalker` through a package-private helper in `infra.adapter` rather than copied, so the two cannot drift; `NioFileTreeWalker`'s behavior is unchanged (its existing tests stay green unchanged). The application service is field-less apart from the port.
6. **Exposure like DFS-022; not limited to the scan's roots.** `ScannerFactories.listDirectory()` returns a `ListDirectory` built on the NIO adapter, and `ScannerAutoConfiguration` gains a `ListDirectory` bean with `@ConditionalOnMissingBean`. The core lists any folder it is given; refusing paths outside the scan's roots is the server's (`ui.md` U10, server ticket 8). `depends_on: [DFS-022]`: both tickets edit `ScannerFactories`, `ScannerAutoConfiguration` and `ErrorCode`, so this one starts once DFS-022 has merged, from its pattern. Use-case checklist items 1, 3, 4, 6, 7, 8, 9, 11 and 12 do not apply: the query takes no ID, keeps no state, calls no caller code and runs synchronously on the caller's thread.
7. **Gates `[done]`.** The query only reads; nothing is planned or deleted, so the run goes unattended to the product-owner review.

## Acceptance criteria

The full ID of each criterion is `DFS-032.AC-<n>`.

- **AC-1** `list(folder)` returns `Success` with a `DirectoryListing` holding exactly one entry per direct child of `folder`, and nothing from inside its subfolders.
- **AC-2** A regular file is a `FileItem` with its name, `folder.resolve(name)` as path, its size and its last-modified time; empty files, `.DS_Store` and `._*` files are listed like any other.
- **AC-3** A plain subfolder is a `FolderItem` with its name, path and last-modified time.
- **AC-4** A symlink child is a `SkippedItem` with reason `SYMLINK` and a junction (or other reparse-point directory) is a `SkippedItem` with reason `SPECIAL_FILE`; neither is followed, so nothing from their targets appears.
- **AC-5** A child whose attributes cannot be read is a `SkippedItem` with reason `UNREADABLE` and a non-empty detail, and the listing still returns `Success` with the other children.
- **AC-6** Entries are ordered by `Path.compareTo` of their names, whatever their kind.
- **AC-7** `list` returns `Failure(LOCATION_NOT_FOUND)` for a missing folder, `Failure(NOT_A_DIRECTORY)` for a regular file, `Failure(LOCATION_IS_LINK)` for a symlink or junction given as the folder, and `Failure(LOCATION_UNREADABLE)` for a folder that cannot be read; a `null` folder throws `NullPointerException`.
- **AC-8** Listing changes nothing on disk: the folder's children, their sizes and their last-modified times are the same before and after.
- **AC-9** `ScannerFactories.listDirectory()` returns a working `ListDirectory`, and `ScannerAutoConfiguration` provides a `ListDirectory` bean that backs off when the application declares its own.

## Scenarios

```gherkin
Feature: List one directory level

  @AC-1 @AC-2 @AC-3
  Scenario: Files and subfolders of one level are listed, not their contents
    Given a temp folder T with files a.txt (3 bytes) and b.jpg (5 bytes) and a subfolder sub holding c.txt
    When T is listed
    Then the listing holds exactly a.txt, b.jpg and sub
    And a.txt is a FileItem with path T/a.txt, size 3 and its last-modified time
    And sub is a FolderItem with path T/sub
    And c.txt is not in the listing

  @AC-2
  Scenario: Files the scan leaves out are still listed
    Given a temp folder T with an empty file empty.txt, a file .DS_Store and a file ._x.jpg
    When T is listed
    Then all three are FileItems, empty.txt with size 0

  @AC-1
  Scenario: An empty folder gives an empty listing
    Given an empty temp folder T
    When T is listed
    Then it returns Success with no entries

  @AC-4
  Scenario: A symlink child is reported, not followed
    Given a temp folder T with a file a.txt and a symlink link pointing to a folder holding secret.txt
    And the platform allows creating symlinks (otherwise the test is skipped)
    When T is listed
    Then link is a SkippedItem with reason SYMLINK
    And secret.txt appears nowhere in the listing

  @AC-4
  Scenario: A junction child is reported, not followed
    Given on Windows a temp folder T with a junction j pointing to a folder holding secret.txt (skipped on other platforms)
    When T is listed
    Then j is a SkippedItem with reason SPECIAL_FILE
    And secret.txt appears nowhere in the listing

  @AC-5
  Scenario: A child that cannot be read does not fail the listing
    Given a folder whose children are a.txt and a child whose attributes cannot be read (simulated through the DirectoryReader seam or adapter as design.md decides)
    When the folder is listed
    Then the listing is Success with a.txt as a FileItem
    And the unreadable child is a SkippedItem with reason UNREADABLE and a non-empty detail

  @AC-6
  Scenario: Entries are ordered by name, kinds mixed
    Given a temp folder T with files b.txt and d.txt and subfolders a and c
    When T is listed
    Then the entries are, in order, a, b.txt, c, d.txt

  @AC-7
  Scenario: A missing folder or a file is refused
    Given a temp folder T holding the file f.txt
    When T/missing is listed
    Then it returns Failure with code LOCATION_NOT_FOUND
    And listing T/f.txt returns Failure with code NOT_A_DIRECTORY

  @AC-7
  Scenario: A link given as the folder is refused
    Given a temp folder T and a symlink L pointing to T (skipped when the platform does not allow it)
    When L is listed
    Then it returns Failure with code LOCATION_IS_LINK
    And on Windows a junction J pointing to T, when listed, also returns Failure with code LOCATION_IS_LINK

  @AC-7
  Scenario: A folder that cannot be read is refused, and null is a caller bug
    Given a folder that cannot be opened (simulated as design.md decides)
    When it is listed
    Then it returns Failure with code LOCATION_UNREADABLE and the exception's message
    And list(null) throws NullPointerException

  @AC-8
  Scenario: Listing changes nothing
    Given a temp folder T with files a.txt, b.jpg and a subfolder sub, whose names, sizes and last-modified times are recorded
    When T is listed
    Then T's names, sizes and last-modified times are unchanged

  @AC-9
  Scenario: The factory and the Spring wiring expose the listing
    Given ScannerFactories.listDirectory()
    When it lists a temp folder holding a.txt
    Then the listing holds a.txt
    And an application context with ScannerAutoConfiguration has one ListDirectory bean that lists the same folder
    And a context that declares its own ListDirectory bean uses that one instead
```

## Scope

**Touches:**
- `domain.usecases.query`: the `ListDirectory` port
- `domain.model`: `DirectoryListing`, `DirectoryEntry` and its variants, `ErrorCode.LOCATION_IS_LINK` and the widened Javadoc of the existing codes
- `application`: the service implementing `ListDirectory`
- `domain.factory`: `ScannerFactories.listDirectory()`
- `infra.port`: `DirectoryReader`
- `infra.adapter`: `NioDirectoryReader` and the classification helper shared with `NioFileTreeWalker`
- `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java`: the `ListDirectory` bean
- `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java`: its bean and back-off tests

**Out of scope:**
- Recursion, and any write to disk.
- Marking entries against a scan (duplicate, unique, skipped, excluded, new or missing since scan): a follow-up once `ui.md` U4 is reconciled with what the scan records (decision 1).
- Refusing paths outside the scan's roots: the server's (`ui.md` U10).
- The server endpoint (frontend session).
- Paging or sorting options: the UI sorts and pages the whole listing.

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] `design.md` addresses every item of the `use-case` edge-case checklist or marks it not applicable with a one-line reason (decision 6 lists the ones expected not to apply)
- [x] `NioFileTreeWalker`'s existing tests pass unchanged after the shared classification helper is introduced
- [x] Symlinks and junctions are created at test time and their tests skip through `Assumptions` where the platform or privileges do not allow them; nothing platform-specific is committed under `fixtures/`
- [x] No test uses `Thread.sleep`, and no test reads or writes outside a `@TempDir`
- [x] `module-info.java` is unchanged and `ModuleExportsTest` stays green

## Open questions

None.
