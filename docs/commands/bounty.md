# /bounty

The main player-facing command.

## Usage

```
/bounty <set|remove|list|help> [args]
```

## Subcommands

### set

```
/bounty set <player> <amount> [--anon]
```

Place a bounty on a player. The `--anon` flag hides your name from other players (admins can still see it via `/bountyadmin inspect`).

- Works for online **and** offline players (must have joined the server at least once)
- Minimum and maximum amounts are enforced by config
- A placement tax is deducted on placement

### remove

```
/bounty remove <player>
```

Cancel your own bounty on a player. A partial refund is issued based on `expiry-refund-tax`.

### list

```
/bounty list [player]
```

List active bounties on yourself or another player. Shows each bounty's reward and whether it is anonymous.

### help

```
/bounty help
```

Displays command usage.

---

## Permissions

No special permission required — any player can use `/bounty`.
