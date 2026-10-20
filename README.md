# Atomic Sky 1.0.4

A nuke that changes the weather. Built on **ProtoManly's Weather** (required) for Minecraft 1.21.1, NeoForge 21.1.x.

## Requirements
- Minecraft 1.21.1, NeoForge 21.1.0 or newer (tested target: 21.1.262)
- ProtoManly's Weather 0.17.x (and its own dependency, Veil)

Drop `atomicsky-1.0.4.jar` into `mods/` next to the weather mod. Config files appear on first launch as
`config/atomicsky-common.toml` (world side) and `config/atomicsky-client.toml` (visuals).

## What it adds
| Item / block | Recipe | Use |
|---|---|---|
| Fissile Core | glowstone x4, redstone block x4, netherite ingot (centre) | ingredient |
| Atomic Bomb | TNT x4 (corners), iron block x4, Fissile Core (centre) | place, then arm |
| Geiger Counter | comparator over glass pane over redstone, iron ingots at the sides | right-click: fallout and winter readings where you stand |
| Radiation Suit (hood, jacket, trousers, boots) | yellow wool in the usual armour shapes, glass pane visor in the hood, iron ingot in the jacket and boots | the full set keeps Radiation Sickness off in fallout |

Arm the bomb with flint and steel, a fire charge or a redstone signal. It flashes red for `fuseSeconds` (default 10);
sneak-click it to disarm. Breaking an armed bomb also disarms it.

## The sequence
1. **Flash.** Everyone within `flashRange` sees a white-out that fades over a few seconds (stronger when looking at it).
2. **Crater.** A parabolic bowl `craterRadius` blocks across (default 120, deepest 45 blocks at the centre) is dug
   ring by ring over about ten seconds, taking trees and buildings with it; the floor is left glassed and scorched.
   Bedrock, obsidian and other blast-proof blocks survive. Turn off with `destroyBlocks=false`.
3. **Heat pulse and dead zone.** A ring around the crater as wide as the crater itself (`heat.radiusFactor` 2.0 x the
   crater radius, so 240 blocks out by default) is baked dead column by column as the front sweeps outward: grass to
   coarse dirt, farmland drained, shallow water boiled off, canopies burnt away, soil moisture set to dry in the weather
   mod, and fires started on anything flammable and on some bare ground. Past the dead zone the heat and fires keep
   going for another `heat.fadeBeyond` blocks (100), fading with distance. The whole thing weakens over
   `heat.pulseSeconds` (150 s) and is over within minutes; the wildfires then spread under the weather mod's own rules.
   The heat works on terrain only: it never burns or hurts players or mobs, so after the blast itself the only danger
   of the area is radiation (and walking into a fire).
4. **Shockwave.** A ring of wind leaves the crater at `shockwave.speedBlocksPerSecond` and runs out to
   `blastRadius x shockwave.rangeFactor` (default 384 blocks). It is injected into the weather mod's wind engine, so
   its wind-driven particles, smoke and entity pushing all feel it, and on top of that it shoves players, mobs and items
   and shatters glass, leaves, torches and other fragile blocks out to the edge of the heat zone (inside the dead zone it
   also rips off doors, trapdoors, signs, beds and banners). The front is drawn as a ring of dust and debris racing
   outward. Strength falls off with distance; a roof over your head cuts it to a third. The camera shakes as it passes.
5. **Fallout.** After `fallout.delaySeconds` a plume grows to `blastRadius x fallout.radiusFactor` and, for
   `fallout.durationMinutes`, drifts slowly downwind (ash keeps falling on the whole trail between the blast and the
   cloud, and the cloud never wanders more than three plume radii from the blast). Under it the sky and fog darken, ash falls as
   particles and settles on the ground as *Fallout*, a snow-like layer block (up to `maxLayers` deep). Anything out in
   the open under the plume or standing in fallout gets **Radiation Sickness** (slow health and hunger drain; it can
   kill at level II, and the death message says so). A full Radiation Suit keeps it off. Fallout crumbles away over
   `decayDays` in-game days, or shovel it up.
6. **Nuclear winter** (optional, `winter.enabled`). `winter.delayHours` in-game hours after the blast, temperatures
   on the weather mod's calendar start to fall, bottom out `temperatureDropC` lower, and recover after `winter.days`
   calendar days. This feeds the weather mod's thermodynamics (its storms, snow and seasons react), not vanilla biome
   temperature.
