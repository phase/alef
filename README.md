# alef mappings

Library for using various Minecraft mapping sets. This is a continuation / rewrite of my previous
[mapping generator](https://github.com/phase/MinecraftMappings).

## Matches

This uses Fabric's version matches to create cross version mappings. Given a set of matches between two versions, we can
chain them together and use existing mapping sets to create migration mappings between arbitrary versions.

## Providers

Current Providers:
* MCP

Planned Providers:
* Yarn / Intermediary
* Mojang
* Spigot
* [CraftBukkit](https://github.com/agaricusb/MinecraftRemapping)

### Sponge Mappings

* `1.12.2`: `snapshot_20180808`
* `1.14.4`: `20200119-1.14.3`
