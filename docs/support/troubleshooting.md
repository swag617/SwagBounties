# Troubleshooting

## Bounty not paying out on kill

- Confirm the killer is a player (not a mob or command)
- Check the console for errors
- Verify the killer and victim are not on the same IP (same-IP protection blocks payouts)
- Ensure SwagAPI is installed and its `IEconomyService` registered successfully (check console on startup)

## GUI not opening

- Check that SwagAPI is installed and SwagBounties enabled successfully (it refuses to enable without SwagAPI)
- Confirm the player has permission to run `/bounties` (no special permission required by default)

## Discord notification not firing

- Verify `discord-enabled: true` and `discord-webhook-name` is set correctly in `config.yml`
- Confirm [DiscordUtils](https://github.com/swag617/DiscordUtils) is installed with a matching `webhooks.<name>` entry in *its* `config.yml`
- Check that the bounty meets `discord-notify-threshold`

## PlaceholderAPI placeholders not working

- Confirm PlaceholderAPI is installed
- Check the console for `PlaceholderAPI expansion registered.` on startup
- Use `%swagbounties_total_bounties%` to test — it requires no player argument

## Config changes not taking effect

Run `/bountyadmin reload` to reload the config from disk without restarting.

## Still stuck?

Open an issue on [GitHub](https://github.com/swag617/SwagBounties/issues) with your server version, plugin version, and full console error.
