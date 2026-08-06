# Development Script Prepare Separation Hygiene

Status: Open hygiene

## Finding

The normal server consumes a separately refreshed classpath, but freshness is
not recorded and `scripts/run-server-debug.sh` still launches top-level SBT.
There is no aggregate prepare entry covering the toolchain CAR/runtime evidence
or a digest-backed preparation manifest equivalent to ArtScene.

## Required direction

- Consolidate build, CAR, tool/dependency, and runtime-evidence work under one
  prepare entry routed through the shared serialized SBT runner.
- Keep maintained runtime, tool-check, and debug paths top-level-SBT-free.
- Reject missing or stale preparation using explicit input/artifact digests.
- Reuse current evidence across ordinary repair restarts.

## Completion evidence

Serialized preparation succeeds; maintained runtime/check scripts are
statically SBT-free; current evidence runs; missing or stale evidence is
rejected without implicit preparation.
