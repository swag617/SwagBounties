# Discord Webhooks

SwagBounties can post to a Discord channel whenever a bounty is set, claimed, or expires.

As of the SwagAPI event-bus migration, SwagBounties no longer sends the HTTP request itself —
it publishes a `discordutils:notify` event on **SwagAPI's shared event bus**. [DiscordUtils](https://github.com/swag617/DiscordUtils)
must be installed alongside SwagAPI and have a matching `webhooks.<discord-webhook-name>` entry
in *its own* `config.yml` for the message to actually be delivered. There is no compile-time or
reflection dependency on DiscordUtils from SwagBounties — if DiscordUtils isn't installed, or
isn't subscribed to the channel, the event is simply never picked up.

## Setup

1. Install SwagAPI (hard dependency of SwagBounties) and DiscordUtils.
2. In DiscordUtils' `config.yml`, add a named webhook, e.g.:

```yaml
webhooks:
  bounties: "https://discord.com/api/webhooks/..."
```

3. In SwagBounties' `config.yml`, point at that webhook name:

```yaml
discord-enabled: true
discord-webhook-name: "bounties"
```

4. Reload both plugins (or restart the server):

```
/bountyadmin reload
```

## Threshold

Only bounties at or above `discord-notify-threshold` trigger a notification:

```yaml
discord-notify-threshold: 500.0
```

Set to `0` to notify on every bounty.

## Messages

Customize the Discord message text:

```yaml
discord-set-message: "💰 A bounty of **${amount}** has been placed on **{target}**!"
discord-claim-message: "⚔️ **{killer}** claimed the bounty on **{target}** for **${reward}**!"
discord-expire-message: "⏰ The bounty on **{target}** has expired."
```

Messages support Discord markdown. Set `discord-enabled: false` to disable Discord notifications entirely.
