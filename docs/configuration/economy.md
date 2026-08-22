# Economy & Taxes

SwagBounties handles all economy transactions through **SwagAPI's shared `IEconomyService`**
(hooked at startup — see `SwagBounties#hookSwagAPI()`), not a direct Vault dependency. SwagAPI is
a hard dependency of SwagBounties; if SwagAPI's `IEconomyService` isn't registered, economy
features (placing/claiming/refunding bounties) are disabled and a warning is logged, though the
plugin itself still enables.

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
