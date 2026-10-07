## Rule 1: Structural Navigation via Graphify
- Before executing sweeping search commands (`grep`, `find`, `rg`), query the `graphify-out/graph.json` file to identify exact file names, functions, and cross-file method dependencies.
- Refer to architectural layouts via the Graphify layout map rather than parsing raw multi-file strings to map structure.

## Rule 2: Shell Tool Execution via RTK
- All terminal tool executions related to framework operations (`npm`, `pytest`, `cargo`, `go test`, `git`) MUST be prefixed with `rtk` (e.g., use `rtk npm test` or `rtk git status`).
- Do not request full raw terminal traces; trust the compressed, token-filtered summaries returned by the RTK proxy.


<!-- rtk-instructions v2 -->
# RTK — Token-Optimized CLI

**rtk** is a CLI proxy that filters and compresses command outputs, saving 60-90% tokens.

## Rule

Always prefix shell commands with `rtk`:

```bash
# Instead of:              Use:
git status                 rtk git status
git log -10                rtk git log -10
cargo test                 rtk cargo test
docker ps                  rtk docker ps
kubectl get pods           rtk kubectl get pods
```

## Meta commands (use directly)

```bash
rtk gain              # Token savings dashboard
rtk gain --history    # Per-command savings history
rtk discover          # Find missed rtk opportunities
rtk proxy <cmd>       # Run raw (no filtering) but track usage
```
<!-- /rtk-instructions -->
