# DFS-002 learnings

## design (architect)

- **Clash between the stub convention and a deliberate "not yet" throw.** AC-8 requires the finished `NioContentHasher.fullHash` to throw `UnsupportedOperationException` naming DFS-003. If that final throw used the `not implemented:` prefix, two things would go wrong:
  - The AC-8 test would pass against the stub, so the red gate would not see it fail.
  - `StubsCheck` might read the finished method as a leftover stub.

  Workaround: the method is stubbed with the standard `not implemented: DFS-002`, and the implementer swaps in `"full hash is implemented by DFS-003"`, a message without the prefix. The archetype and `impl-guide` do not cover a port method that is intentionally left unimplemented for a later ticket. A convention would help, for example a distinct prefix such as `deferred to:` that `StubsCheck` ignores.
- **Checklist item 10 asks about both "unreadable" and "changes size".** The ticket only decided the unreadable case. I deferred the size-change case in `design.md` rather than invent behavior that no AC tests. The archetype could split item 10, or the ticket template could prompt for both halves during refinement.
