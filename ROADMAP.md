# Roadmap

This is a living document tracking planned work for EnvironmentAI. Nothing here is set in stone — update it as priorities shift.

## Phase 0 — Foundation (done)

- [x] Scaffold Fabric mod boilerplate (Minecraft 26.3, Loom 1.17, Java 25)
- [x] Project docs: README, ROADMAP, HISTORY

## Phase 1 — Dual-process hostile mob AI (done)

- [x] Config: independent System 1 / System 2 tiers, each LOCAL (Ollama) or CLOUD (Anthropic)
- [x] Model provider abstraction (Anthropic + Ollama), async, bounded local/cloud executors
- [x] Per-mob narrative state (System 2) as a persistent Fabric API attachment
- [x] Discrete action vocabulary (System 1) executed via six gated vanilla-style `Goal`s
- [x] Event-triggered System 2 refresh (damage, target change, System-1-flagged escalation)
      with debounce + cooldown
- [x] `/environmentai inspect <target>` debug command
- [x] Smoke-tested via headless `runServer` boot (no mixin errors)

## Phase 2 — Real in-game verification & tuning

- [ ] Manually verify in-game: spawn hostile mobs, confirm goal switching feels right, watch
      narrative state evolve via the debug command over an actual fight/chase
- [ ] Tune default prompts, thresholds (near-death %, stuck-action tick count), and cadence based
      on how it actually plays
- [ ] Decide whether System 1's JSON-in-prose parsing needs hardening (e.g. Anthropic tool-use
      structured output) based on observed parse-failure rate

## Phase 3 — Expand scope

- [ ] Consider extending beyond hostile mobs (passive mobs, villagers) if Phase 1/2 prove out
- [ ] Per-mob-type config overrides (e.g. exclude specific hostile mobs, tune per species)
- [ ] Consider OpenAI-compatible generic provider if a use case needs it beyond Anthropic/Ollama

## Backlog / ideas

- Config validation could actively probe Ollama reachability at startup (currently deferred —
  errors surface on first failed call instead, to avoid blocking server startup on a network call)
- Structured/tool-use output for more reliable action parsing on the cloud tier

## Out of scope (for now)

- Passive mobs / villagers — narrative-driven AI scoped to hostile mobs only for this phase
- Raw/continuous movement control from System 1 — discrete actions + vanilla goal execution only,
  since per-tick raw-control LLM calls aren't feasible at any provider's latency
