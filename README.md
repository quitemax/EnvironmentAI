# EnvironmentAI

A [Fabric](https://fabricmc.net/) mod for Minecraft that replaces hostile mob AI with a two-tier,
LLM-driven decision system:

- **System 2** (slow, deliberate) maintains a per-mob narrative state — disposition, intent, a
  description of its target, and short memory notes — refreshed only when something significant
  happens (took damage, acquired/lost a target, or an escalation from System 1).
- **System 1** (fast, frequent) reads that cached narrative state plus immediate situational facts
  and picks one discrete action (`ATTACK`, `FLEE`, `APPROACH`, `GUARD`, `WANDER`, `IDLE`). Vanilla's
  own goal/pathfinding machinery then carries out whichever action is currently active.

Both tiers are configured independently and can each point at a local model (via [Ollama](https://ollama.com/))
or a cloud model (the Anthropic API). See [`ROADMAP.md`](ROADMAP.md) and [`HISTORY.md`](HISTORY.md)
for the design background.

## Requirements

- **JDK 25** or newer (Minecraft 26.x requires Java 25 as the minimum Gradle/runtime JVM)
- Git

Gradle itself does not need to be installed — the project uses the Gradle wrapper (`gradlew` / `gradlew.bat`), which downloads the pinned Gradle version automatically.

## Project versions

| Component     | Version        |
|---------------|----------------|
| Minecraft     | 26.3           |
| Fabric Loader | 0.19.5         |
| Fabric API    | 0.161.0+26.3   |
| Fabric Loom   | 1.17-SNAPSHOT  |
| Gradle        | 9.5.1          |
| Java          | 25             |

Check [fabricmc.net/develop](https://fabricmc.net/develop) periodically for newer versions and bump `gradle.properties` accordingly.

Note: as of Minecraft 26.1, the game ships unobfuscated with official Mojang mappings — Yarn mappings are no longer used, and code refers to official class names directly (e.g. `net.minecraft.client.Minecraft`, `net.minecraft.server.MinecraftServer`).

## Getting started

```bash
# Build the mod
./gradlew build

# Run the client for testing
./gradlew runClient

# Run a dedicated server for testing
./gradlew runServer
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The built jar is output to `build/libs/`.

### IDE setup

- **IntelliJ IDEA**: Open the project folder directly; IDEA will detect the Gradle project. Use IDEA 2025.3+ for correct mixin support.
- Run `./gradlew genSources` if you want decompiled Minecraft sources attached for navigation.

## Configuring the AI

On first launch the mod writes `config/environmentai.json` (relative to the run directory) with
defaults: System 1 → a local Ollama model, System 2 → Anthropic's cloud API. Each tier's `provider`
can independently be `LOCAL` or `CLOUD`.

- **Cloud (Anthropic)**: set the `ANTHROPIC_API_KEY` environment variable before launching (the
  env var name is itself configurable via `apiKeyEnvVar`). The mod logs a warning at startup if a
  tier is set to `CLOUD` and the key isn't present.
- **Local (Ollama)**: run an [Ollama](https://ollama.com/) server (defaults to `http://localhost:11434`)
  with the configured `model` pulled.

Cadence/throttling fields (`tickInterval`, `minSpacingLocalMs`, `minSpacingCloudMs` for System 1;
`debounceMs`, `cooldownMs` for System 2) are also editable in the same file. Each tier has its own
model-call thread pool (`system1ExecutorThreads`, `system2ExecutorThreads`), so a slow System 2
call can never starve System 1.

### Two local Ollama instances

`tools/ollama/start-instances.ps1` starts native Ollama instances (`:11434` is left alone) with
`OLLAMA_KEEP_ALIVE=-1`, per-instance context length and parallelism, flash attention and a q8_0 KV
cache. Modes:

- **default**: one instance per tier (System 1 on `:11435`, System 2 on `:11436`).
- **`-Shared`**: one instance on `:11435` serving both tiers. Prefer this when both tiers use the
  same model: two processes time-slice the GPU against each other (measured: System 1 median
  261 ms with System 2 idle, 1088 ms while a System 2 call ran) and each holds its own weights.
  Point both tiers' `endpointOverride` at `http://127.0.0.1:11435`.
- **`-Warm`**: load each tier's model and keep it resident. A cold load takes seconds, longer than
  `timeoutMs`, so the first calls would otherwise silently fall back.
- **`-Pull`** downloads the models; **`-Stop`** shuts everything down (including the runner child).

Keep-alive matters: with Ollama's default 5 minute unload, the first call after a quiet spell can
exceed `timeoutMs` and silently fall back.

### Debug scenarios

`testing/datapack/environmentai_debug` is a datapack of reproducible test environments, one per
action or behaviour. Install it with `tools/install-datapack.ps1 -World <name>`, then in-game:
`/reload`, `/function eai:setup`, `/function eai:help`. See
[`testing/README.md`](testing/README.md).

### Debugging

`/environmentai inspect <target>` prints a mob's live AI state (current action, disposition,
intent, target description, memory notes) — useful for iterating on prompts without digging
through logs.

## Project layout

```
src/main/java/com/quitemax/environmentai/
  EnvironmentAI.java        mod entrypoint: loads config, starts AiService, registers commands
  config/                   EnvironmentAIConfig / ModelTierConfig / ConfigManager (environmentai.json)
  command/                  the /environmentai debug command
  ai/provider/              ModelProvider abstraction + Anthropic/Ollama HTTP clients
  ai/action/                MobAction (the discrete vocabulary) + its JSON parser
  ai/narrative/             NarrativeState (System 2's output) + codec + parser
  ai/state/                 MobAiState (the per-mob attachment) + its AttachmentType registration
  ai/prompt/                System1PromptBuilder / System2PromptBuilder
  ai/scheduling/            AiExecutors (per-tier pools), MobTracker (load/unload-scoped), System1Scheduler, System2TriggerManager, AiService
  goal/                     the six discrete-action Goals + SystemGoals (installs them on entity load)
  mixin/                    target-change-detection mixin (server/common only)
src/client/java/com/quitemax/environmentai/client/   client entrypoint (currently no client-side logic)
tools/                      dev scripts: Ollama instance launcher, datapack installer
testing/datapack/           debug-scenario datapack
```

This mod uses Loom's split source sets (`splitEnvironmentSourceSets`), so common code lives under
`src/main` and client-only code lives under `src/client`. Never reference client-only classes from
`src/main`. Everything under `ai/`, `goal/`, and `mixin/` is server/common-side — there's currently
no client-only mixin config since nothing in this feature needs one.

## Documentation

- [`ROADMAP.md`](ROADMAP.md) — planned features and milestones
- [`HISTORY.md`](HISTORY.md) — project history / changelog of notable decisions and changes

## License

[MIT](LICENSE)





