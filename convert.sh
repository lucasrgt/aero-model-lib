#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
# AeroModelLib — convert.sh
# Converts Blockbench .bbmodel files to AeroModelLib .anim.json format
#
# Usage:
#   bash convert.sh MyMachine.bbmodel
#   bash convert.sh MyMachine.bbmodel output.anim.json
#
# Requires: Node.js (v14+)
#
# by lucasrgt — aerocoding.dev
# ─────────────────────────────────────────────────────────────────────

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "AeroModelLib Converter"
    echo ""
    echo "Usage: bash convert.sh <input.bbmodel> [output.anim.json]"
    echo ""
    echo "Converts Blockbench .bbmodel files to .anim.json format"
    echo "for use with AeroModelLib's animation system."
    echo ""
    echo "The OBJ model must be exported manually from Blockbench:"
    echo "  File → Export → Export OBJ Model"
    echo ""
    echo "See README.md for the full workflow."
    exit 1
fi

INPUT="$1"
if [ ! -f "$INPUT" ]; then
    echo "Error: file not found: $INPUT"
    exit 1
fi

# Default output: replace .bbmodel with .anim.json
if [ $# -ge 2 ]; then
    OUTPUT="$2"
else
    OUTPUT="${INPUT%.bbmodel}.anim.json"
fi

if ! command -v node &> /dev/null; then
    echo "Error: Node.js is required. Install from https://nodejs.org"
    exit 1
fi

node -e '
var fs = require("fs");

var input = process.argv[1];
var output = process.argv[2];

var bb = JSON.parse(fs.readFileSync(input, "utf8"));

// ── 1. Build UUID → group map ──────────────────────────────────────
var groupByUuid = {};
var groups = bb.groups || [];
for (var i = 0; i < groups.length; i++) {
    groupByUuid[groups[i].uuid] = groups[i];
}

// ── 2. Extract pivots from outliner hierarchy ──────────────────────
// Outliner items reference groups by UUID. Each group has name + origin.
var pivots = {};

function collectPivots(items) {
    for (var i = 0; i < items.length; i++) {
        var item = items[i];
        if (typeof item === "string") continue; // element UUID, skip
        if (!item.uuid) continue;

        var g = groupByUuid[item.uuid];
        var name = g ? g.name : item.name;
        var origin = g ? g.origin : item.origin;

        if (name && origin) {
            pivots[name] = [origin[0], origin[1], origin[2]];
        }

        if (item.children) collectPivots(item.children);
    }
}
collectPivots(bb.outliner || []);

// ── 3. Extract childMap from outliner hierarchy ────────────────────
// For each parent group with children, map child→parent.
// Skip element UUIDs (strings that reference cubes, not groups).
var childMap = {};

function collectChildren(items, parentName) {
    for (var i = 0; i < items.length; i++) {
        var item = items[i];
        if (typeof item === "string") {
            // Element UUID — map element name to parent bone
            var el = findElement(item);
            if (el && parentName) {
                childMap[el.name] = parentName;
            }
            continue;
        }
        if (!item.uuid) continue;

        var g = groupByUuid[item.uuid];
        var name = g ? g.name : item.name;

        if (parentName && name) {
            childMap[name] = parentName;
        }

        if (item.children) collectChildren(item.children, name);
    }
}

// Build element UUID → element map
var elementByUuid = {};
var elements = bb.elements || [];
for (var i = 0; i < elements.length; i++) {
    elementByUuid[elements[i].uuid] = elements[i];
}

function findElement(uuid) {
    return elementByUuid[uuid] || null;
}

// Walk outliner — top-level groups have no parent
var outliner = bb.outliner || [];
for (var i = 0; i < outliner.length; i++) {
    var item = outliner[i];
    if (typeof item === "string") continue;
    if (!item.uuid) continue;

    var g = groupByUuid[item.uuid];
    var name = g ? g.name : item.name;

    // Top-level group: no parent entry in childMap
    // But walk its children with this as parent
    if (item.children) collectChildren(item.children, name);
}

// ── 4. Extract animations ──────────────────────────────────────────
// BBModel animators reference bones by UUID. We need to resolve to names.
// Animator can have .name directly, or we look up via group UUID.

var animations = {};
var bbAnims = bb.animations || [];

for (var a = 0; a < bbAnims.length; a++) {
    var bbAnim = bbAnims[a];

    // Clean animation name: strip "animation." prefix if present
    var animName = bbAnim.name || ("clip_" + a);
    if (animName.indexOf("animation.") === 0) {
        animName = animName.substring("animation.".length);
    }

    var clip = {
        loop: bbAnim.loop === "loop" || bbAnim.loop === true,
        length: bbAnim.length || 1,
        bones: {}
    };

    var animators = bbAnim.animators || {};
    var animatorKeys = Object.keys(animators);

    for (var k = 0; k < animatorKeys.length; k++) {
        var animatorUuid = animatorKeys[k];
        var animator = animators[animatorUuid];

        if (animator.type !== "bone") continue;

        // Resolve bone name: animator.name, or look up group by UUID
        var boneName = animator.name;
        if (!boneName) {
            var bg = groupByUuid[animatorUuid];
            if (bg) boneName = bg.name;
        }
        if (!boneName) continue;

        var keyframes = animator.keyframes || [];
        if (keyframes.length === 0) continue;

        var boneData = {};

        // Group keyframes by channel (rotation, position, scale)
        for (var kf = 0; kf < keyframes.length; kf++) {
            var frame = keyframes[kf];
            var channel = frame.channel; // "rotation", "position", "scale"

            // Only support rotation and position (AeroModelLib does not use scale)
            if (channel !== "rotation" && channel !== "position") continue;

            if (!boneData[channel]) boneData[channel] = {};

            var dp = frame.data_points[0];
            var time = String(frame.time);

            boneData[channel][time] = [
                parseFloat(dp.x) || 0,
                parseFloat(dp.y) || 0,
                parseFloat(dp.z) || 0
            ];
        }

        // Only add bone if it has keyframes
        if (Object.keys(boneData).length > 0) {
            clip.bones[boneName] = boneData;
        }
    }

    // Only add animation if it has bones with keyframes
    if (Object.keys(clip.bones).length > 0) {
        animations[animName] = clip;
    }
}

// ── 5. Build output ────────────────────────────────────────────────
var result = {
    format_version: "1.0",
    pivots: pivots,
    childMap: childMap,
    animations: animations
};

var json = JSON.stringify(result, null, 2);
fs.writeFileSync(output, json + "\n", "utf8");

// ── 6. Summary ─────────────────────────────────────────────────────
var pivotCount = Object.keys(pivots).length;
var childCount = Object.keys(childMap).length;
var animCount = Object.keys(animations).length;
var boneCount = 0;
var animNames = Object.keys(animations);
for (var i = 0; i < animNames.length; i++) {
    boneCount += Object.keys(animations[animNames[i]].bones).length;
}

console.log("Converted: " + input + " → " + output);
console.log("");
console.log("  Pivots:     " + pivotCount + " bones");
console.log("  ChildMap:   " + childCount + " entries");
console.log("  Animations: " + animCount + " clips, " + boneCount + " animated bones");
console.log("");

if (animCount === 0) {
    console.log("  ⚠ No animations found. If your model has animations,");
    console.log("    make sure they are created in Blockbench before exporting.");
    console.log("");
}

console.log("Next steps:");
console.log("  1. Export OBJ from Blockbench: File → Export → Export OBJ Model");
console.log("  2. Place both files in your mod resources (e.g. /models/)");
console.log("  3. See README.md for the Java integration code");
' "$INPUT" "$OUTPUT"
