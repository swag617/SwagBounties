<div align="center">

<br>

# ✦ SwagBounties

<p align="center">
  <img src="https://img.shields.io/badge/Spigot-1.21.4-667eea?style=for-the-badge" alt="Spigot 1.21.4">
  <img src="https://img.shields.io/badge/Java-21-764ba2?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Vault-Economy-f0c060?style=for-the-badge" alt="Vault">
  <img src="https://img.shields.io/badge/License-MIT-5865F2?style=for-the-badge" alt="MIT">
</p>

**A full-featured player bounty system for Spigot 1.21.4.**
Place bounties on players, claim them on kill, browse them in a paginated GUI, and get notified via Discord webhooks.

<br>

</div>

---

## ✦ Features

- **Place bounties** — set a reward on any online or offline player with an optional anonymous flag
- **Claim on kill** — bounty reward is automatically paid to the killer via Vault on player death
- **Paginated GUI** — `/bounties` opens a 54-slot chest GUI with player skulls, click to see individual bounty details
- **Bounty expiry** — unclaimed bounties expire after a configurable number of days and refund the creator (with tax)
- **Placement tax** — configurable tax taken on placement and on expiry refund
- **Anonymous bounties** — creators can hide their identity with `--anon`
- **Discord webhooks** — high-value bounty set, claim, and expiry events posted to a Discord channel
- **PlaceholderAPI** — exposes `%swagbounties_*%` placeholders for scoreboards and other plugins
- **Admin commands** — force-remove bounties, wipe all bounties, place free admin bounties, inspect with anonymity unmasked, and edit config live in-game
- **Same-IP exploit prevention** — players cannot claim bounties on accounts sharing their IP

---

## ✦ Installation

1. Download `SwagBounties.jar` from [Releases](https://github.com/swag617/SwagBounties/releases)
2. Drop it into your server's `plugins/` folder alongside Vault and a Vault economy provider (e.g. EssentialsX)
3. Start the server once to generate `plugins/SwagBounties/config.yml`, then stop it
4. Edit `config.yml` to set your desired min/max bounty, taxes, expiry days, and (optionally) a Discord webhook URL
5. Start the server

> **Requirements:** Spigot / Paper 1.21.4 — Java 21 — Vault + an economy plugin
> **Optional:** PlaceholderAPI for `%swagbounties_*%` placeholders

---

## ✦ Dependencies

| Dependency | Required | Notes |
|---|---|---|
| Java 21 | Yes | |
| Spigot / Paper 1.21.4 | Yes | |
| Vault | Yes | Economy withdrawals and deposits |
| Economy plugin | Yes | EssentialsX, CMIEconomy, etc. |
| PlaceholderAPI | No | Enables `%swagbounties_*%` placeholders |

---

## ✦ Commands

| Command | Description | Permission |
|---|---|---|
| `/bounty set <player> <amount> [--anon]` | Place a bounty on a player | none |
| `/bounty remove <player>` | Cancel your bounty and receive a refund | none |
| `/bounty list [player]` | List active bounties on yourself or another player | none |
| `/bounty help` | Show command usage | none |
| `/bounties` | Open the paginated bounty GUI | none |
| `/bountyadmin config get <key>` | View a config value | `swagbounties.admin` |
| `/bountyadmin config set <key> <value>` | Change a config value in-game | `swagbounties.admin` |
| `/bountyadmin config list` | List all config values | `swagbounties.admin` |
| `/bountyadmin config reset` | Reload config from disk | `swagbounties.admin` |
| `/bountyadmin reload` | Shorthand for config reset | `swagbounties.admin` |
| `/bountyadmin remove <target> [creator]` | Force-remove bounty/bounties with refund | `swagbounties.admin` |
| `/bountyadmin clear <player>` | Clear all bounties on a player with refunds | `swagbounties.admin` |
| `/bountyadmin clearall` | Wipe all bounties server-wide with refunds | `swagbounties.admin` |
| `/bountyadmin give <target> <amount> [--anon]` | Place a free admin bounty | `swagbounties.admin` |
| `/bountyadmin inspect <player>` | View full bounty detail with anonymity unmasked | `swagbounties.admin` |

---

## ✦ Permissions

| Permission | Description | Default |
|---|---|---|
| `swagbounties.admin` | Access to all `/bountyadmin` commands | op |

---

## ✦ PlaceholderAPI

| Placeholder | Returns |
|---|---|
| `%swagbounties_top_reward%` | Highest total bounty reward across all targets |
| `%swagbounties_top_target%` | Name of the most-wanted player |
| `%swagbounties_total_bounties%` | Total count of all active bounties |
| `%swagbounties_bounty_<name>%` | Total reward on the named player |
| `%swagbounties_has_bounty_<name>%` | `yes` or `no` |

---

## ✦ Configuration

Key config options in `plugins/SwagBounties/config.yml`:

| Key | Default | Description |
|---|---|---|
| `min-bounty` | `100.0` | Minimum bounty a player can place |
| `max-bounty` | `0.0` | Maximum bounty (0 = no limit) |
| `placement-tax` | `5.0` | % taken from bounty on placement |
| `expiry-refund-tax` | `10.0` | % taken from refund on expiry |
| `bounty-expiry-days` | `7` | Days until unclaimed bounty expires (0 = never) |
| `discord-webhook-url` | `""` | Discord webhook URL (leave empty to disable) |
| `discord-notify-threshold` | `500.0` | Min reward to trigger a Discord notification |

---

<div align="center">

**Built by [Swag617](https://swag617.github.io/) · [Portfolio](https://swag617.github.io/) · [DiscordUtils](https://github.com/swag617/DiscordUtils)**

</div>
