# /bounty

The main player-facing command.

## Usage

```
/bounty <set|add|remove|list|top|help> [args]
```

## Subcommands

### set

```
/bounty set <player> <amount> [--anon]
```

Place a bounty on a player. The `--anon` flag hides your name from other players (admins can still see it via `/bountyadmin inspect`).

- Works for online **and** offline players (must have joined the server at least once)
- Cannot place a bounty on yourself
- Minimum and maximum amounts are enforced by config
- A placement tax is deducted on placement
- Subject to `bounty-cooldown-seconds` between placements

### add

```
/bounty add <player> <amount>
```

Add to an existing bounty you have already placed on a player. The additional amount is taxed at the same placement tax rate. The original placement time and anonymous flag are preserved.

- Requires an existing bounty by you on that target — use `/bounty set` first if you don't have one
- The resulting total reward cannot exceed `max-bounty` (if configured)
- Subject to the same cooldown as `/bounty set`

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

### top

```
/bounty top
```

Lists the top 5 most wanted players in chat, sorted by total reward descending.

### help

```
/bounty help
```

Displays command usage.

---

## Permissions

No special permission required — any player can use `/bounty`.
