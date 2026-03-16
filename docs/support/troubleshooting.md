# Troubleshooting

## Bounty not paying out on kill

- Confirm the killer is a player (not a mob or command)
- Check the console for errors
- Verify the killer and victim are not on the same IP (same-IP protection blocks payouts)
- Ensure Vault and an economy provider are installed and working

## GUI not opening

- Check that `Vault` is installed and the plugin is enabled
- Confirm the player has permission to run `/bounties` (no special permission required by default)

## Discord webhook not firing

- Verify `discord-webhook-url` is set correctly in `config.yml`
- Check that the bounty meets `discord-notify-threshold`
- Test the webhook URL manually with a POST request

## PlaceholderAPI placeholders not working

- Confirm PlaceholderAPI is installed
- Check the console for `PlaceholderAPI expansion registered.` on startup
- Use `%swagbounties_total_bounties%` to test — it requires no player argument

## Config changes not taking effect

Run `/bountyadmin reload` to reload the config from disk without restarting.

## Still stuck?

Open an issue on [GitHub](https://github.com/swag617/SwagBounties/issues) with your server version, plugin version, and full console error.
