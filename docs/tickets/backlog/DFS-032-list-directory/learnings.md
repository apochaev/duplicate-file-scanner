## design (architect) — 2026-11-01

- **Shared helper extracted from existing code vs. "every stub throws".** Ticket decision 5 asks for a classification helper shared with NioFileTreeWalker. Stubbing it and refactoring the walker at design would break every scan test before implementation; writing it for real would be implementing at design. Resolved by adding the helper as a stub and leaving the walker untouched until the implementer rewires it. The archetype could say so for "extract and share" tickets.
- **Turn budget.** The architect ran out of turns before writing design.md (a heredoc batch failed and had to be redone with Write). For a ticket with several new types and a refactor, 25 turns is tight; batching writes earlier would help.

## verify (verifier) — 2026-11-01

- **"Refactor with identical behavior" needs a pinning test, not only unchanged old tests.** NioFileTreeWalker's move onto EntryKind changed the check order (`isOther()` before `isRegularFile()`). It is equivalent only because the JDK's attribute flags are mutually exclusive, which the verifier had to argue from `WindowsFileAttributes`. A small `EntryKind` unit test over a fake `BasicFileAttributes` (each flag combination maps to its kind, reason and detail) would have pinned it cheaply. The test-author could add one for any "extract a shared helper" ticket.
- **Ticket-level DoD "no test uses Thread.sleep" is ambiguous** when a touched test class already holds one (`ScannerAutoConfigurationTest.await`, DFS-031). The verifier read it as "no new sleep in this ticket's tests". Ticket templates could say "no test added by this ticket".
