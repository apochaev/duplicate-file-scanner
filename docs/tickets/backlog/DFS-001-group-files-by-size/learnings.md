# DFS-001 learnings

## design (architect)

- **Template gap: checklist item 14 is missing from `design.md.tmpl`.** `archetype.md`'s edge-case checklist now has 14 items ("Input/output currency named", added from CCW-005), but the template's "Checklist disposition" section stops at 13. I added item 14 to this `design.md` by hand. The template should list it too.
- **`skeletons/Accumulator.java.tmpl` contradicts L-013.** Its `add` and `merge` stubs call `Objects.requireNonNull(...)` before the `throw`. As a result, a null-argument test (AC-7 here) passes against the stub instead of failing red, which is exactly what L-013 forbids and what `FilterStage.java.tmpl` and `StageResult.java.tmpl` already avoid. I left the checks out of the `SizeIndex` stub. The skeleton should drop them and carry the same "implementer: add requireNonNull" comment as the other two.
- **The skeletons do not cover an accumulator whose constructor takes validated configuration.** `Accumulator.java.tmpl` has only a no-arg constructor that does not throw. For `SizeIndex(long minSize)`, a constructor that does not throw would let the negative-minimum test fail red without the `not implemented:` message, so I made the constructor throw too (the no-arg constructor delegates to it). The skeleton could show this pattern.
