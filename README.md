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
`debounceMs`, `cooldownMs` for System 2) are also editable in the same file.

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
  ai/scheduling/            AiExecutors, MobTracker, System1Scheduler, System2TriggerManager, AiService
  goal/                     the six discrete-action Goal implementations
  mixin/                    goal-injection and target-change-detection mixins (server/common only)
src/client/java/com/quitemax/environmentai/client/   client entrypoint (currently no client-side logic)
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





