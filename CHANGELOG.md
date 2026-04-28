# CHANGELOG

All notable changes to **AeroModelLib** documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) loosely; versions
correspond to `mod_version` in `stationapi/gradle.properties`.

## [0.2.3] — 2026-04-28

External feedback pass — addresses calmilamsy's review (April 2026) on
build script structure and rendering performance.

### Added
- **Display-list cache** for `renderModelAtRest`: geometry + UV baked into
  4 GL lists per model (one per brightness bucket); brightness × tint
  applied via `glColor4f` outside. Replaces N Tessellator.draw cycles
  with 4 `glCallList` invocations. After-profile drops `nglDrawArrays`
  from 33.6% to 7.5% on the showcase test world.
- **Aero_FrustumCull**: dot-product cone check against the cached camera
  forward, computed per-frame from MC window aspect (Beta hardcodes
  vertical FOV at 70°). 75° floor + 20° margin on horizontal half-FOV
  so 2K and ultrawide setups don't false-cull screen edges. Toggle off
  with `-Daero.frustumcull=false`.
- **Aero_AnimationTickLOD**: distance-tiered animation tick stride
  (1× / ½× / ¼× / skip beyond 256). `recommendedAnimatedDistance(viewDistance)`
  returns a stride radius scaled to the player's render-distance setting.
- **Adaptive LOD swap inside `renderAnimatedInternal`**: far entities
  auto-route to the at-rest display-list path without per-renderer
  changes. Threshold uses `Aero_AnimationTickLOD`'s policy so tick LOD
  and render LOD line up at the same distance.
- **`Aero_RenderOptions.cullFaces(boolean)`**: opt-in `GL_CULL_FACE`
  for models with verified consistent triangle winding (~40% GPU
  triangle savings on closed meshes). Default off — many OBJ exporters
  produce inconsistent winding and silent invisibilify is worse than
  the perf hit.
- **`Aero_MeshRenderer.disposeModel(model)`**: releases cached display
  lists (`glDeleteLists`). Call from hot-reload / shutdown hooks to
  keep the driver from accumulating list IDs.
- **`Aero_RenderDistanceBlockEntity.shouldTickAnimation()` / `Aero_RenderDistanceTileEntity.shouldTickAnimation()`**:
  drop-in tick-LOD test for BE / TE update methods. Uses the per-entity
  age counter for phase-stable thinning.
- **`runClientStress` gradle task**: `-Daero.stresstest=true`, every
  qualifying chunk gets the showcase + a 4×4 motor grid + 3×3×3
  mega-model tower; animated LOD bumped to 256 for max stress.
- **`runClientBenchmark -Pbench=N` gradle task**: same as stress, but
  auto-exits after `N` seconds via `Aero_TestProfilerHook` and dumps
  JFR to `run/aero-benchmark.jfr`. One-command repro for perf reviewers.

### Changed
- **`Aero_Profiler.start` is now zero-allocation**: start time stored on
  the cached `Section` object instead of a parallel `Map<String, Long>`
  (which was boxing `Long.valueOf(System.nanoTime())` per call). Old-gen
  GC pauses dropped from 45 ms to 34 ms in the stress profile.
- **Build script**: composite build via `includeBuild('..')`; the test
  project's `modImplementation "dev.aerocoding:aero-model-lib:${mod_version}"`
  resolves through the composite instead of via `files('../build/libs/...jar')`.
- **`modRuntimeOnly`** for AMI / glass-networking / GCAPI; `downloadAMI`
  task removed. Pinned to mod versions built with the same Loom minor
  (AMI 1.9.0 → 1.6.0, GCAPI 3.2.5 → 3.2.2). modmenu has no Loom-1.9.2
  release, off the stack until a wider Loom bump.
- **`babric:fabric-loader:0.15.6-babric.1`** explicit pin — the upstream
  `net.fabricmc:fabric-loader` collides with StationAPI's transitive
  babric loader at runtime (`duplicate fabric loader classes`).
  `fabric.mod.json` `depends.fabricloader` lowered from `>=0.16.0` to
  `>=0.15.0` to accept the babric fork (Fabric's semver treats
  `-babric.1` as a pre-release, so `>=0.15.6` rejects it).

### Docs
- **README**: AI disclaimer at the top.
- **DOC.md** § 15 Profiling: VisualVM walkthrough alongside JFR + JMC.
- **CHANGELOG.md** (this file): introduced.

## [0.2.1] — 2026-04-26

- Graph render path follow-up + 5 StationAPI test showcases.

## [0.2.0] — 2026-04-25

- Quaternion slerp on rotation channels.
- UV animation channels (`uv_offset`, `uv_scale`).
- Skeletal IK with CCD solver + hierarchical rendering.
- Morph targets (`format_version "1.1"`).
- Animation graph (Blend1D + Additive nodes).
- Five visual showcases per runtime.

## [0.1.0] — initial release

- Static + animated OBJ rendering on ModLoader and StationAPI.
- Strict format-1.0 `.anim.json` validation.
- Aero_AnimationStack (multi-layer animation).
- Aero_AnimationStateRouter (predicate-based state machine).
- Aero_RenderOptions (per-call tint / alpha / blend / depth).
- Aero_RenderDistance LOD (animated / static / culled).
- Locator-anchored sound + particle keyframe events.
- 33 easing curves + smooth state transitions.
- Multiplayer-ready (server/client side gating).
- Aero_Profiler (always-on, zero-cost when off).
- 210 pure-Java unit tests + JMH-style benchmark harness.
