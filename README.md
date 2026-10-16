# Starfront Command — M1 Prototype

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-support-yellow)](https://buymeacoffee.com/wilsonsamiano)

Touchscreen-first real-time strategy for Android. **M1 slice:** Terran-only tower
defense — one lane, 10 waves, unlimited resources, Logistics cap as the pacing
constraint. Survive all 10 waves (boss on wave 10) without losing your Command Core.

Full design doc: `../starfront-command-design-doc.md` (in Wilson's files).

## Project layout

```
app/src/main/java/com/wilson/starfront/
  MainActivity.kt  — fullscreen Activity, hosts the GameView
  GameView.kt      — SurfaceView: 60 Hz fixed-timestep loop, all touch input, camera
  Game.kt          — simulation: waves, combat, construction, training, win/lose
  Entities.kt      — Unit / Building / Projectile types + balance stats
  UiState.kt       — selection, camera, HUD button state shared by view + renderer
  Renderer.kt      — Canvas drawing: world, units, HUD, minimap, overlays
```

No engine, no dependencies — pure Android Canvas. `minSdk 26`, landscape.

## Touch controls (M1)

| Gesture | Action |
|---|---|
| Tap unit/building | Select |
| Drag | Box-select units |
| Tap ground (units selected) | Move |
| Long-press ground (units selected) | Attack-move |
| Pinch / two-finger drag | Zoom / pan camera |
| Tap minimap | Jump camera |

Select a **Constructor** → build MG Turret / Barracks → tap map to place.
Select a **Barracks** → train Militia / Ranger / Bulwark; tap map to set rally point.

## Balance knobs (M1)

All in `Entities.kt` (`Stats` object) and `Game.kt` (companion constants):
`LOGISTICS_CAP = 40`, `TOTAL_WAVES = 10`, wave composition in `buildWave()`.

## Build

Open in Android Studio (it will fetch the Gradle distribution on first sync),
then **Build > Build APK(s)**. Or build via Grok Build — see `GROK_BUILD_STEPS.md`.

## In-app updates

The game checks `https://api.github.com/repos/wilsonsamiano/starfront-command/releases/latest`
on launch. If a release tag is newer than the installed build, it offers a
one-tap download + install (same signing key required — see signing below).

**Shipping an update:**
1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle`.
2. Build the signed release APK (same keystore, every time).
3. Create a GitHub release tagged `vN` where **N = the new versionCode**
   (e.g. `versionCode 3` → tag `v3`), and attach the APK to the release.
4. Players get the update prompt on next launch.

## Support

If you enjoy the game, support development: https://buymeacoffee.com/wilsonsamiano

## Roadmap

- **M2** — controller heroes (Psi Operative), builder capture, limited second race camp
- **M3** — all three races, 30 waves, bosses, Endless Siege, tutorial
- **M4** — art/audio pass, meta progression, release build
