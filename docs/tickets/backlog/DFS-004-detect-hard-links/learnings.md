## design (architect, attempt 1)

- Ticket ambiguity (AC-8): "a file whose same-file check throws" does not say which file of a pairwise check is at fault. The design attributes it to the candidate being placed. If a copy's first link is the unreadable one, readable candidates are dropped instead (conservative: fewer copies, never more). Worth a sentence in the ticket or the archetype's item 10 for pairwise ports.
- Skeleton gap: `FilterStage.java.tmpl` keeps a real `requireNonNull` in the stub constructor, so a null-constructor test passes against the stub (L-013). The Accumulator skeleton already advises a throwing constructor. The design follows that advice here. The FilterStage skeleton could say the same.
