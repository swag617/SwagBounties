# Requirements

| Dependency | Version | Required |
|---|---|---|
| Spigot / Paper | 1.21.4 | Yes |
| Java | 21+ | Yes |
| [SwagAPI](https://github.com/swag617/SwagAPI) | Latest | Yes (hard depend) |
| PlaceholderAPI | 2.11+ | No |
| [DiscordUtils](https://github.com/swag617/DiscordUtils) | Latest | No (needed for Discord notifications) |

## Notes

- SwagBounties will **refuse to enable** if SwagAPI is not found (specifically, if its
  `IDatabaseService` isn't registered).
- Economy transactions go through SwagAPI's shared `IEconomyService`, not Vault directly. If that
  service isn't available, SwagBounties still enables but economy features (placing, claiming,
  refunding bounties) are disabled.
- PlaceholderAPI is soft-depend — the plugin works fine without it, but `%swagbounties_*%` placeholders will not function.
- Discord notifications are published on SwagAPI's event bus and require DiscordUtils to be
  installed separately with a matching webhook configured — see [Discord Webhooks](../configuration/discord.md).
