# AeroModelLib

3D rendering and animation library for Minecraft Beta 1.7.3 — like GeckoLib, but for RetroMCP's OpenGL 1.1 pipeline.

Demo Animated Machine on YouTube:

[![Demo](https://img.youtube.com/vi/ewJ0XgnOSHE/maxresdefault.jpg)](https://www.youtube.com/watch?v=ewJ0XgnOSHE)

> **Compatibility:** Java 8 | Minecraft Beta 1.7.3 | RetroMCP | ModLoader / Forge 1.0.6 | LWJGL (OpenGL 1.1+)

## Features

- **Static models** — Load Blockbench JSON exports, render blocks and items with flat/smooth lighting
- **OBJ mesh models** — Load `.obj` files with named groups for animated parts
- **Keyframe animation** — `.anim.json` format with rotation + position keyframes, loop/clamp, state machine
- **Partial-tick interpolation** — Smooth 60fps animation from 20-tick updates
- **Brightness optimization** — Triangles pre-classified into 4 groups, only 4 color calls per frame
- **Built-in caching** — All loaders cache by resource path automatically

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

// ── TileEntitySpecialRenderer ──

bindTextureByName("/block/my_texture_hq.png");
Aero_MeshRenderer.renderAnimated(MODEL, BUNDLE, ANIM_DEF, tile.animState,
    d, d1, d2, brightness, partialTick);
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
| `Aero_AnimationBundle` | All clips + pivots + childMap from a `.anim.json` |
| `Aero_AnimationClip` | Single animation clip with keyframes per bone |
| `Aero_AnimationDefinition` | Maps state IDs to clip names (one per machine type) |
| `Aero_AnimationState` | Per-instance playback state with tick/setState/NBT |
| `Aero_AnimationLoader` | Loads + caches `.anim.json` files from classpath |
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
      "loop": true,
      "length": 2.0,
      "bones": {
        "fan": {
          "rotation": { "0.0": [0,0,0], "2.0": [0,0,0] }
        }
      }
    },
    "working": {
      "loop": true,
      "length": 1.0,
      "bones": {
        "fan": {
          "rotation": { "0.0": [0,0,0], "1.0": [0,360,0] },
          "position": { "0.0": [0,0,0] }
        }
      }
    }
  }
}
```

- **Pivots**: Blockbench pixels (auto-divided by 16 in the loader)
- **Rotation**: Euler degrees [X, Y, Z], applied Z→Y→X (Bedrock compatible)
- **Position**: Blockbench pixels (divided by 16 in the renderer)

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
bash scripts/convert.sh MyMachine.bbmodel

# Windows
scripts\convert.bat MyMachine.bbmodel

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

**Transition rules:**
- **Same state** → no-op
- **Different state, different clip** → playback resets to 0 (new animation starts)
- **Different state, same clip** → playback continues (animation uninterrupted)
- **No blending** — transitions are instantaneous, no crossfade

**Edge cases are safe:** unknown state IDs resolve to `null` clip (animation stops gracefully). Looping clips handle wrap-around without stutter.

See [DOC.md § State Machine](DOC.md#6-state-machine) for flowcharts, diagrams, and detailed behavior.

## Best Practices

- Store loaders as `static final` fields — caching is automatic
- Call `tick()` **before** `setState()` every tick
- Persist animation state via `writeToNBT()` / `readFromNBT()`
- Use `STATE_OFF = 0` as default (NBT returns 0 when key is absent)
- Bind your texture **before** calling any render method

## Documentation

See [DOC.md](DOC.md) for the full API reference, architecture diagrams, and end-to-end examples.

## Author

**lucasrgt** — [aerocoding.dev](https://aerocoding.dev)

## License

MIT