7. **Mushroom cloud** (client, `mushroomCloud`). Built from the weather mod's volumetric smoke puffs, animated for the
   anchored over the crater (it only leans slightly with the wind) and standing until the fallout stops, then thinning
   away over a minute. Turn off or lower `particleDensity` on weaker PCs.

## Commands (op level 2)
- `/atomicsky detonate [x y z] [radius]` : detonate without a bomb (default: where you stand, config radius)
- `/atomicsky status` : list active detonations with their phases, how far the plume has drifted and the winter's calendar-day window
- `/atomicsky clear` : stop every detonation in this dimension now (heat, shockwave, fallout and winter)
- `/atomicsky endwinter` : end the nuclear winter only

## Config highlights (`atomicsky-common.toml`)
- `blast.blastRadius` (32): the yield; heat, shockwave and fallout distances scale from it. `blast.craterRadius` (120)
  and `blast.craterDepth` (45): the bowl. `blast.craftable` (true): remove recipes on servers.
- `heat.radiusFactor` (2.0, dead zone as a multiple of the crater radius), `heat.fadeBeyond` (100), `heat.pulseSeconds`
  (150), `heat.fireChance` (0.85), `heat.driesSoil`, `heat.evaporatesWater`
- `shockwave.rangeFactor` (16), `speedBlocksPerSecond` (60), `pushStrength` (6), `windMph` (500), `breaksFragile`
- `fallout.durationMinutes` (20), `delaySeconds` (25), `radiusFactor` (8), `maxLayers` (6), `windDrift` (0.06),
  `radiation`, `decayDays` (6)
- `winter.enabled` (true), `winter.days` (7), `temperatureDropC` (18), `delayHours` (6)

`atomicsky-client.toml`: `flash`, `flashStrength`, `mushroomCloud`, `particleDensity`, `darkSky`, `cameraShake`.

## Performance
Block work is budgeted per tick (crater 5000 blocks/tick, heat and fallout a few hundred columns/tick), entity pushes
only scan the shock ring, and all client visuals are plain particles plus one fog tweak. Fallout particles and the
mushroom cloud scale with `particleDensity`.

## Changes
- 1.0.4: the heat no longer damages or ignites players and mobs (radiation is the only lingering danger); the mushroom
  cloud stays up until the fallout ends.
- 1.0.3: dead zone as wide as the crater around it, baked deterministically; heat and fire reach 100 blocks past it;
  Radiation Suit; effect icon; radiation deaths say so; mushroom cloud stays over the crater; shock front drawn as a
  dust ring, pushes harder and shatters further.
- 1.0.2: crater is now a wide parabolic bowl with its own `craterRadius` (120) and `craterDepth` (45) settings instead of
  a sphere of the yield (24); yield default raised to 32, so heat reaches 160 blocks, the shockwave 512 and the plume 256.
- 1.0.1: fallout plume drifts about six times slower and leaves a trail (1.0.0 blew it hundreds of blocks away within a
  minute, so nothing fell near the blast); the mushroom cloud survives the weather mod's smoke cap (wildfire smoke was
  retiring its puffs within seconds); the heat sweep crosses the zone in a few seconds instead of instantly; status
  shows the winter day window.

## Known limits
- Only 1.21.1 NeoForge: ProtoManly's Weather exists for nothing else.
- The weather mod requires Veil; without a shader-capable GPU its own visuals already struggle, and so will ours.
- Fallout does not feed Mekanism's radiation system; the two are separate.
- The blast ignores FTB Chunks claims the way TNT does.

## Building
Standard NeoForge (ModDevGradle) project: `./gradlew build` puts the jar in `build/libs/`. ProtoManly's Weather is
closed source, so either drop its jar into `libs/` (compile-only, git-ignored) or set `pmweather_version` in
`gradle.properties` to a Modrinth release and the Modrinth maven resolves it. `./gradlew runClient` starts a dev client.

`build-offline.sh` is the no-internet fallback used to make the first releases: plain javac against an exact-signature
stub jar plus the weather mod jar (see the script header and `stubfix/`).

Testing notes: [docs/atomicsky-TESTING.md](docs/atomicsky-TESTING.md).

License: MIT.
