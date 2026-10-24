---
name: reference-ak-plan-cli
description: Observed behavior of ak 2.19.0 plan CLI (create/add-phase/reindex/validate) when scaffolding plans in skim-browser
metadata:
  type: reference
---

Observed with `ak 2.19.0` on 2026-10-21:
- `ak plan create <name> --basedir <plans> --no-interactive` names the dir with a UTC timestamp (e.g. `261020-1611-…` while local Asia/Singapore date was 2026-10-21), creates `plan.md` + `phase-01-start.md`.
- `ak plan add-phase <dir> <Name>` appends `phase-NN-<slug>.md` but does NOT update the Phases table in plan.md — the table is plain markdown owned by the planner.
- Phase frontmatter `status: pending` is read as `todo` by `ak plan parse`; `phase:`/`priority:`/`effort:`/`dependencies:` keys are accepted.
- Renaming `phase-01-start.md` is safe: `ak plan reindex` (dry run by default, `--apply` only touches the plan store, never files) and `ak plan validate` both pass afterwards.
- `set-active-plan.cjs` lives at `~/.claude/scripts/set-active-plan.cjs`; the documented `find` command is blocked by the scout-block hook (node_modules pattern).

Related: [[feedback-plan-language]].
