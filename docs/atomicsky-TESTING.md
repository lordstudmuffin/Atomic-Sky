# Atomic Sky 1.0.5: testing guide

1.0.0 ran in the Test instance on 2026-10-19 with no errors (both mixins applied). 1.0.1 fixed what the recording
showed, 1.0.2 added the big crater and 1.0.3 the dead zone, suit, heat reach, anchored cloud and visible shock front;
none of these has been run in game yet. The config file in the Test instance was updated to the new defaults. Please test on a **normal (non-superflat) world**: on superflat there are only
three blocks above bedrock, so the crater is a shallow dent you can barely see.

## 0. Does it load? (Done-when 1)
1. Put `atomicsky-1.0.5.jar` in `mods/` (remove the older one). Launch. Check `logs/latest.log` for `atomicsky` errors, especially mixin
   lines (search for `WindEngineMixin`, `ThermodynamicEngineMixin`, "Mixin apply failed").
2. Create a creative superflat or plains world. Search JEI/creative for "Atomic Bomb", "Fissile Core", "Geiger Counter":
   all three should have recipe pages.
3. Let the weather mod run a storm for a minute to be sure its own wind and radar still work with the mixins in.

## 1. Flash and heat (Done-when 2 and 3)
1. Stand 150 to 200 blocks from a grassy, treed area. Run `/atomicsky detonate <x> <y> <z>` on that spot.
2. Expect: instant white-out that fades over about 4 seconds; a 240-block-wide bowl that digs itself out over ten
   seconds (stand well outside it: anything inside the crater radius dies on the first tick); a dead ring 120 blocks
   wide around the crater baked column by column as the front sweeps out, fires burning another 100 blocks past that;
   a dust ring racing outward along the ground; grass and trees within ~120 blocks turning to
   dirt and catching the weather mod's fire; farmland drying; small ponds near the centre gone.
3. Walk into the dead zone (survival, no suit) while the heat is still on: no fire damage and no burning, only
   Radiation Sickness once the fallout comes down (and vanilla fire if you step into a wildfire). The mushroom cloud
   should still be standing 20 minutes later and thin away in the minute after fallout stops.
4. Walk back after 3 minutes: the heat should be over (no new fires start, `/atomicsky status` shows heat done).
   Fires that already burn spread by the weather mod's own wildfire rules.

## 2. Shockwave (Done-when 4)
1. Place some dropped items, a few mobs and a glass wall at 60, 120 and 250 blocks from a bomb.
2. Expect a visible ring of wind-driven weather particles moving out at ~60 blocks/s, a camera shake as it passes you,
   items and mobs thrown outward (far at 60, a nudge at 250), glass shattering near the blast.
3. Stand under a solid roof: the push should be about a third.

## 2b. Radiation suit
Craft or spawn the four Radiation Suit pieces (Combat tab). Wearing all four under the plume: no Radiation Sickness.
Take one piece off: it comes back within a second. Let the sickness kill you at level II (deep in the plume, no suit):
the death message reads "was killed by radiation sickness".

## 3. Fallout (Done-when 5)
1. 25 seconds after the blast the sky under the plume darkens and grey ash starts to fall.
2. Within a few minutes Fallout layers build on exposed ground, deepest near the centre, and the plume drifts slowly
   downwind (about 40 blocks a minute in a 25 mph wind; `/atomicsky status` shows the drift) (hold the Geiger Counter and right-click: it reports fallout level).
3. Stand in the open under the plume: Radiation Sickness appears. Stand under a roof: it does not.
4. Fallout stops after 20 minutes (default); layers decay over ~6 in-game days.

## 4. Nuclear winter (Done-when 6)
1. Detonate, then `/time add 6000` (6 in-game hours; plain number, no `d` suffix: `6000d` skips 6000 *days* and ends
   the winter before it starts) a couple of times and watch the weather mod's thermometer or radar
   temperature readout: it should fall by up to 18 °C compared with before.
2. `/time add 24000` seven times (or set `winter.days` lower first): temperature returns to normal.
3. Set `winter.enabled = false` in `config/atomicsky-common.toml`, restart, detonate: no temperature change.
   `/atomicsky endwinter` cuts a winter short at any time.

## 5. Config (Done-when 7)
Change `blast.blastRadius`, `fallout.durationMinutes`, `winter.days` and restart (common config loads with the world).
`craterRadius` 200 with `craterDepth` 70 gives a lake-sized hole (about 20 seconds of digging); `blastRadius` 48
scales everything else up by half and the crater with it.

## 6. FPS and video (Done-when 8)
Watch the F3 FPS counter during the mushroom cloud and fallout. If it drops too low, set `particleDensity` to 0.5 or
`mushroomCloud = false` in `config/atomicsky-client.toml`. Record the full sequence from ~150 blocks away for the video
(flash, crater, fires, shockwave, darkening sky, ash, then winter via `/time add`).

## 7. The 1.0.5 fixes
- **Bunker.** Dig down 60 blocks under where the bomb will sit, stand there in survival, detonate with the command at the
  surface. You should live (the crater floor is 45 deep). Stand 20 blocks down instead and you should die.
- **Two clouds.** Detonate twice, a few hundred blocks apart. Both mushroom clouds should stand.
- **Relog.** Detonate, wait ten seconds, save and quit, reopen the world. The cloud should be back, fully grown.
- **Far chunks.** Set render distance to 6, detonate, then walk 200 blocks out in any direction after a minute. The
  dead zone should continue without a notch where the chunks had not been loaded.
- **Nether.** Detonate in the Overworld, go to the Nether while the shockwave is still running: no blast wind there.
- **Radiation drain.** Stand in the open under the plume (survival, no suit) and stay there: you should lose half a
  heart every 3 seconds down to the last half-heart at level I, and every 1.5 seconds with no floor at level II (deep
  in the plume, or on 4+ layers of fallout). Before this fix nothing happened while you stayed put.
- **Plume end.** Watch the sky after the 20 minutes: it should clear gradually over about two minutes, not snap.
- **Pond with stairs.** Put waterlogged stairs or a slab in a pond within 100 blocks of the bomb: after the heat the
  stairs are still there, dry; the open water next to them is gone.

## If something breaks
Send `logs/latest.log` (and `crash-reports/` if any). Likely suspects, in order: a mixin that did not apply (then
wind and winter silently fall back to the weather mod's own values), a missing model/texture (pink-black block), a
recipe condition error in the log at world load.
