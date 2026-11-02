# Advisory workflow

Agreed 2026-11-01: Codex advises; Claude's coordinator/product session evaluates advice and routes approved work to lane owners. Claude may automatically evaluate and propose a ticket. Implementation requires user approval through the existing ticket workflow.

## Ownership

- Advisor: writes proposals, research, and reviews. Does not implement product/harness changes, claim lanes, manage tickets, or change Claude configuration in this role.
- Coordinator: writes receipts and evaluations; proposes tickets and routes approved work.
- User: approves implementation. Advice, receipt, or agreement is never approval.
- Tickets remain authoritative for execution. After one clarification exchange, unresolved disagreement returns to the user.

## Files

- proposals/ADV-NNN-rN.md: advisor-owned published recommendations.
- responses/ADV-NNN-rN.json: coordinator-owned receipts/evaluations.
- reviews/ADV-NNN-rN-review-N.md: advisor-owned assessments; create when needed.
- research/: dated sources and evidence.
- schemas/: machine-readable response contract.
- .runtime/: ignored local cursors/locks; create when needed.

Durable records belong in Git. Stage named files and commit only when authorized. Uncommitted complete proposals are eligible for evaluation. The canonical exchange is advisory/ in the main checkout; worktree copies may be stale. Locate main through Git's worktree inventory; if unavailable, report it rather than switching another session's branch.

## Publication and intake

1. The single advisor allocates the next unused ADV ID. Every proposal states ID, revision, date, recipient, scope, evidence/base revision, recommendation, success criteria, and requested action.
2. Published proposals are immutable. Changes create a new revision explicitly naming the superseded revision. Automation writes a temporary file and renames it on the same filesystem; discovery ignores temporary files.
3. At the coordinator's next turn or session start, deterministic discovery lists revisions without responses and those with status received. Emit only IDs, titles, and paths. No pending advice means no context output or model call. Only the designated coordinator consumes intake.
4. Check supersession and evidence freshness. Persist received/pending before evaluation, then evaluated with propose-ticket, decline, needs-user, or superseded. Resume received entries after restart.
5. Repeated discovery must not duplicate tickets: check durable responses and existing tickets before proposing one. Record material evidence drift; do not alter another session's ongoing work.
6. Write responses atomically. Include exact proposal ID/revision, ticket references, and evidence paths. Advisor reviews those records and linked artifacts.

No automation is installed by these files. Claude must propose discovery integration and obtain implementation approval. Immediate wake-up, channels, background model polling, and additional sessions are deferred.

## Response fields

See schemas/response.schema.json. Required fields: schema_version (1), proposal_id, revision, status (received/evaluated), disposition, updated_at (actual ISO timestamp), summary, ticket_ids, evidence, implementation_authorized (always false).

Received status requires pending disposition. Evaluated permits propose-ticket, decline, needs-user, or superseded. The fixed false field means the advisory response itself grants no implementation authority; approval remains in the existing ticket/user workflow.
