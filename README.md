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

### Archetypes, perception and intimidation

Each hostile mob gets an archetype that decides which actions the model may choose from and how
easily it loses its nerve:

| Archetype | Mobs (by capability) | Actions | Nerve |
|---|---|---|---|
| `MELEE` | zombie and most others | ATTACK, FLEE, APPROACH, GUARD | 9.0 |
| `SKITTISH` | spiders | ATTACK, FLEE, APPROACH, GUARD | 6.5 |
| `EXPLOSIVE` | creeper (ATTACK defers to the vanilla swell) | ATTACK, APPROACH, FLEE, GUARD | 7.0 |
| `RELENTLESS` | enderman (never intimidated by gear) | ATTACK, APPROACH, GUARD | 99 |
| `RANGED` | skeleton, pillager, witch, drowned (anything `RangedAttackMob`) | ATTACK, KEEP_DISTANCE, FLEE, APPROACH, GUARD | 6.0 |

`KEEP_DISTANCE` backs a ranged mob off when the target is within 7 blocks and otherwise leaves it
to its own bow, so it kites rather than going quiet.

The prompt includes what a mob can see of its target: armour points and held weapon (reduced to a
0-10 threat score), line of sight, light level, and the nearest creatures with distances. **Intimidation** is a rule: when the target's
threat reaches the mob's nerve (raised by fellow hostiles nearby) melee mobs flee and ranged mobs
keep their distance. It is not applied to a nearly dead target. Disable with
`intimidationEnabled: false`.

Replies are schema-constrained where the provider supports it (`structuredOutput`, default on): a
System 1 reply can only be one of the actions valid for that mob in that situation.

### Talking to villagers (System 3)

Look at a villager within 6 blocks and run `/talk <message>`, or press **V** (rebindable under
Controls > Gameplay > "Talk to villager") while looking at one to start a **conversation**: the
camera moves to an over-the-shoulder shot, the villager stands still and faces you, and a text box
stays open so you can keep talking line after line. The villager's last line is shown above it.
Escape, stepping more than a metre away, an attack on the villager, or a hostile creature
appearing ends it; saying something that frightens it makes it run away. The frame is framed like
a film shot: a zoom, letterbox bars, and a status panel with the villager's mood and standing.
Chat stays quiet while the conversation is on screen (it would duplicate it); when it ends, the
whole exchange, including your own lines, is written to chat. The framing is the
`conversationCamera` block of `config/environmentai.json` (`behind`, `aside`, `above`,
`aimTowardPlayer`, `zoom`), read by the client, so it can be tuned without rebuilding.

Villagers also speak first, for a few real reasons (a strong mood, a nearby threat, being thanked
for a cure, a greeting). One event is seen by a whole village, so a gate lets exactly one villager
speak for the group, at most once every twenty seconds per player. The `system3` block of the config
selects its model (local by default; dialogue is the tier most worth moving to the cloud, since it
is not latency-critical). Replies are grounded in the villager's real situation and its vanilla
gossip toward you; a warm or cold exchange can nudge that gossip by one point at most once per day.
Villagers also have a **mood** (calm, content, uneasy, afraid, terrified, grieving, angry), derived by
rules from what happens to and near them: an attack, a death they witnessed, a zombie coming close,
a raid. It colours what they say, and a strong mood change can make one exclaim unprompted
(`villagerAmbientChatter`; `villagerMoodEnabled` turns the whole thing off). It never changes how
they move. Your text is written to `training/villager-<timestamp>.jsonl` locally. See
[`docs/VILLAGERS.md`](docs/VILLAGERS.md) for the design and the roadmap beyond this.

### The narrator

A comic-book narrator comments on what happens to you in caption boxes in the corner of the screen:
reaching a goal on the way to the dragon, a near miss, a death, being hunted at night, entering
another dimension, surviving until dawn, a long quiet stretch. It watches **every** player, and
tells the others about a player's big moments ("Elsewhere, another traveler fell...").

- **Voice:** `/environmentai narrator style <noir|pulp|deadpan|documentary>`. Noir is the default.
- **How much:** `/environmentai narrator hints <off|narration|hints>`. `hints` (the default) adds
  oblique nudges toward the next goal when you have stalled; `narration` only comments; `off` is
  silent. Both settings are saved to `config/environmentai.json`.
- It is rule-driven and rate-limited, never speaks during a villager conversation, and falls back
  to hand-written captions if the model is slow or unavailable. See
  [`docs/NARRATOR_DIRECTOR.md`](docs/NARRATOR_DIRECTOR.md).

### Reading the logs

`tools/analyze-log.ps1` summarises the newest `training/system1-*.jsonl` and its System 2 sibling:
which build ran, decisions by health band and species, rule/override counts, latency by queue
depth and System 2 load, errors, and whether fleeing opened the distance. `-Mob <id prefix>` prints
one mob's timeline.

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





