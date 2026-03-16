# Bounty Expiry

Unclaimed bounties automatically expire after a configurable number of days.

## Configuration

```yaml
bounty-expiry-days: 7
```

Set to `0` to disable expiry entirely — bounties will remain active until claimed or manually removed.

## Behavior

- The expiry check runs every 5 minutes in the background
- When a bounty expires, the creator receives a partial refund (see [Economy & Taxes](../configuration/economy.md))
- A Discord notification is sent if the bounty meets the threshold
- The expired bounty is removed from the active list

## Refund

```
refund = stored_reward * (1 - expiry-refund-tax / 100)
```

The `expiry-refund-tax` is configured separately from the placement tax to allow different rates.
