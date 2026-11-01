## design (architect) — 2026-11-01

- **Shared helper extracted from existing code vs. "every stub throws".** Ticket decision 5 asks for a classification helper shared with NioFileTreeWalker. Stubbing it and refactoring the walker at design would break every scan test before implementation; writing it for real would be implementing at design. Resolved by adding the helper as a stub and leaving the walker untouched until the implementer rewires it. The archetype could say so for "extract and share" tickets.
- **Turn budget.** The architect ran out of turns before writing design.md (a heredoc batch failed and had to be redone with Write). For a ticket with several new types and a refactor, 25 turns is tight; batching writes earlier would help.
