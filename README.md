# AeroModelLib

> **Modern animated rendering for Minecraft Beta 1.7.3.** Blockbench
> models, OBJ meshes, GeckoLib-style animation, locator-anchored
> sounds and particles, multi-layer blending, render-distance LOD —
> all on the OpenGL 1.1 fixed-function pipeline that Beta 1.7.3 ships with.

[![Demo](https://img.youtube.com/vi/ewJ0XgnOSHE/maxresdefault.jpg)](https://www.youtube.com/watch?v=ewJ0XgnOSHE)

> **Compatibility:** Java 8 core/ModLoader · JDK 17 StationAPI build · Minecraft Beta 1.7.3 · RetroMCP · ModLoader / Forge 1.0.6 · StationAPI / Babric · LWJGL (OpenGL 1.1+)

---

## Why AeroModelLib?

Beta 1.7.3 modding has spent fourteen years rendering animated machines as
hand-rolled `glRotatef`/`glTranslatef` blocks where every gear is a
forty-line GL push/pop dance and every animation drift bug is a debug
session. AeroModelLib brings the **2024-era authoring workflow** —
Blockbench, OBJ, GeckoLib `.anim.json` — to the engine that started it
all.

- **Author once in Blockbench**, render on both ModLoader and StationAPI.
- **Strict schema validation** — typos in easing names, loop types or
  keyframes fail fast at load time, not silently mid-game.
- **Cross-platform spec API** — declare model + texture + animations +
  transform + LOD as `static final` data; the renderer reads everything
  from one object.
- **Production-grade performance** — pre-baked quads, four-bucket flat
  lighting, bilinear smooth-light cache, alloc-free animation samplers,
  bone-resolution memoization. Designed for dozens of animated multiblocks
  on screen simultaneously.

---

## Killer features

### Quaternion slerp on rotation channels (v0.2.0)

Multi-axis pose interpolation now follows the geodesic path on the
rotation sphere — no more gimbal-style stutter when two rotation axes
animate at once. The lib pre-bakes unit quaternions per keyframe and
slerps short-arc segments automatically. Single-axis rotations and
2-keyframe full revolutions (`0 → 360`) keep their v0.1 visual via a
hybrid heuristic that falls back to euler-lerp when the chosen path
would be ambiguous. Zero opt-in: every existing `.anim.json` looks the
same or smoother.

### UV animation channels (v0.2.0)

Two new optional channels — `uv_offset` and `uv_scale` — animate the
texture coordinates per bone. Use them for conveyor belts, magic-rune
scrolls, glowing energy flows, atlas-sheet sprite cycling, or breathing
texture pulses. Identity transform fast-paths to the raw emit, so a
bone without UV channels pays zero cost.

```json
"uv_offset": {
  "0":   { "value": [0, 0, 0], "interp": "linear" },
  "2.0": { "value": [1, 0, 0], "interp": "linear" }
},
"uv_scale": {
  "0":   { "value": [1.0, 1.0, 0], "interp": "easeInOutSine" },
  "1.0": { "value": [1.2, 1.2, 0], "interp": "easeInOutSine" },
  "2.0": { "value": [1.0, 1.0, 0], "interp": "easeInOutSine" }
}
```

### Skeletal IK with CCD solver + hierarchical rendering (v0.2.0)

Bones now compose hierarchically through `childMap` — rotating a parent
bone moves every animated descendant with it, like Blockbench's animator.
On top of that, `Aero_IkChain` + `Aero_CCDSolver` mutate intermediate
rotations so an end-effector tracks any world target each frame. Hook
your own resolver — nearest player's eye for a turret, ground raycast
for foot planting, anything you can compute per render call:

```java
Aero_IkChain trackPlayer = new Aero_IkChain() {
    public String[] getBoneChain() { return new String[]{"base", "arm", "tip"}; }
    public boolean resolveTargetInto(float[] worldPos) {
        EntityPlayer p = world.getClosestPlayer(x, y, z, 16.0);
        if (p == null) return false;
        worldPos[0] = (float) ((p.posX - x) * 16.0);
        worldPos[1] = (float) ((p.posY + p.getEyeHeight() - y) * 16.0);
        worldPos[2] = (float) ((p.posZ - z) * 16.0);
        return true;
    }
};
Aero_MeshRenderer.renderAnimated(MODEL, bundle, def, state, x, y, z,
    brightness, partialTick, options, proceduralPose, new Aero_IkChain[]{trackPlayer});
```

### Morph targets / blend shapes (v0.2.0)

Pulse a crystal between forms, deform a slime body, animate a mob's
facial expression — anything that needs **vertex-level deformation**
beyond bone rotation. Variants live as separate OBJs with matching
topology, declared in the bundle's `morph_targets` block (schema
`format_version "1.1"`, fully backward-compatible with v1.0). Per-frame
weights drive a per-vertex blend `final = base + Σ(weight × delta)`,
fast-path skipped when all weights are zero.

```json
"format_version": "1.1",
"morph_targets": {
  "smile":    "/models/Robot_smile.obj",
  "expanded": "/models/Crystal_expanded.obj"
}
```

```java
// Tile-side: oscillate the morph weight.
morphState.set("expanded", 0.5f + 0.5f * (float) Math.sin(phase));
```

### Animation graph (Blend1D + Additive nodes) (v0.2.0)

`Aero_AnimationGraph` composes clips into a tree — Blend1D nodes lerp
N children by a float param, Additive nodes layer overlays on a base.
Coexists with the flat `Aero_AnimationStack`; pick whichever fits.
Drive params from gameplay (movement speed, redstone, spell intensity)
and the graph blends smoothly without the consumer wiring per-bone math:

```java
Aero_GraphNode root = new Aero_GraphBlend1DNode("speed",
    new float[]{0f, 1f},
    new Aero_GraphNode[]{
        new Aero_GraphClipNode(slowPlayback),
        new Aero_GraphClipNode(fastPlayback)
    });
Aero_AnimationGraph graph = new Aero_AnimationGraph(root, params);

// Each tick:
params.setFloat("speed", normalizedRedstoneOrInputDelta);
Aero_MeshRenderer.renderAnimated(MODEL, graph, bundle, x, y, z, brightness, partialTick);
```

### Locator-anchored sounds & particles

Drop a `keyframes` block into your `.anim.json` and tag each event with a
**bone locator**. The lib resolves the locator's *current animated
position* at fire time — so a `random.click` declared on `shredder_L` plays
from wherever the left shredder *actually is* this tick, not from the tile
origin. A `smoke` particle on `turbine_l` emits from the spinning blade.
Sounds and particles **follow the moving mesh**.

```json
"keyframes": {
  "sound":    {
    "0.5": { "name": "random.click",    "locator": "shredder_L" },
    "1.5": { "name": "tile.piston.out", "locator": "shredder_R" }
  },
  "particle": {
    "0.0": { "name": "smoke", "locator": "turbine_l" },
    "0.25": { "name": "flame", "locator": "turbine_r" }
  }
}
```

Wire a router on the playback and the lib does the rest:

```java
animState.setEventListener(Aero_AnimationEventRouter.builder()
    .onChannel("sound",    (ch, name, locator, t) -> playSoundAt(locator, name))
    .onChannel("particle", (ch, name, locator, t) -> spawnParticleAt(locator, name))
    .build());
```

### 33 easing curves

Per-keyframe `interp` picks one of `linear`, `step`, `catmullrom`, plus
**every GeckoLib-style ease** — `easeInBack`, `easeOutElastic`,
`easeOutBounce`, the full `{In,Out,InOut} × {Sine,Quad,Cubic,Quart,Quint,Expo,Circ,Back,Elastic,Bounce}`
matrix. Unknown curve names throw at load time.

```json
"0.5": { "value": [0, 8, 0],  "interp": "easeOutBack" },
"1.0": { "value": [0, 0, 0],  "interp": "easeInBounce" }
```

### Smooth state transitions

Snap between clips, or **crossfade**:

```java
animState.setStateWithTransition(STATE_WALK, 6);   // 6-tick blend
```

The blend handles bones present in only one clip cleanly (fade-in / fade-out
to identity), so swapping between an "idle hands" pose and a full attack
animation doesn't pop. Or declare the default once on the spec:

```java
Aero_AnimationSpec.builder("/models/MyMob.anim.json")
    .state(0, "idle").state(1, "walk").state(2, "attack")
    .defaultTransitionTicks(6)
    .build();
```

### Multi-layer animation Stack

Compose a base walk loop + an additive arm-wave overlay + an additive
head-look in three lines — like Unity's animator layers, on Beta 1.7.3:

```java
Aero_AnimationStack stack = Aero_AnimationStack.builder()
    .replace(walkPlayback)                   // base
    .additive(armWavePlayback, 0.8f)         // arm-wave overlay at 80% weight
    .additive(headLookPlayback, 1.0f)        // head-look overlay
    .build();

stack.tick();
Aero_MeshRenderer.renderAnimated(MODEL, stack, x, y, z, brightness, partialTick);
```

Scale composes multiplicatively, rotation/position add. Bones missing
from a layer's clip pass through unchanged. The Stack renderer resolves a
bone's full pose in one pass (`samplePose`) instead of repeating layer,
clip and bone lookups for rotation, position and scale separately.

### Procedural pose hook (vehicles, input-driven rotations)

Keyframed animation handles idle/walk/attack cleanly, but a tank's turret
follows the rider's mouse and a plane's propeller spins proportional to
throttle — those don't fit in a `.anim.json` track. `Aero_ProceduralPose`
is a per-frame render hook that **layers runtime rotations on top of the
keyframe pose**, so the spec stays declarative and the input-driven parts
compose without escaping into a parallel render path:

```java
// Spec stays pure data, shared as static final.
public static final Aero_ModelSpec MODEL =
    Aero_ModelSpec.mesh("/models/Tank.obj")
        .animations(Aero_AnimationSpec.builder("/models/Tank.anim.json")
            .state(0, "idle").state(1, "moving")
            .build())
        .build();

// At render time, inject per-frame deltas:
Aero_EntityModelRenderer.render(MODEL, tank.animState,
    entity, x, y, z, yaw, partialTick,
    new Aero_ProceduralPose() {
        public void apply(String bone, Aero_BoneRenderPose p) {
            if ("turret".equals(bone))    p.rotY += tank.turretYaw;
            if ("barrel".equals(bone))    p.rotX += tank.barrelPitch;
            if ("propeller".equals(bone)) p.rotX += (tank.age + partialTick) * tank.rpm;
        }
    });
```

This unlocks Flans-mod-style vehicles (tanks, planes, helicopters) inside
the same declarative spec API used for blocks, multiblocks, and mobs.
Composes with the multi-layer Stack as well.

### Predicate state router

Instead of an `if/else` ladder in your tick method:

```java
new Aero_AnimationStateRouter()
    .when(p -> entity.isDead(),       STATE_DEATH)
    .when(p -> entity.isAttacking(),  STATE_ATTACK)
    .when(p -> entity.isMoving(),     STATE_WALK)
    .otherwise(STATE_IDLE)
    .withTransition(6)
    .applyTo(animState);
```

### Per-call render styling

Tint, alpha, blend mode (alpha / **additive** for energy beams + plasma
glow), depth-test toggle — all via immutable `Aero_RenderOptions`:

```java
Aero_RenderOptions overheat = Aero_RenderOptions.tint(1f, 0.45f, 0.35f);
Aero_RenderOptions plasma   = Aero_RenderOptions.additive(0.8f);
Aero_RenderOptions ghost    = Aero_RenderOptions.translucent(0.4f);
```

### Render-distance aware LOD

Big multiblocks shouldn't pop in at 64 blocks like a piece of cobblestone.
The lib hooks into the player's render distance setting and exposes a
three-band LOD result your renderer can read at zero cost:

```java
Aero_RenderLod lod = Aero_RenderDistance.lodRelative(d, d1, d2, 2d, 48d);
if (lod.shouldAnimate())      Aero_MeshRenderer.renderAnimated(MODEL, state, ...);
else if (lod.isStaticOnly())  Aero_MeshRenderer.renderModelAtRest(MODEL, ...);
// CULLED: skip entirely
```

Or let `Aero_ModelSpec` infer LOD automatically.

### Multiplayer-ready

- Tick is local on each side — both server and client step at 20 TPS.
- NBT serialization is the same one used for description packets.
- `Aero_AnimationSide.isServerSide(world)` gates broadcasting actions
  (sounds) so SMP doesn't double-play. Particles fire on both sides
  freely (server-side `spawnParticle` is a no-op).

See [DOC.md § Multiplayer](DOC.md#multiplayer) for the full SMP recipe.

### Always-on profiler, zero cost when off

Disabled calls short-circuit on a single boolean read. Flip it on with a
JVM flag, run, and `dump()` shows where ticks went:

```bash
java -Daero.profiler=true ...
```

```
[Aero_Profiler] section                         calls       total ms     avg us
[Aero_Profiler]   aero.playback.tick            12000          180.5      15.04
[Aero_Profiler]   aero.mesh.renderAnimated       1800          340.2     189.00
[Aero_Profiler]   aero.mesh.render               4200           42.1      10.02
```

The lib auto-instruments the four hot paths; add your own sections for
application work.

### Integrated tooling

`tools/convert.sh MyMachine.bbmodel` produces a valid `.anim.json` directly
from your Blockbench file. The transpile pipeline turns the StationAPI
sources into ModLoader-flat layout for RetroMCP automatically. JFR launch
script for full method-level profiling. Pure-Java unit test suite (no MC
runtime needed) so refactors stay safe. GitHub Actions also run tests,
StationAPI builds, CodeQL, dependency review, Gitleaks, Trivy and Gradle
wrapper validation; Actions are pinned by SHA and Gradle dependency updates
are tracked by Dependabot.

---

## Quick Start

### Static block model (Blockbench JSON)

```java
public static final Aero_JsonModel MODEL =
    Aero_JsonModelLoader.load("/models/MyBlock.json");

// in TileEntitySpecialRenderer:
bindTextureByName("/block/my_texture.png");
float brightness = tile.worldObj.getLightBrightness(x, y + 1, z);
Aero_JsonModelRenderer.renderModel(MODEL, d, d1, d2, 0f, brightness);

// in inventory:
Aero_InventoryRenderer.render(renderer, MODEL);
```

### Animated OBJ machine

```java
// ── TileEntity ──
public static final int STATE_OFF = 0;
public static final int STATE_ON  = 1;

public static final Aero_AnimationSpec ANIMATION =
    Aero_AnimationSpec.builder("/models/MyMachine.anim.json")
        .state(STATE_OFF, "idle")
        .state(STATE_ON,  "working")
        .build();

public final Aero_AnimationState animState = ANIMATION.createState();

public void updateEntity() {
    animState.tick();                                         // ALWAYS first
    ANIMATION.applyState(animState, isRunning ? STATE_ON : STATE_OFF);
}

// ── TileEntitySpecialRenderer ──
public static final Aero_MeshModel MODEL =
    Aero_ObjLoader.load("/models/MyMachine.obj");

bindTextureByName("/block/my_texture_hq.png");
Aero_RenderLod lod = Aero_RenderDistance.lodRelative(d, d1, d2, 2d, 48d);
if (lod.shouldAnimate()) {
    Aero_MeshRenderer.renderAnimated(MODEL, tile.animState,
        d, d1, d2, brightness, partialTick);
} else if (lod.isStaticOnly()) {
    Aero_MeshRenderer.renderModelAtRest(MODEL, d, d1, d2, 0f, brightness);
}
```

### Animated mob (Entity + Renderer)

The `Aero_ModelSpec` lives on the Entity class so the constructor and the
renderer share the same culling/LOD configuration — no redundant literals
to keep in sync.

```java
// In your Entity class:
public static final Aero_ModelSpec MODEL =
    Aero_ModelSpec.mesh("/models/MyMob.obj")
        .texture("/mob/my_mob.png")
        .animations(Aero_AnimationSpec.builder("/models/MyMob.anim.json")
            .state(0, "idle").state(1, "walk").state(2, "attack")
            .defaultTransitionTicks(4)
            .build())
        .offset(-0.5f, 0f, -0.5f)
        .cullingRadius(2f)        // visual radius — used by both ctor and LOD
        .animatedDistance(48d)    // beyond this, render at-rest
        .build();

public final Aero_AnimationState animState = MODEL.createState();

public MyMob(World world) {
    super(world);
    // Reads the spec's cullingRadius + maxRenderDistance — single source of truth.
    Aero_RenderDistance.applyEntityRenderDistance(this, MODEL);
}

public void onLivingUpdate() {
    super.onLivingUpdate();
    animState.tick();
    MODEL.applyState(animState,
        isSwinging ? 2 : isMoving() ? 1 : 0);
}

// In your Renderer:
public void doRender(Entity entity, double x, double y, double z,
                     float yaw, float partialTick) {
    loadTexture(MyMob.MODEL.getTexturePath());
    Aero_EntityModelRenderer.render(MyMob.MODEL, ((MyMob) entity).animState,
        entity, x, y, z, yaw, partialTick);   // computes LOD from the spec
}
```

---

## Asset workflow

```
┌─────────────┐    Blockbench    ┌──────────────┐     OBJ + bbmodel       ┌────────────────┐
│  Author in  │ ───────────────► │   .obj +     │ ──────────────────────► │  tools/convert │
│  Blockbench │                  │   .bbmodel   │                         │      .sh       │
└─────────────┘                  └──────────────┘                         └───────┬────────┘
                                                                                  │
                                                                                  ▼
                                                                          ┌────────────────┐
                                                                          │  .anim.json    │
                                                                          │  (strict 1.0)  │
                                                                          └───────┬────────┘
                                                                                  │
                                          ┌───────────────────────────────────────┘
                                          ▼
┌───────────────────────────┐    ┌──────────────────────────┐
│   ModLoader / Forge       │    │  StationAPI / Babric     │
│   transpile.sh + RetroMCP │    │  Loom (JDK 17)           │
└───────────────────────────┘    └──────────────────────────┘
```

Both runtimes share the same `core/` package — ~67% of the lib is pure
Java that compiles and runs identical on both.

---

## Class index

| Class | Role |
|-------|------|
| **Models** | |
| `Aero_JsonModel` | Parsed Blockbench JSON model (elements as float[30] arrays) |
| `Aero_MeshModel` | Parsed OBJ model with named groups + brightness classification |
| `Aero_JsonModelLoader` | Loads + caches `.json` models from classpath |
| `Aero_ObjLoader` | Loads + caches `.obj` models from classpath |
| **Renderers** | |
| `Aero_JsonModelRenderer` | Renders JSON models in the world |
| `Aero_MeshRenderer` | Renders OBJ models (static / atRest / animated / per-group) |
| `Aero_EntityModelRenderer` | Renders models from entity renderers with entity-origin transform |
| `Aero_InventoryRenderer` | Renders any model type as inventory thumbnail |
| **Render styling** | |
| `Aero_RenderOptions` | Immutable per-call tint / alpha / blend / depth-test |
| `Aero_MeshBlendMode` | `OFF` / `ALPHA` / `ADDITIVE` blend func selector |
| `Aero_EntityModelTransform` | Immutable entity offset / scale / yaw conversion |
| **Render distance + LOD** | |
| `Aero_RenderDistance` | Loader adapter for current render distance + culling |
| `Aero_RenderDistanceCulling` | Pure shared culling math used by both runtimes |
| `Aero_RenderLod` | Three-band LOD result: animated / static / culled |
| `Aero_RenderDistanceTileEntity` / `Aero_RenderDistanceBlockEntity` | Optional bases for special renderers that scale with view distance |
| **Animation data** | |
| `Aero_AnimationBundle` | All clips + pivots + childMap from a `.anim.json` |
| `Aero_AnimationClip` | Single clip with keyframes per bone, plus optional non-pose events |
| `Aero_AnimationLoop` | Loop type enum: `LOOP` / `PLAY_ONCE` / `HOLD_ON_LAST_FRAME` |
| `Aero_AnimationLoader` | Loads + caches `.anim.json` files; strict format-1.0 validation |
| `Aero_Easing` | 33 interpolation curves (linear / step / catmullrom + 30 GeckoLib-style) |
| **Animation runtime** | |
| `Aero_AnimationDefinition` | Maps state IDs to clip names |
| `Aero_AnimationPlayback` | Platform-neutral playback: tick / setState / setStateWithTransition / interpolation / animated-pivot |
| `Aero_AnimationState` | Loader-specific playback state with NBT persistence |
| **Declarative specs** | |
| `Aero_AnimationSpec` | Bundle + state map + default transition + factory methods |
| `Aero_ModelSpec` | Model + texture + animations + transform + render options + LOD |
| **Multi-layer + routing** | |
| `Aero_AnimationLayer` | One playback head inside a stack (additive flag + weight) |
| `Aero_AnimationStack` | Ordered collection of layers, exposes per-bone combined sample |
| `Aero_AnimationPredicate` | Single-method `test(playback) → bool` for the state router |
| `Aero_AnimationStateRouter` | `when(...).otherwise(...).withTransition(N)` rule chain |
| `Aero_AnimationEventListener` | Receives sound / particle / custom keyframes with optional bone locator |
| `Aero_AnimationEventRouter` | Declarative `(channel, name) → handler` event routing |
| **Procedural pose** | |
| `Aero_ProceduralPose` | Render-time hook that layers runtime / input-driven rotations on top of the keyframe pose |
| `Aero_BoneRenderPose` | Mutable per-bone pose (rotation/offset/scale fields) passed to the procedural hook |
| **Multiplayer + observability** | |
| `Aero_AnimationSide` | `isServerSide(world)` / `isClientSide(world)` — gate event side-effects per side |
| `Aero_Profiler` | Always-on, zero-cost-when-off section timer; auto-instruments hot paths |
| **Tools** | |
| `Aero_Convert` | CLI: converts `.bbmodel` → `.anim.json` (standalone, JDK 8+) |

---

## File formats

### `.anim.json` (format 1.0, strict)

```json
{
  "format_version": "1.0",
  "pivots": {
    "fan": [24.0, 44.5, 47.0]
  },
  "childMap": {
    "blade_0": "fan"
  },
  "animations": {
    "working": {
      "loop": "loop",
      "length": 1.0,
      "bones": {
        "fan": {
          "rotation": {
            "0.0": { "value": [0,   0, 0], "interp": "linear" },
            "0.5": { "value": [0, 180, 0], "interp": "easeInOutBack" },
            "1.0": { "value": [0, 360, 0], "interp": "linear" }
          }
        }
      },
      "keyframes": {
        "sound":    { "0.5": { "name": "random.click", "locator": "fan" } },
        "particle": { "0.0": { "name": "smoke",        "locator": "exhaust" } },
        "custom":   { "0.0": { "name": "CYCLE_START" } }
      }
    }
  }
}
```

- **Pivots**: Blockbench pixels (auto-divided by 16 in the loader)
- **Rotation**: Euler degrees [X, Y, Z], applied Z→Y→X (Bedrock compatible)
- **Position**: Blockbench pixels (divided by 16 in the renderer)
- **Pose keyframes**: every segment is `{"value": [x, y, z], "interp": "..."}`. The interp picks one of the 33 [easing curves](DOC.md#easing-curves); unknown names throw at load time.
- **Loop types**: `"loop"` / `"play_once"` / `"hold_on_last_frame"`.
- **Keyframe events**: every entry under `keyframes` is `{"name": "...", "locator": "boneName"}`. Channel is the parent key (`sound`, `particle`, `custom`, or any string the listener routes); locator is optional.
- **`format_version`**: required string; the loader currently accepts `"1.0"` only. Future schema bumps reject mismatched versions loudly instead of silently half-parsing.

### `.obj`

Standard Wavefront OBJ. Use `o` or `g` directives to create named groups
for animated parts — those names become the bone identifiers in the
`.anim.json` and the locators in keyframe events.

---

## Best practices

- Store loaders + specs as `static final` fields — caching is automatic.
- Loader caches are synchronized and bounded to 512 entries by default;
  override with `-Daero.modellib.cache.maxEntries=N` for unusual hot-reload
  workflows. `clearCache()` is available on every loader for tests and
  tooling.
- Call `tick()` **before** `setState()` / `applyState()` every tick.
- Persist animation state via `writeToNBT()` / `readFromNBT()`.
- Use `STATE_OFF = 0` as default (NBT returns 0 when the key is absent).
- Bind your texture **before** calling any render method.
- For SMP: gate sound events through `Aero_AnimationSide.isServerSide(world)`
  so `playSoundEffect` broadcasts once; particles fire freely on both sides.

---

## Documentation

[DOC.md](DOC.md) covers the full API reference, architecture diagrams,
end-to-end examples (full mod + tile + renderer + JSON), troubleshooting,
and the multiplayer recipe.

| Section | What |
|---------|------|
| [§ 1 Quick Start](DOC.md#1-quick-start) | 3-step + 5-step recipes for static and animated models |
| [§ 2 Architecture](DOC.md#2-architecture) | Mindmap + class dependency graph |
| [§ 5 Animations](DOC.md#5-animations) | Schema, sampling, per-channel keyframes |
| [§ 7 Advanced Animation](DOC.md#7-advanced-animation) | Easings, transitions, keyframe events, Stack, router |
| [§ 8 API Reference](DOC.md#8-api-reference) | Every public class with method tables |
| [§ 11 Patterns](DOC.md#11-patterns--best-practices) | Multiplayer, NBT, LOD, render-distance idioms |
| [§ 14 End-to-end example](DOC.md#14-full-end-to-end-example) | A complete simple-crusher mod, copy-paste ready |
| [§ 15 Profiling](DOC.md#15-development-tests--benchmarks) | Aero_Profiler + JFR launch + JMC analysis |

---

## Development

```powershell
# Pure-Java unit tests (no Minecraft runtime required)
powershell -ExecutionPolicy Bypass -File modloader/tests/run.ps1

# Microbenchmark for geometry caches and animation sampling
powershell -ExecutionPolicy Bypass -File modloader/tests/bench.ps1

# StationAPI library build (requires JDK 17+)
cd stationapi
.\gradlew.bat build

# StationAPI integration test mod build
cd test
.\gradlew.bat build

# In-game StationAPI smoke test (animated entity probe in spawn chunks)
.\gradlew.bat runClient
```

The CI workflow mirrors these checks and adds security coverage: CodeQL,
dependency review, Gitleaks secret scan, Trivy filesystem scan, Gradle
wrapper validation, pinned GitHub Actions and Dependabot for GitHub Actions
and Gradle updates.

---

## Author

**lucasrgt**

## License

MIT
