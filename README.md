# AeroModelLib

3D rendering and animation library for Minecraft Beta 1.7.3 — like GeckoLib, but for RetroMCP's OpenGL 1.1 pipeline.

Demo Animated Machine on YouTube:

[![Demo](https://img.youtube.com/vi/ewJ0XgnOSHE/maxresdefault.jpg)](https://www.youtube.com/watch?v=ewJ0XgnOSHE)

> **Compatibility:** Java 8 core/ModLoader | JDK 17 StationAPI build | Minecraft Beta 1.7.3 | RetroMCP | ModLoader / Forge 1.0.6 | StationAPI | LWJGL (OpenGL 1.1+)

## Features

- **Blockbench model rendering** — Use exported JSON models for blocks, items and world renderers
- **OBJ mesh rendering** — Bring textured mesh assets into Minecraft Beta with named parts for animation
- **GeckoLib-style animation workflow** — Define reusable clips and states, then drive per-instance playback in game
- **Declarative integration specs** — Describe animations, model paths, texture, transform, tint and LOD once, then render from small, readable mod code
- **30+ easing curves** — Per-keyframe `interp`: `easeInBack`, `easeOutElastic`, `easeOutBounce`, etc. on top of `linear` / `step` / `catmullrom`
- **Smooth state transitions** — `setStateWithTransition(state, ticks)` snapshots the previous pose and blends it into the new clip over N ticks
- **Loop types** — `loop` / `play_once` / `hold_on_last_frame`, with `state.isFinished()` to chain clips
- **Keyframe events** — Sound / particle / custom keyframes fired through a listener, with optional bone "locator" so events anchor to a specific part of the moving mesh
- **Multi-layer playback** — `Aero_AnimationStack` runs several clips at once (base + additive overlays), composing per-bone deltas multiplicatively for scale and additively for rotation/position
- **Predicate state router** — Declarative `when(...).when(...).otherwise(...)` chain replaces the giant `if/else` in the consumer's tick method
- **Smooth animation playback** — Interpolate between ticks for fluid motion on classic 20 TPS logic
- **Entity model support** — Render the same Aero models from mob/entity renderers with reusable transforms
- **Render-distance aware visibility** — Keep large block and entity models visible at the right player settings
- **Distance LOD for animated scenes** — Tune when dense animated models render fully, render static, or disappear
- **Inventory previews** — Reuse world models as item and block thumbnails
- **ModLoader and StationAPI support** — Share one core model/animation pipeline across both loaders

## Quick Start

### Static Model (Blockbench JSON)

```java
// Load (cached automatically — use a static field)
public static final Aero_JsonModel MODEL =
    Aero_JsonModelLoader.load("/models/MyBlock.json");

// Render in TileEntitySpecialRenderer
bindTextureByName("/block/my_texture.png");
float brightness = tile.worldObj.getLightBrightness(x, y + 1, z);
Aero_JsonModelRenderer.renderModel(MODEL, d, d1, d2, 0f, brightness);

// Render in inventory
Aero_InventoryRenderer.render(renderer, MODEL);
```

### Animated OBJ Model

```java
// ── TileEntity ──

// ModLoader: extend Aero_RenderDistanceTileEntity.
// StationAPI: extend Aero_RenderDistanceBlockEntity.
// Override getAeroRenderRadius() for models that visually extend past 1 block.
// Override getAeroMaxRenderDistance() only for light/landmark models.

public static final int STATE_OFF = 0;
public static final int STATE_ON  = 1;

public static final Aero_MeshModel MODEL =
    Aero_ObjLoader.load("/models/MyMachine.obj");

public static final Aero_AnimationBundle BUNDLE =
    Aero_AnimationLoader.load("/models/MyMachine.anim.json");

public static final Aero_AnimationDefinition ANIM_DEF =
    new Aero_AnimationDefinition()
        .state(STATE_OFF, "idle")
        .state(STATE_ON,  "working");

public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

public void updateEntity() {
    animState.tick();                              // ALWAYS first
    animState.setState(isRunning ? STATE_ON : STATE_OFF); // AFTER tick
}

protected double getAeroRenderRadius() {
    return 2.0d;
}

protected double getAeroMaxRenderDistance() {
    return 96.0d; // default; use 128/256 only after profiling
}

// ── TileEntitySpecialRenderer ──

bindTextureByName("/block/my_texture_hq.png");
Aero_RenderLod lod = Aero_RenderDistance.lodRelative(d, d1, d2, 2d, 48d);
if (lod.shouldAnimate()) {
    Aero_MeshRenderer.renderAnimated(MODEL, BUNDLE, ANIM_DEF, tile.animState,
        d, d1, d2, brightness, partialTick);
} else if (lod.isStaticOnly()) {
    Aero_MeshRenderer.renderModelAtRest(MODEL, d, d1, d2, 0f, brightness);
}
```

### Entity Model (Mob Renderer)

```java
// In your Entity class
public static final int STATE_IDLE   = 0;
public static final int STATE_WALK   = 1;
public static final int STATE_ATTACK = 2;

public static final Aero_AnimationSpec ANIMATION =
    Aero_AnimationSpec.builder("/models/MyMob.anim.json")
        .state(STATE_IDLE,   "idle")
        .state(STATE_WALK,   "walk")
        .state(STATE_ATTACK, "attack")
        .build();

public final Aero_AnimationState animState = ANIMATION.createState();

public MyMob(World world) {
    super(world);
    Aero_RenderDistance.applyEntityRenderDistance(this, 2.0d);
}

public void onLivingUpdate() {
    super.onLivingUpdate();
    animState.tick();
    animState.setState(isSwinging ? STATE_ATTACK : isMoving() ? STATE_WALK : STATE_IDLE);
}

// In your Render / EntityRenderer class
private static final Aero_ModelSpec MODEL =
    Aero_ModelSpec.mesh("/models/MyMob.obj")
        .texture("/mob/my_mob.png")
        .animations(MyMob.ANIMATION)
        .offset(-0.5f, 0f, -0.5f)
        .cullingRadius(2f)
        .animatedDistance(48d)
        .maxRenderDistance(96f)
        .build();

public void doRender(Entity entity, double x, double y, double z,
                     float yaw, float partialTick) {
    MyMob mob = (MyMob) entity;

    loadTexture(MODEL.getTexturePath());
    Aero_RenderLod lod = Aero_RenderDistance.lodRelative(MODEL, x, y, z);
    Aero_EntityModelRenderer.render(MODEL, mob.animState, lod,
        entity, x, y, z, yaw, partialTick);
}
```

## Classes

| Class | Role |
|-------|------|
| `Aero_JsonModel` | Parsed Blockbench JSON model (elements as float[30] arrays) |
| `Aero_JsonModelLoader` | Loads + caches `.json` models from classpath |
| `Aero_JsonModelRenderer` | Renders JSON models (world) |
| `Aero_InventoryRenderer` | Renders any model type as inventory thumbnail (auto-scale + center) |
| `Aero_MeshModel` | Parsed OBJ model with named groups + brightness classification |
| `Aero_ObjLoader` | Loads + caches `.obj` models from classpath |
| `Aero_MeshRenderer` | Renders OBJ models (static, animated, per-group) |
| `Aero_EntityModelRenderer` | Renders JSON/OBJ models from entity renderers with entity-origin transform |
| `Aero_EntityModelTransform` | Immutable entity offset/scale/yaw conversion settings |
| `Aero_RenderDistance` | Loader adapter for current render distance, entity multipliers and culling checks |
| `Aero_RenderDistanceCulling` | Pure shared culling math used by ModLoader and StationAPI |
| `Aero_RenderLod` | Render-distance LOD result: animated, static-at-rest or culled |
| `Aero_RenderOptions` | Explicit render styling such as per-call mesh tint |
| `Aero_ModelSpec` | Declarative model contract: model path, texture path, animations, transform, render options and LOD |
| `Aero_RenderDistanceTileEntity` / `Aero_RenderDistanceBlockEntity` | Optional ModLoader/StationAPI bases that make special renderers scale with render distance under a configurable cap |
| `Aero_AnimationBundle` | All clips + pivots + childMap from a `.anim.json` |
| `Aero_AnimationClip` | Single animation clip with keyframes per bone, plus optional non-pose events |
| `Aero_AnimationDefinition` | Maps state IDs to clip names (one per machine type) |
| `Aero_AnimationSpec` | Declarative animation contract: bundle + state map with playback/state factories |
| `Aero_AnimationPlayback` | Platform-neutral playback engine with tick / setState / setStateWithTransition / interpolation / animated-pivot resolver |
| `Aero_AnimationState` | Loader-specific playback state with NBT persistence |
| `Aero_AnimationLoader` | Loads + caches `.anim.json` files from classpath |
| `Aero_Easing` | 33 interpolation curves (linear / step / catmullrom + 30 GeckoLib-style easings) |
| `Aero_AnimationLayer` | One playback head inside a stack (additive flag + weight) |
| `Aero_AnimationStack` | Ordered collection of layers, exposes per-bone combined sample {Rot,Pos,Scl} |
| `Aero_AnimationEventListener` | Receives sound / particle / custom keyframes with optional bone locator |
| `Aero_AnimationPredicate` | Single-method `test(playback) → bool` for the state router |
| `Aero_AnimationStateRouter` | `when(...).otherwise(...).withTransition(N)` rule chain that picks the next state |
| `Aero_Profiler` | Optional named-section timer for manual profiling |
| `Aero_Convert` | CLI tool: converts `.bbmodel` → `.anim.json` (standalone, not bundled in mod) |

## File Formats

### `.anim.json`

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
    "idle": {
      "loop": "loop",
      "length": 2.0,
      "bones": {
        "fan": {
          "rotation": {
            "0.0": { "value": [0, 0, 0], "interp": "linear" },
            "2.0": { "value": [0, 0, 0], "interp": "linear" }
          }
        }
      }
    },
    "working": {
      "loop": "hold_on_last_frame",
      "length": 1.0,
      "bones": {
        "fan": {
          "rotation": {
            "0.0": { "value": [0,   0, 0], "interp": "linear" },
            "0.5": { "value": [0, 180, 0], "interp": "easeInOutBack" },
            "1.0": { "value": [0, 360, 0], "interp": "linear" }
          },
          "position": {
            "0.0": { "value": [0, 0, 0], "interp": "linear" }
          }
        }
      },
      "keyframes": {
        "sound":    { "0.5": { "name": "random.click", "locator": "fan" } },
        "particle": { "1.0": { "name": "smoke",        "locator": "exhaust" } },
        "custom":   { "0.0": { "name": "CYCLE_START" } }
      }
    }
  }
}
```

The schema is strict v2 — the loader rejects unknown easings, boolean loops
and shorthand keyframes. Convert existing `.bbmodel` animation exports with
`tools/convert.sh` or `tools\convert.bat`.

- **Pivots**: Blockbench pixels (auto-divided by 16 in the loader)
- **Rotation**: Euler degrees [X, Y, Z], applied Z→Y→X (Bedrock compatible)
- **Position**: Blockbench pixels (divided by 16 in the renderer)
- **Pose keyframes**: every segment is `{"value": [x, y, z], "interp": "..."}`. The interp picks one of the 33 [easing curves](DOC.md#easing-curves); unknown names throw at load time.
- **Loop types**: must be `"loop"` / `"play_once"` / `"hold_on_last_frame"`.
- **Keyframe events**: every entry under `keyframes` is `{"name": "...", "locator": "boneName"}`. Channel is the parent key (`sound`, `particle`, `custom`, or anything else the listener routes); locator is optional.

### `.obj`

Standard Wavefront OBJ. Use `o` or `g` directives to create named groups for animated parts.

## Asset Workflow

### 1. Create your model in Blockbench

Design your machine with named bone groups for animated parts (e.g. `fan`, `piston`, `gear`).
Add animations in the **Animation** tab — rotation and position keyframes are supported.

### 2. Export OBJ

In Blockbench: **File → Export → Export OBJ Model**

Use `o` or `g` directives in the OBJ to define named groups that match your bone names.

### 3. Convert animations

```bash
# Linux / macOS
bash tools/convert.sh MyMachine.bbmodel

