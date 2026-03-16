# config.yml

Full reference for `plugins/SwagBounties/config.yml`.

## Economy

```yaml
min-bounty: 100.0
max-bounty: 0.0
placement-tax: 5.0
expiry-refund-tax: 10.0
```

| Key | Default | Description |
|---|---|---|
| `min-bounty` | `100.0` | Minimum amount a player can place |
| `max-bounty` | `0.0` | Maximum amount (0 = no limit) |
| `placement-tax` | `5.0` | Percentage taken on bounty placement |
| `expiry-refund-tax` | `10.0` | Percentage taken from refund when a bounty expires |

## Expiry

```yaml
bounty-expiry-days: 7
```

| Key | Default | Description |
|---|---|---|
| `bounty-expiry-days` | `7` | Days until an unclaimed bounty expires (0 = never) |

## Cooldown

```yaml
bounty-cooldown-seconds: 60
```

| Key | Default | Description |
|---|---|---|
| `bounty-cooldown-seconds` | `60` | Seconds a player must wait between placing or removing bounties (0 = disabled) |

## Messages

```yaml
bounty-set-message: "&a[SwagBounties] A bounty of &e${amount} &ahas been placed on &e{target}&a!"
bounty-claim-message: "&a[SwagBounties] &e{killer} &aclaimed the bounty on &e{target} &afor &e${reward}&a!"
bounty-anon-message: "&7[SwagBounties] A bounty has been placed on &e{target}&7."
```

Placeholders in messages:
- `{target}` — target player name
- `{killer}` — killer player name
- `{amount}` — bounty amount placed
- `{reward}` — total reward claimed

## Discord

```yaml
discord-webhook-url: ""
discord-notify-threshold: 500.0
discord-set-message: "💰 A bounty of **${amount}** has been placed on **{target}**!"
discord-claim-message: "⚔️ **{killer}** claimed the bounty on **{target}** for **${reward}**!"
discord-expire-message: "⏰ The bounty on **{target}** has expired."
```

See [Discord Webhooks](discord.md) for setup instructions.
