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
set-message: "&c[SwagBounties] &e%creator% &fhas placed a &a$%amount% &fbounty on &e%target%&f!"
claim-message: "&c[SwagBounties] &e%killer% &fhas claimed a &a$%amount% &fbounty on &e%target%&f!"
set-message-anon: "&c[SwagBounties] &fAn anonymous player has placed a &a$%amount% &fbounty on &e%target%&f!"
```

Placeholders in messages:
- `%target%` — target player name
- `%creator%` — bounty creator's name (used in `set-message`)
- `%killer%` — killer player name
- `%amount%` — bounty amount placed / claimed

## Discord

```yaml
discord-enabled: true
discord-webhook-name: "bounties"
discord-notify-threshold: 500.0
discord-set-message: "💰 **%creator%** placed a **$%amount%** bounty on **%target%**!"
discord-claim-message: "⚔️ **%killer%** claimed a **$%amount%** bounty on **%target%**!"
discord-expire-message: "⏰ A **$%amount%** bounty on **%target%** has expired and was refunded."
```

See [Discord Webhooks](discord.md) for setup instructions.
