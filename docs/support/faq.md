# FAQ

## The plugin won't enable — what's wrong?

Check your console for errors. Common causes:

- **SwagAPI not found** — SwagBounties has a hard dependency on [SwagAPI](https://github.com/swag617/SwagAPI); install and enable it first
- **SwagAPI's `IDatabaseService` not registered** — check SwagAPI's own console output for startup errors

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

Set `discord-enabled: false` in `config.yml`.
