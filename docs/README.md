# ✦ SwagBounties

> Full-featured player bounty system for Spigot 1.21.4

SwagBounties lets players place monetary bounties on each other. When a target is killed, the killer claims the reward automatically through Vault. Browse all active bounties in a polished in-game GUI and get instant Discord notifications on every bounty event.

---

## Features

- **Place bounties** on any online or offline player with an optional anonymous flag
- **Stack bounties** — add to an existing bounty with `/bounty add` without creating a duplicate
- **Claim on kill** — reward paid to the killer via Vault on player death
- **Paginated GUI** — `/bounties` opens a skull-based chest GUI with per-target bounty details
- **Bounty expiry** — unclaimed bounties expire after a configurable number of days and refund the creator
- **Placement tax** — configurable tax on placement and expiry refunds
- **Placement cooldown** — configurable cooldown between bounty placements to prevent spam
- **Login notification** — players are warned on join if they have active bounties on their head
- **Top bounties** — `/bounty top` lists the 5 most wanted players in chat
- **Anonymous bounties** — hide your identity with `--anon`
- **Discord webhooks** — bounty set, claim, and expiry events posted to a Discord channel
- **PlaceholderAPI** — `%swagbounties_*%` placeholders for scoreboards and other plugins
- **Admin commands** — force-remove, wipe, inspect, and edit config live in-game
- **Same-IP exploit prevention**

---

## Quick Links

| | |
|---|---|
| [Installation](getting-started/installation.md) | Get SwagBounties running on your server |
| [Commands](commands/bounty.md) | Full command reference |
| [Configuration](configuration/config.md) | All config options explained |
| [PlaceholderAPI](placeholders/placeholders.md) | Available placeholders |
| [Admin Guide](admin/commands.md) | Admin commands and permissions |

---

## Requirements

| Dependency | Required |
|---|---|
| Spigot / Paper 1.21.4 | Yes |
| Java 21 | Yes |
| Vault | Yes |
| Economy plugin (EssentialsX, etc.) | Yes |
| PlaceholderAPI | No |

> **Download:** [GitHub Releases](https://github.com/swag617/SwagBounties/releases)
