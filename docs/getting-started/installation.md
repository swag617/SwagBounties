# Installation

## 1. Download

Download `SwagBounties.jar` from [GitHub Releases](https://github.com/swag617/SwagBounties/releases).

## 2. Install

Drop `SwagBounties.jar` into your server's `plugins/` folder alongside:

- **[SwagAPI](https://github.com/swag617/SwagAPI)** — required (hard dependency). SwagBounties will not enable without it; economy transactions go through SwagAPI's shared `IEconomyService`.
- **[DiscordUtils](https://github.com/swag617/DiscordUtils)** — optional, only needed if you want Discord notifications (see [Discord Webhooks](../configuration/discord.md)).

## 3. Generate config

Start your server once to generate `plugins/SwagBounties/config.yml`, then stop it.

## 4. Configure

Edit `config.yml` to set your desired limits, taxes, expiry, and Discord webhook. See [Configuration](../configuration/config.md) for all options.

## 5. Start

Start your server. SwagBounties will log `SwagBounties enabled.` when ready.

---

> **Optional:** Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) to enable `%swagbounties_*%` placeholders.