# Windows
tools\convert.bat MyMachine.bbmodel

# → MyMachine.anim.json
```

Requires JDK 8+ (same as RetroMCP — no extra dependencies).

The converter extracts from your `.bbmodel`:
- **Pivots** — bone origins (Blockbench pixels)
- **ChildMap** — parent→child bone hierarchy
- **Animations** — all clips with rotation/position keyframes

### 4. Integrate in Java

Place both `.obj` and `.anim.json` in your resources folder (e.g. `/models/`), then use the Quick Start code above.

## State Machine

The animation system includes a built-in state machine for managing clip transitions. It maps integer state IDs to clip names and handles playback automatically.

```java
// Define states (one per machine type — static final)
public static final Aero_AnimationDefinition ANIM_DEF = new Aero_AnimationDefinition()
    .state(0, "idle")       // STATE_OFF
    .state(1, "working")    // STATE_ON
    .state(2, "overdrive"); // STATE_FAST

// Create per-instance state
public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

// In updateEntity():
animState.tick();                              // 1. Advance time (ALWAYS first)
animState.setState(isRunning ? 1 : 0);         // 2. Evaluate state (AFTER tick)
```

**State switch rules:**
- **Same state** → no-op
- **Different state, different clip** → playback resets to 0 (new animation starts)
- **Different state, same clip** → playback continues (animation uninterrupted)
- **Default switch is instant** — use `setStateWithTransition(...)` or the router's `withTransition(...)` for crossfade

**Edge cases are safe:** unknown state IDs resolve to `null` clip (animation stops gracefully). Looping clips handle wrap-around without stutter.

See [DOC.md § State Machine](DOC.md#6-state-machine) for flowcharts, diagrams, and detailed behavior.

## Advanced Animation

Most consumers can stay on the basic state machine above. The features below
are opt-in — no existing clip needs them, but they are there when a mod
wants smoother transitions, layered motion, or sounds and particles fired
on cue.

### Easing curves

Each keyframe can declare its own `interp` (the curve over the segment that
ENDS at that keyframe). Any of the 33 curves work — `linear` (default),
`step`, `catmullrom`, plus the GeckoLib-style families: `sine`, `quad`,
`cubic`, `quart`, `quint`, `expo`, `circ`, `back`, `elastic`, `bounce`,
each with `easeIn*`, `easeOut*`, `easeInOut*` variants.

```json
"position": {
  "0":   { "value": [0, 0, 0], "interp": "linear" },
  "0.5": { "value": [0, 8, 0], "interp": "easeOutBack" },
  "1.0": { "value": [0, 0, 0], "interp": "easeInBounce" }
}
```

Unknown curve names throw at load time so typos surface immediately rather
than degrading silently. See [DOC.md § Easing curves](DOC.md#easing-curves).

### Smooth state transitions

Replace `setState(N)` with `setStateWithTransition(N, ticks)` to snapshot
the previous pose and blend it into the new clip's first N ticks. The
machinery handles bones present in only one of the two clips (those fade
in or out cleanly).

```java
animState.setStateWithTransition(STATE_WALK, 6);  // 6-tick blend
```

### Keyframe events with locators

Declare non-pose keyframes alongside the pose tracks; register a listener
on the playback to receive them. The `locator` is a bone name — the
listener uses `state.getAnimatedPivot(locator, partialTick, out)` to get
the bone's CURRENT position (rest pivot + the position channel offset),
so a particle anchored to "fan" emits from wherever the fan is RIGHT NOW.

```java
animState.setEventListener((channel, data, locator, time) -> {
    if (!"sound".equals(channel)) return;
    float[] p = new float[3];
    if (animState.getAnimatedPivot(locator, 0f, p)) {
        world.playSound(x + p[0], y + p[1], z + p[2], data, 0.6f, 1.0f);
    }
});
```

### Multi-layer playback (Stack)

`Aero_AnimationStack` runs several playbacks at once, combining their
per-bone outputs by REPLACE (default) or by ADD (additive). Useful for
a base walk loop plus a head-look or arm-wave overlay that only animates
its own bones.

```java
Aero_AnimationStack stack = Aero_AnimationStack.builder()
    .replace(walkPlayback)                  // base
    .additive(armWavePlayback, 0.8f)         // overlay
    .build();

