# Discord Webhooks

SwagBounties can post to a Discord channel whenever a bounty is set, claimed, or expires.

## Setup

1. In your Discord server, go to **Channel Settings → Integrations → Webhooks**
2. Create a new webhook and copy the URL
3. Paste it into `config.yml`:

```yaml
discord-webhook-url: "https://discord.com/api/webhooks/..."
```

4. Reload the plugin:

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

Messages support Discord markdown. Leave `discord-webhook-url` empty to disable webhooks entirely.
