# Economy & Taxes

SwagBounties uses Vault to handle all economy transactions.

## Placement Tax

When a player places a bounty of `X`:

```
amount deducted from placer = X
reward stored               = X * (1 - placement-tax / 100)
```

Example with `placement-tax: 5.0` and a `$1000` bounty:
- Player pays `$1000`
- Stored reward: `$950`

## Expiry Refund

When a bounty expires unclaimed:

```
refund = stored_reward * (1 - expiry-refund-tax / 100)
```

Example with `expiry-refund-tax: 10.0` and a stored reward of `$950`:
- Creator receives `$855`

## Claim

When a bounty is claimed on kill, the killer receives the **total stored reward** across all bounties on the victim. No additional tax is applied at claim time.