stack.tick();   // ticks every layer
Aero_MeshRenderer.renderAnimated(MODEL, stack, x, y, z, brightness, partialTick);
```

Scale composes multiplicatively (`base × layer`), rotation/position add.

### Declarative specs

Use specs when the same model wiring appears in several places. The entity
or tile keeps the animation spec; the renderer keeps the client-side model
spec and passes it to the helper.

```java
public static final Aero_AnimationSpec ANIMATION =
    Aero_AnimationSpec.builder("/models/Robot.anim.json")
        .state(0, "idle")
        .state(1, "walk")
        .build();

private static final Aero_ModelSpec ROBOT =
    Aero_ModelSpec.mesh("/models/Robot.obj")
        .texture("/models/robot.png")
        .animations(ANIMATION)
        .offset(-0.5f, 0f, -0.5f)
        .cullingRadius(2f)
        .animatedDistance(48d)
        .build();

Aero_RenderLod lod = Aero_RenderDistance.lodRelative(ROBOT, x, y, z);
Aero_EntityModelRenderer.render(ROBOT, mob.animState, lod,
    entity, x, y, z, yaw, partialTick);
```

### Explicit render options

Use `Aero_RenderOptions` when a draw call needs styling such as a damage
flash or overheat tint. Options are passed per render call, so there is no
renderer-global state to reset afterward.

```java
Aero_RenderOptions hot = Aero_RenderOptions.tint(1f, 0.45f, 0.35f);
Aero_EntityModelRenderer.render(ROBOT, mob.animState, lod,
    x, y, z, yaw, brightness, partialTick, hot);
