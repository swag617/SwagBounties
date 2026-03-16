# Admin Commands

Full reference for `/bountyadmin`. See [/bountyadmin](../commands/bountyadmin.md) for usage details.

## Summary

| Command | Description |
|---|---|
| `/bountyadmin config get <key>` | View a config value |
| `/bountyadmin config set <key> <value>` | Change a config value live |
| `/bountyadmin config list` | List all config values |
| `/bountyadmin config reset` | Reload config from disk |
| `/bountyadmin reload` | Shorthand for config reset |
| `/bountyadmin remove <target> [creator]` | Force-remove bounty with refund |
| `/bountyadmin clear <player>` | Clear all bounties on a player |
| `/bountyadmin clearall` | Wipe all bounties server-wide |
| `/bountyadmin give <target> <amount> [--anon]` | Place a free admin bounty |
| `/bountyadmin inspect <player>` | View bounties with anonymity unmasked |

## Config Keys

All keys from `config.yml` can be read and written in-game:

```
/bountyadmin config get min-bounty
/bountyadmin config set min-bounty 50
/bountyadmin config set bounty-cooldown-seconds 30
/bountyadmin config set discord-webhook-url https://discord.com/api/webhooks/...
```

Changes take effect immediately and are saved to disk.
