# Anonymous Bounties

Players can hide their identity when placing a bounty using the `--anon` flag.

## Usage

```
/bounty set <player> <amount> --anon
```

## Behavior

- In `/bounty list` and the GUI, the creator is shown as **"Anonymous"**
- The broadcast message uses `bounty-anon-message` instead of `bounty-set-message`
- **Admins** can always see the real creator using `/bountyadmin inspect <player>`

## Notes

The `--anon` flag can appear anywhere after the amount:

```
/bounty set Notch 500 --anon
```
