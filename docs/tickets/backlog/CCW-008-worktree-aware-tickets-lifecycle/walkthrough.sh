#!/usr/bin/env bash
# CCW-008 walkthrough: runs the git steps of .claude/skills/tickets/SKILL.md (start, review, done, cancel)
# literally against a throwaway repo. One function per mode, one block per scenario.
set -u
S=${WALK_DIR:-${TMPDIR:-/tmp}/ccw-008-walk}   # throwaway, deleted at the end
R=$S/repo
TF() { echo "docs/tickets/backlog/$1-$2/$1-ticket.md"; }
status_of() { grep '^status:' "$1" | cut -d' ' -f2; }
setstatus() { sed -i "s/^status: .*/status: $2/" "$1"; }
wt_of() { git -C "$R" worktree list --porcelain | awk -v b="branch refs/heads/$1" '/^worktree /{w=substr($0,10)} $0==b{print w}'; }

mk() {
  cd /; rm -rf "$S"; mkdir -p "$S"; git init -q -b main "$R"; cd "$R"
  git config user.name t; git config user.email t@t; git config core.autocrlf false
  for t in XYZ-001-demo XYZ-002-plain; do
    mkdir -p docs/tickets/backlog/$t
    printf -- '---\nid: %s\nstatus: ready\ncompleted:\n---\n' "${t:0:7}" > docs/tickets/backlog/$t/${t:0:7}-ticket.md
  done
  printf 'line1\n' > a.txt; git add -A; git commit -qm "init"
  git commit -q --allow-empty -m "docs(tickets): refine XYZ-001 [XYZ-001]"
}

# start <ID> <slug> [--worktree]   (SKILL.md "start")
start() {
  local id=$1 slug=$2 flag=${3:-} br=tickets/$1-$2 tree wt top path
  wt=$(wt_of $br)
  if [ -n "$wt" ]; then tree=$wt; echo "start: entering existing worktree $wt"
  elif [ "$flag" = --worktree ]; then
    top=$(git worktree list --porcelain | awk '/^worktree /{print substr($0,10); exit}')
    path=$(dirname "$top")/$(basename "$top")-$id-$slug
    if git show-ref -q --verify refs/heads/$br; then git worktree add -q "$path" $br; else git worktree add -q -b $br "$path" main; fi || return 1
    tree=$path; echo "start: created worktree $path on $br"
  else tree=$(git rev-parse --show-toplevel); echo "start: in place in $tree ($(git branch --show-current))"; fi
  setstatus "$tree/$(TF $id $slug)" in-progress
}

# review <ID> <slug>   (SKILL.md "review": current tree only)
review() { setstatus "$(git rev-parse --show-toplevel)/$(TF $1 $2)" in-review; echo "review: in-review on $(git branch --show-current)"; }

