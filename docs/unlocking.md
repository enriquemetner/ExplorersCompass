# Structure unlocking

The server reads `config/explorerscompass/unlocking_rules.json` at startup. A new file contains:

```json
{
  "default": "unlocked",
  "rules": {}
}
```

[The full example](unlocking_rules.example.json) demonstrates the selectors and condition forms, including the other default, `lockedUntilVisited`. Copy it to the config directory to try it.

## Rules

Each named rule contains:

- `unlocks`: exactly one of `structures` (an array of structure IDs) or `structureSet` (a structure-set ID).
- `condition`: a visit, a completed advancement, or a nested `any`/`all` array of conditions. Arrays must be nonempty.
- `description` (optional): literal player-facing text. Use `\n` for line breaks. Without it the compass generates a readable requirements summary.

A visit condition has `type: "visit"` and exactly one of `structure`, `structures`, or `structureSet`. `match` may be `any` (the default) or `all`. One instance of each structure type is sufficient for `all`; visits count across dimensions. An advancement condition has `type: "advancement"` and a nonempty `advancements` array, even for one advancement. Its `match` is `any` by default or `all` to require every listed advancement.

Explicit rules override the default. Multiple rules targeting the same structure are alternatives: any satisfied rule is enough. `lockedUntilVisited` applies only to structures without an explicit rule and requires a visit to that structure type.

New visits and advancement progress can immediately add to a player's saved unlocked structures. Opening the compass also evaluates current conditions. The open screen uses the snapshot received when opened; reopen it after progress, commands, or rule changes. Saved unlocked structures (including default-unlocked structures) persist through lost requirements, config edits, and restarts. There is no automatic relocking.

Locked entries remain visible and show a requirements dialog on search attempts. Search Listed skips them. Requirement text wraps in a panel no taller than half the screen; use the wheel, scrollbar, arrow keys, Page Up/Down, or Home/End to scroll. Hover an advancement name to see its title and description. Previously found compass locations remain usable.

## Operator commands

Permission level 2 is required. `<player>` accepts an online name, a name in `usercache.json`, or a UUID. Names not found in the cache require a UUID.

```text
/explorerscompass lock <player> structure <id>
/explorerscompass lock <player> structureSet <id>
/explorerscompass lock <player> rule <name>
/explorerscompass lock <player> all

/explorerscompass unlock <player> structure <id>
/explorerscompass unlock <player> structureSet <id>
/explorerscompass unlock <player> rule <name>
/explorerscompass unlock <player> all

/explorerscompass clearVisited <player> structure <id>
/explorerscompass clearVisited <player> all
/explorerscompass reloadRules
```

`lock` removes the selected IDs from saved unlocked structures. If their requirements still hold, reopening the compass adds them again. Locking also cancels an in-progress search for that online player, restoring the compass's previous result. `unlock` adds the selected IDs regardless of conditions. A `rule` selector means all its target structures, not its requirement structures. Quote rule names containing spaces.

`clearVisited` removes every visited instance of the chosen type, or the player's entire history. It does not relock anything. A player standing inside a structure can have that visit recorded again at the next normal check. Use Minecraft's `/advancement revoke` to reset advancements separately.

`reloadRules` replaces rule definitions without evaluating players or modifying their saved data. Existing compass screens refresh on reopening. Later progress events use the new rules.

Invalid individual rules are logged and skipped; the default or other valid rules then apply. Unavailable targets are skipped with warnings. Unavailable visit/advancement requirements cannot match (and an empty set never satisfies `all`). An unreadable or malformed file logs an error and falls back to `unlocked` with no rules; it does not overwrite the file.
