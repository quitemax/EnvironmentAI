#!/usr/bin/env python3
"""Regenerates the debug datapack's help function and validates every tellraw in the pack.

help.mcfunction is one long tellraw JSON line. Editing it by hand broke it twice (a line
continuation hack, then literal newlines inside JSON strings), so it is generated from the list
below and every tellraw payload in the pack is parsed afterwards. Run after adding a scenario:

    python tools/generate-datapack-help.py
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "testing", "datapack",
                    "environmentai_debug", "data", "eai", "function")

SCENARIOS = [
    ("arena", "ATTACK baseline"),
    ("gauntlet", "ATTACK + pathing: gap, bridge over pit"),
    ("open_field", "FLEE in the open, hurt mob"),
    ("corner_trap", "FLEE with no route + near-death"),
    ("moat", "APPROACH across water"),
    ("perch", "APPROACH vs ATTACK from a hill"),
    ("checkpoint", "GUARD around a chest"),
    ("dark_room", "WANDER / no target, sealed"),
    ("daylight", "WANDER/IDLE at noon, sun vs shade"),
    ("zoo", "one pen per species"),
    ("pack", "System 2 narrative divergence"),
    ("stress", "100 zombies, scheduler load"),
    ("ranged", "KEEP_DISTANCE: skeletons at 4/9/15 blocks"),
    ("intimidation", "one of each archetype; pair with gear"),
    ("village_talk", "villagers with professions; /talk to them"),
    ("village_threat", "L2 mood: villagers + a zombie, no golem (afraid)"),
    ("village_golem", "L2 mood: same threat with an iron golem (uneasy)"),
]


def click(command):
    return {"action": "suggest_command", "command": command}


def build_help():
    parts = [{"text": "[eai] scenarios (built around where you stand):\n", "color": "green"}]
    for name, description in SCENARIOS:
        parts.append({"text": f"  {name}", "color": "aqua",
                      "click_event": click(f"/function eai:scenario/{name}")})
        parts.append({"text": f" {description}\n", "color": "gray"})
    parts.append({"text": "  gear/netherite | diamond | iron | none", "color": "aqua",
                  "click_event": click("/function eai:gear/netherite")})
    parts.append({"text": " equip yourself to test intimidation\n", "color": "gray"})
    parts.append({"text": "  /talk <message>", "color": "aqua", "click_event": click("/talk ")})
    parts.append({"text": " speak to the villager you are looking at\n", "color": "gray"})
    parts.append({"text": "  eai:clear", "color": "red", "click_event": click("/function eai:clear")})
    parts.append({"text": " removes scenario mobs. Inspect with /environmentai inspect @n[tag=eai_debug]",
                  "color": "gray"})
    return "tellraw @s " + json.dumps(parts, ensure_ascii=False, separators=(",", ":"))


def main():
    line = build_help()
    json.loads(line.split(" ", 2)[2])
    assert "\n" not in line
    with open(os.path.join(ROOT, "help.mcfunction"), "w", encoding="utf-8", newline="\n") as handle:
        handle.write(line + "\n")

    missing = [n for n, _ in SCENARIOS
               if not os.path.exists(os.path.join(ROOT, "scenario", f"{n}.mcfunction"))]
    problems = [f"missing scenario file: {n}" for n in missing]

    checked = 0
    for path in glob.glob(os.path.join(ROOT, "**", "*.mcfunction"), recursive=True):
        with open(path, encoding="utf-8") as handle:
            for number, text in enumerate(handle, 1):
                text = text.rstrip("\n")
                if text.endswith("\\"):
                    problems.append(f"{path}:{number}: line continuation")
                match = re.match(r"^tellraw @\w+ (.*)$", text)
                if match:
                    checked += 1
                    try:
                        json.loads(match.group(1))
                    except ValueError as error:
                        problems.append(f"{path}:{number}: invalid tellraw JSON: {error}")

    print(f"help.mcfunction regenerated ({len(SCENARIOS)} scenarios); {checked} tellraw payloads checked")
    for problem in problems:
        print("PROBLEM:", problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
