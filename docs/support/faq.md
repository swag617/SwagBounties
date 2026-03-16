# FAQ

## The plugin won't enable — what's wrong?

Check your console for errors. Common causes:

- **Vault not found** — Install [Vault](https://www.spigotmc.org/resources/vault.34315/)
- **No economy provider** — Install an economy plugin like [EssentialsX](https://essentialsx.net/)

## Can I place a bounty on an offline player?

Yes. SwagBounties supports offline players as long as they have joined the server at least once.

## Does the bounty persist after a server restart?

Yes. Bounties are saved to `plugins/SwagBounties/bounties.yml` on shutdown and reloaded on startup.

## What happens if a player dies to the environment?

No bounty is claimed. Bounties are only paid out when a **player** kills the target.

## Can I disable expiry?

Yes. Set `bounty-expiry-days: 0` in `config.yml`.

## Can two players share a bounty on the same target?

Yes. Multiple players can place separate bounties on the same target. When that target is killed, **all** bounties are paid out to the killer as a combined reward.

## How do I disable Discord notifications?

Leave `discord-webhook-url` empty in `config.yml`.
