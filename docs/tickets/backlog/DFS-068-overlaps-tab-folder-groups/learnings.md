# DFS-068 learnings

- **L-1 (refinement gap): `touches` missed the host component.** The ticket changes a child tab that takes `roots` (for `displayPath`) and `density` from its host, and removes the requests an existing host spec answers, yet neither `result-screen.html` nor `result-screen.spec.ts` is in `touches`. A component ticket that adds an input to a child, or changes the requests a child makes, should list the host's template and spec at refinement: grep the host's spec for the child's request helpers (`expectSummaries`, `openOverlaps`) and add both files.
