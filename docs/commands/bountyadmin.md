# /bountyadmin

Admin management commands.

**Permission:** `swagbounties.admin` (default: op)

## Config

```
/bountyadmin config get <key>
/bountyadmin config set <key> <value>
/bountyadmin config list
/bountyadmin config reset
```

View, change, list, or reload config values live in-game without restarting.

## Reload

```
/bountyadmin reload
```

Shorthand for `config reset` — reloads config from disk.

## Remove

```
/bountyadmin remove <target> [creator]
```

Force-remove a bounty on `<target>`. If `[creator]` is specified, only removes that creator's bounty. Refunds are issued automatically.

## Clear

```
/bountyadmin clear <player>
```

Remove all bounties on a player with full refunds.

## Clearall

```
/bountyadmin clearall
```

Wipe every bounty on the server with full refunds.

## Give

```
/bountyadmin give <target> <amount> [--anon]
```

Place a free server-funded bounty on a player (no money deducted from anyone).

## Inspect

```
/bountyadmin inspect <player>
```

View full bounty details on a player with anonymous bounties unmasked — shows real creator names.