```

### Predicate state router

Replaces the giant `if/else` translating gameplay state into the int
that `setState(...)` consumes. Rules evaluate in declaration order; first
match wins. Optional `withTransition(ticks)` makes every state change a
smooth blend.

```java
Aero_AnimationStateRouter router = new Aero_AnimationStateRouter()
    .when(p -> entity.isDead(),       STATE_DEATH)
    .when(p -> entity.isAttacking(),  STATE_ATTACK)
    .when(p -> entity.isMoving(),     STATE_WALK)
    .otherwise(STATE_IDLE)
    .withTransition(6);

router.applyTo(animState);
```

## Best Practices

- Store loaders as `static final` fields — caching is automatic
- Call `tick()` **before** `setState()` every tick
- Persist animation state via `writeToNBT()` / `readFromNBT()`
- Use `STATE_OFF = 0` as default (NBT returns 0 when key is absent)
- Bind your texture **before** calling any render method

## Documentation

See [DOC.md](DOC.md) for the full API reference, architecture diagrams, and end-to-end examples.

## Development

```powershell
# Core unit tests (pure Java, no Minecraft runtime)
powershell -ExecutionPolicy Bypass -File modloader/tests/run.ps1

# Core microbenchmark for geometry caches and animation sampling
powershell -ExecutionPolicy Bypass -File modloader/tests/bench.ps1

# StationAPI library build (requires JDK 17+)
cd stationapi
.\gradlew.bat build

# StationAPI integration test mod build
cd test
.\gradlew.bat build

# In-game StationAPI smoke test, includes an animated entity probe
.\gradlew.bat runClient
```

## Author

**lucasrgt** — [aerocoding.dev](https://aerocoding.dev)

## License

MIT