# done|cancel <ID> <slug> [confirm=yes|no]   (SKILL.md "done" steps 1-6, "cancel")
close_in() {
  local T=$1 f=$(TF $id $slug) nf=docs/tickets/completed/$id-$slug/$id-ticket.md st=done
  [ $mode = cancel ] && st=cancelled
  setstatus "$T/$f" $st; sed -i "s/^completed:.*/completed: 2026-09-22/" "$T/$f"
  [ $mode = cancel ] && printf '\n## Resolution\n\nsome-reason\n' >> "$T/$f"
  mkdir -p "$T/docs/tickets/completed"; git -C "$T" mv docs/tickets/backlog/$id-$slug docs/tickets/completed/$id-$slug
  git -C "$T" add $nf
  git -C "$T" diff --quiet -- $nf && echo "  staged == working tree" || echo "  MISMATCH staged vs working tree"
  echo "  staged: $(git -C "$T" diff --cached --name-status | tr '\t\n' '  ')"
  git -C "$T" commit -qm "docs: close $id [$id]" && echo "  close commit: $(git -C "$T" log -1 --format='%h %s')"
}
done_() {
  mode=$1 id=$2 slug=$3; confirm=${4:-yes} br=tickets/$2-$3 M= ; local hits
  if ! git show-ref -q --verify refs/heads/$br; then echo "$mode: no $br, closing in place"; close_in "$(git rev-parse --show-toplevel)"; return; fi
  M=$(git worktree list --porcelain | awk '/^worktree /{w=substr($0,10)} $0=="branch refs/heads/main"{print w}')
  CT=$(dirname "$R")/repo-close-$id
  if [ -z "$M" ]; then git worktree add -q "$CT" main && M=$CT && echo "  step 1: no tree has main, opened close tree $CT"
  elif [ "$M" = "$(cd "$CT" 2>/dev/null && pwd -W)" ] || [ "$M" = "$CT" ]; then
    [ -n "$(git -C "$M" status --porcelain)" ] && { echo "  step 1: leftover close tree has uncommitted changes, STOP"; return 3; }
    echo "  step 1: reusing leftover close tree $M"
  fi
  if git -C "$M" merge-base --is-ancestor $br main; then echo "  landed: ancestor check passed"
  elif hits=$(git -C "$M" log --oneline -F --grep="[$id]" $br..main) && [ -n "$hits" ]; then echo "  landed: ancestor failed, fallback grep found: $hits"
  else echo "  not landed: neither check passed"
    if [ $mode = cancel ]; then echo "  cancel: never merges"
    elif git -C "$M" merge -q --no-edit $br; then echo "  merged: $(git -C "$M" log -1 --format='%h %s (parents %p)')"
    else echo "  $mode: STOP, merge conflict left unresolved; resolve, commit and re-run $mode"; return 2; fi
  fi
  close_in "$M"; cleanup; closetree
}
closetree() { case "$M" in *-close-$id) git -C "$R" worktree remove "$M" && echo "  step 6: close tree removed";; esac; }
cleanup() {
  wt=$(wt_of $br)
  echo "  cleanup: will remove worktree '${wt:-none}' and branch $br -- confirm? $confirm"
  [ $confirm = yes ] || { echo "  cleanup declined"; return; }
  if [ -n "$wt" ] && [ -n "$(git -C "$wt" status --porcelain)" ]; then echo "  cleanup REFUSED, dirty worktree: $(git -C "$wt" status --porcelain | tr '\n' ' ')"; return; fi
  if [ -n "$wt" ]; then git -C "$M" worktree remove "$wt" && echo "  worktree removed" || { echo "  worktree remove failed; branch kept"; return; }; fi
  git -C "$M" branch -d $br >/dev/null 2>"$S/err" && echo "  branch deleted (-d)" || echo "  branch -d refused, kept: $(head -1 "$S/err")"
}
# list: "Started elsewhere" = ready tickets whose tickets/<ID>-<slug> branch exists   (SKILL.md "list")
list_() { for f in docs/tickets/backlog/*/*-ticket.md; do d=$(basename $(dirname $f)); id=${d:0:7}; [ "$(status_of $f)" = ready ] || continue
  if git show-ref -q --verify refs/heads/tickets/$d; then echo "  started elsewhere: $id in $(wt_of tickets/$d | sed 's|.*/||')"; else echo "  dispatchable: $id"; fi; done; }
state() { echo "  -> main: $(git -C "$R" log --oneline main | head -4 | tr '\n' '|')"
  echo "  -> worktrees: $(git -C "$R" worktree list | wc -l), branch exists: $(git -C "$R" show-ref -q --verify refs/heads/tickets/XYZ-001-demo && echo yes || echo no)"; }
work() { cd "$(wt_of tickets/XYZ-001-demo)"; printf "$1" > ${2:-b.txt}; git add -A; git commit -qm "feat: work [XYZ-001]"; cd "$R"; }
WT=$(dirname "$R")/repo-XYZ-001-demo

echo "== S1 @AC-1 @AC-14 @AC-15 @AC-16 fast-forward landed; start creates worktree"
mk; start XYZ-001 demo --worktree
echo "  repo checkout copy: $(status_of $R/$(TF XYZ-001 demo)), worktree copy: $(status_of $WT/$(TF XYZ-001 demo))"
work 'x\n'; git merge -q --ff-only tickets/XYZ-001-demo; done_ done XYZ-001 demo; state
echo "  close commit files: $(git show --name-status --format= HEAD | tr '\t\n' '  ')"

echo "== S2 @AC-1 @AC-5 squash-merged"
mk; start XYZ-001 demo --worktree; work 'x\n'; git merge -q --squash tickets/XYZ-001-demo; git commit -qm "feat: squashed work [XYZ-001]"
done_ done XYZ-001 demo; state

echo "== S3 @AC-1 @AC-12 refinement commit before fork does not count"
mk; start XYZ-001 demo --worktree; work 'x\n'
echo "  unranged grep would hit: $(git log --oneline -F --grep='[XYZ-001]' main)"
done_ done XYZ-001 demo; state

echo "== S4 @AC-12 @AC-13 main moved, no conflict"
mk; start XYZ-001 demo --worktree; work 'x\n'; B=$(git rev-parse tickets/XYZ-001-demo)
printf 'y\n' > c.txt; git add c.txt; git commit -qm "chore: unrelated"; BEFORE=$(git rev-parse main)
done_ done XYZ-001 demo no
echo "  branch tip unchanged (not rebased): $([ "$(git rev-parse tickets/XYZ-001-demo)" = "$B" ] && echo yes || echo no)"
echo "  commits done added to main: $(git log --format='%s' $BEFORE..main --first-parent | tr '\n' '|')"

echo "== S5 @AC-12 @AC-13 conflict stops done"
mk; start XYZ-001 demo --worktree; work 'branch\n' a.txt; printf 'main\n' > a.txt; git commit -qam "chore: main edit"
done_ done XYZ-001 demo; echo "  exit $?"
echo "  unmerged: $(git diff --name-only --diff-filter=U | tr '\n' ' '), ticket still in backlog: $([ -f $(TF XYZ-001 demo) ] && echo yes || echo no)"; state
git merge --abort

echo "== S6 @AC-5 dirty worktree"
mk; start XYZ-001 demo --worktree; work 'x\n'; git merge -q --ff-only tickets/XYZ-001-demo; printf 'dirt\n' > "$WT/dirty.txt"
done_ done XYZ-001 demo; state

echo "== S7 @AC-6 @AC-14 refs/original survives"
mk; start XYZ-001 demo --worktree; work 'x\n'; git merge -q --ff-only tickets/XYZ-001-demo
git update-ref refs/original/refs/heads/tickets/XYZ-001-demo tickets/XYZ-001-demo~1
done_ done XYZ-001 demo; state; echo "  refs/original: $(git for-each-ref --format='%(refname)' refs/original)"

echo "== S8 @AC-18 done on a ticket worked on main"
mk; done_ done XYZ-002 plain; state

echo "== S9 @AC-8 start in place"
mk; start XYZ-002 plain; echo "  status here: $(status_of $(TF XYZ-002 plain)), branches: $(git branch --format='%(refname:short)' | tr '\n' ' '), worktrees: $(git worktree list | wc -l)"

echo "== S10 @AC-16 start enters existing worktree"
mk; start XYZ-001 demo --worktree; setstatus "$WT/$(TF XYZ-001 demo)" ready; start XYZ-001 demo
echo "  worktree copy: $(status_of $WT/$(TF XYZ-001 demo)), repo copy: $(status_of $R/$(TF XYZ-001 demo)), worktrees: $(git worktree list | wc -l)"

echo "== S11 @AC-17 cancel never merges"
mk; start XYZ-001 demo --worktree; work 'x\n'; git -C "$WT" status --porcelain; done_ cancel XYZ-001 demo; state
echo "  ticket: $(grep '^status' docs/tickets/completed/XYZ-001-demo/XYZ-001-ticket.md), resolution: $(grep -c '^## Resolution' docs/tickets/completed/XYZ-001-demo/XYZ-001-ticket.md)"

echo "== S12 @AC-11 review in the worktree"
mk; start XYZ-001 demo --worktree; cd "$WT"; review XYZ-001 demo; cd "$R"
echo "  worktree copy: $(status_of $WT/$(TF XYZ-001 demo)), repo copy: $(status_of $R/$(TF XYZ-001 demo)), worktrees: $(git worktree list | wc -l)"

echo "== S13 done invoked from inside the ticket's worktree"
mk; start XYZ-001 demo --worktree; work 'x\n'; cd "$WT"; done_ done XYZ-001 demo; cd "$R"; state

echo "== S14 @AC-19 no tree has main checked out: close tree"
mk; start XYZ-001 demo --worktree; work 'x\n'; git switch -q -c other-ticket; done_ done XYZ-001 demo; echo "  exit $?"; state
echo "  repo checkout still on: $(git branch --show-current); main has close commit: $(git log -1 --format=%s main)"

echo "== S15 @AC-19 conflict in the close tree, resolve, re-run reuses and removes it"
mk; start XYZ-001 demo --worktree; work 'branch\n' a.txt; printf 'main\n' > a.txt; git commit -qam "chore: main edit"; git switch -q -c other-ticket
done_ done XYZ-001 demo; echo "  exit $?; close tree still there: $([ -d "$CT" ] && echo yes || echo no); worktrees: $(git worktree list | wc -l)"
done_ done XYZ-001 demo; echo "  re-run before resolving: exit $?"
printf 'resolved\n' > "$CT/a.txt"; git -C "$CT" add a.txt; git -C "$CT" commit -q --no-edit; echo "  user resolved and committed in close tree"
done_ done XYZ-001 demo; state; echo "  close tree gone: $([ -d "$CT" ] && echo no || echo yes)"

echo "== S16 @AC-20 list hides a ticket started in another worktree"
mk; list_; start XYZ-001 demo --worktree; echo "  after start --worktree (main copy still: $(status_of $(TF XYZ-001 demo))):"; list_

echo "== S17 @AC-21 shared main checkout with another session's uncommitted work"
mk; start XYZ-001 demo --worktree; work 'x\n'; printf 'other\n' > other-session.txt; printf 'line1\nedited by other session\n' > a.txt
done_ done XYZ-001 demo; state; echo "  main tree still has: $(git status --porcelain | tr '\n' ' ')"
cd /; rm -rf "$S"
