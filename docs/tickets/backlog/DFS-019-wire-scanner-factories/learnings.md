# DFS-019 learnings

- **Architect: the use-case checklist mostly does not fit a wiring-only ticket.** 9 of 14 items (1, 3, 4, 5, 7, 8, 9, 10, 12) are not applicable, because a factory has no lifecycle, ID or query of its own. The items that matter here are 2, 6, 11, 13 and 14. There is also a wiring concern the checklist does not name: which instances are shared within one build, and which are fresh across builds (AC-2 and AC-3). This supports the retro question in ticket decision 1. A `wiring` archetype could ask about:
  - the shared versus fresh instance graph;
  - a null or missing executor;
  - default adapters versus overrides;
  - the exported return type.
- **Architect: no stage owns an edit to an existing guard test that has no acceptance criterion.** The ticket's `touches` include `ArchitectureTest.java` (remove `allowEmptyShould(true)`, decision 5), but the architect does not write tests, and the archetype says nothing about who changes an existing guard test. I assigned it to the test-author in `design.md` and checked locally that the change stays green. An archetype line such as "edits to existing guard tests named in `touches` belong to the test-author" would remove the guesswork.
- **Architect: the use-case archetype still has no `skeletons/`.** A data record's stub form was taken from the "where files go" rule: an empty compact constructor, with checks listed in `design.md`. An empty compact constructor holding only a comment looks odd. A skeleton would settle whether to omit the compact constructor entirely instead.
