# PlaceholderAPI

SwagBounties registers a PlaceholderAPI expansion with the identifier `swagbounties`.

## Requirements

[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) must be installed.

## Available Placeholders

| Placeholder | Returns |
|---|---|
| `%swagbounties_top_reward%` | Highest total bounty reward across all targets |
| `%swagbounties_top_target%` | Name of the most-wanted player |
| `%swagbounties_total_bounties%` | Total count of all active bounties |
| `%swagbounties_bounty_<name>%` | Total reward on the named player |
| `%swagbounties_has_bounty_<name>%` | `yes` or `no` |

## Examples

```
Top bounty: %swagbounties_top_reward%
Most wanted: %swagbounties_top_target%
Bounty on Notch: %swagbounties_bounty_Notch%
```

Use these in scoreboards, chat formatting, or any plugin that supports PlaceholderAPI.
