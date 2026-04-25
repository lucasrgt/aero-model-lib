package aero.modellib;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Loads .anim.json files and returns a cached Aero_AnimationBundle.
 *
 * Supports two keyframe formats (backward compatible):
 *   Legacy:  "0.0": [rx, ry, rz]
 *   New:     "0.0": { "value": [rx, ry, rz], "interp": "linear" }
 *
 * Interpolation modes: "linear" (default), "catmullrom" (smooth), "step"
 *
 * Channels: rotation, position, scale
 */
public class Aero_AnimationLoader {

    private static final Map cache = new HashMap();

    /** Loads and caches a .anim.json from the classpath. */
    public static Aero_AnimationBundle load(String resourcePath) {
        if (cache.containsKey(resourcePath)) {
            return (Aero_AnimationBundle) cache.get(resourcePath);
        }
        try {
            InputStream is = Aero_AnimationLoader.class.getResourceAsStream(resourcePath);
            if (is == null) {
                throw new RuntimeException("Aero_AnimationLoader: resource not found: " + resourcePath);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            is.close();

            Map root = (Map) new JsonParser(sb.toString()).parseValue();
            Aero_AnimationBundle bundle = buildBundle(root);
            cache.put(resourcePath, bundle);
            return bundle;
        } catch (Exception e) {
            throw new RuntimeException("Aero_AnimationLoader: failed to load " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Bundle builder
    // -----------------------------------------------------------------------

    private static Aero_AnimationBundle buildBundle(Map root) {
        // --- Pivots ---
        Map pivotsOut = new HashMap();
        if (root.containsKey("pivots")) {
            Map pivotsIn = (Map) root.get("pivots");
            Iterator it = pivotsIn.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                String boneName = (String) entry.getKey();
                List arr = (List) entry.getValue();
                pivotsOut.put(boneName, new float[]{
                    toFloat(arr.get(0)) / 16f,
                    toFloat(arr.get(1)) / 16f,
                    toFloat(arr.get(2)) / 16f
                });
            }
        }

        // --- Animations ---
        Map clipsOut = new HashMap();
        if (root.containsKey("animations")) {
            Map animsIn = (Map) root.get("animations");
            Iterator it = animsIn.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                String clipName = (String) entry.getKey();
                Map clipData = (Map) entry.getValue();
                clipsOut.put(clipName, buildClip(clipName, clipData));
            }
        }

        // --- ChildMap ---
        Map childMapOut = new HashMap();
        if (root.containsKey("childMap")) {
            Map cmIn = (Map) root.get("childMap");
            Iterator cmIt = cmIn.entrySet().iterator();
            while (cmIt.hasNext()) {
                Map.Entry cmEntry = (Map.Entry) cmIt.next();
                childMapOut.put((String) cmEntry.getKey(), (String) cmEntry.getValue());
            }
        }

        return new Aero_AnimationBundle(clipsOut, pivotsOut, childMapOut);
    }

    private static Aero_AnimationClip buildClip(String clipName, Map clipData) {
        // The "loop" field accepts either a JSON boolean (legacy) or a
        // string naming the GeckoLib-style loop type. Unknown strings
        // degrade to PLAY_ONCE.
        int loopType = Aero_AnimationClip.LOOP_TYPE_PLAY_ONCE;
        if (clipData.containsKey("loop")) {
            Object loopVal = clipData.get("loop");
            if (loopVal instanceof Boolean) {
                loopType = ((Boolean) loopVal).booleanValue()
                    ? Aero_AnimationClip.LOOP_TYPE_LOOP
                    : Aero_AnimationClip.LOOP_TYPE_PLAY_ONCE;
            } else if (loopVal instanceof String) {
                String s = (String) loopVal;
                if      ("loop".equals(s))               loopType = Aero_AnimationClip.LOOP_TYPE_LOOP;
                else if ("hold_on_last_frame".equals(s)) loopType = Aero_AnimationClip.LOOP_TYPE_HOLD;
                else                                      loopType = Aero_AnimationClip.LOOP_TYPE_PLAY_ONCE;
            }
        }
        float   length = clipData.containsKey("length") ? toFloat(clipData.get("length")) : 1f;

        Map bonesIn = clipData.containsKey("bones") ? (Map) clipData.get("bones") : new HashMap();

        int n = bonesIn.size();
        String[]    boneNames  = new String[n];
        float[][]   rotTimes   = new float[n][];
        float[][][] rotValues  = new float[n][][];
        int[][]     rotInterps = new int[n][];
        float[][]   posTimes   = new float[n][];
        float[][][] posValues  = new float[n][][];
        int[][]     posInterps = new int[n][];
        float[][]   sclTimes   = new float[n][];
        float[][][] sclValues  = new float[n][][];
        int[][]     sclInterps = new int[n][];

        int bi = 0;
        Iterator it = bonesIn.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            boneNames[bi] = (String) entry.getKey();
            Map channels  = (Map) entry.getValue();

            if (channels.containsKey("rotation")) {
                ParsedChannel ch = parseChannel((Map) channels.get("rotation"));
                rotTimes[bi]   = ch.times;
                rotValues[bi]  = ch.values;
                rotInterps[bi] = ch.interps;
            }
            if (channels.containsKey("position")) {
                ParsedChannel ch = parseChannel((Map) channels.get("position"));
                posTimes[bi]   = ch.times;
                posValues[bi]  = ch.values;
                posInterps[bi] = ch.interps;
            }
            if (channels.containsKey("scale")) {
                ParsedChannel ch = parseChannel((Map) channels.get("scale"));
                sclTimes[bi]   = ch.times;
                sclValues[bi]  = ch.values;
                sclInterps[bi] = ch.interps;
            }
            bi++;
        }

        // Optional non-pose keyframes block. Schema:
        //   "keyframes": {
        //       "sound":    { "0.5": "mob.zombie.hurt" },
        //       "particle": { "1.0": "smoke" },
        //       "custom":   { "0.5": "ATTACK_HITBOX" }
        //   }
        // Each channel maps timestamp → payload string. Times sort across
        // ALL channels into a single array so playback fires them in real
        // chronological order with one linear walk per tick.
        float[]  evTimes    = null;
        String[] evChannels = null;
        String[] evData     = null;
        String[] evLocators = null;
        if (clipData.containsKey("keyframes")) {
            ParsedEvents pe = parseEvents((Map) clipData.get("keyframes"));
            evTimes    = pe.times;
            evChannels = pe.channels;
            evData     = pe.data;
            evLocators = pe.locators;
        }

        return new Aero_AnimationClip(clipName, loopType, length,
            boneNames,
            rotTimes, rotValues, rotInterps,
            posTimes, posValues, posInterps,
            sclTimes, sclValues, sclInterps,
            evTimes, evChannels, evData, evLocators);
    }

    private static class ParsedEvents {
        float[]  times;
        String[] channels;
        String[] data;
        String[] locators;
    }

    private static ParsedEvents parseEvents(Map kfRoot) {
        // Flatten {channel: {time: payload}} into a list, then sort by time.
        // Each keyframe value can be either:
        //   - a bare string (legacy):     "0.5": "random.click"
        //   - a structured object (new):  "0.5": { "name": "random.click", "locator": "muzzle" }
        // The locator field is optional even on the structured form.
        List rows = new ArrayList(); // Object[]{time, channel, data, locator}
        Iterator it = kfRoot.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry chEntry = (Map.Entry) it.next();
            String channel = (String) chEntry.getKey();
            Object value   = chEntry.getValue();
            if (!(value instanceof Map)) continue;
            Map kfs = (Map) value;
            Iterator kfIt = kfs.entrySet().iterator();
            while (kfIt.hasNext()) {
                Map.Entry e = (Map.Entry) kfIt.next();
                float t = Float.parseFloat((String) e.getKey());
                Object payload = e.getValue();
                String data;
                String locator = null;
                if (payload instanceof Map) {
                    Map kf = (Map) payload;
                    Object n = kf.get("name");
                    data = n != null ? String.valueOf(n) : "";
                    Object loc = kf.get("locator");
                    if (loc != null) locator = String.valueOf(loc);
                } else {
                    data = String.valueOf(payload);
                }
                rows.add(new Object[]{ Float.valueOf(t), channel, data, locator });
            }
        }
        Collections.sort(rows, new Comparator() {
            public int compare(Object a, Object b) {
                Float ta = (Float) ((Object[]) a)[0];
                Float tb = (Float) ((Object[]) b)[0];
                return ta.compareTo(tb);
            }
        });
        ParsedEvents out = new ParsedEvents();
        int n = rows.size();
        out.times    = new float[n];
        out.channels = new String[n];
        out.data     = new String[n];
        out.locators = new String[n];
        for (int i = 0; i < n; i++) {
            Object[] row = (Object[]) rows.get(i);
            out.times[i]    = ((Float)  row[0]).floatValue();
            out.channels[i] = (String)  row[1];
            out.data[i]     = (String)  row[2];
            out.locators[i] = (String)  row[3];
        }
        return out;
    }

    /** Parsed channel data with times, values and interp modes */
    private static class ParsedChannel {
        float[] times;
        float[][] values;
        int[] interps;
    }

    /**
     * Parses a channel map. Supports two formats:
     *   Legacy:  "0.0": [x, y, z]
     *   New:     "0.0": { "value": [x, y, z], "interp": "catmullrom" }
     */
    private static ParsedChannel parseChannel(Map kfMap) {
        List entries = new ArrayList(); // float[] {time, x, y, z, interpMode}
        Iterator it = kfMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            float t = Float.parseFloat((String) entry.getKey());
            Object val = entry.getValue();

            float x, y, z;
            int interp = Aero_AnimationClip.INTERP_LINEAR;

            if (val instanceof Map) {
                // New format: { "value": [x,y,z], "interp": "catmullrom" }
                Map kfObj = (Map) val;
                List v = (List) kfObj.get("value");
                x = toFloat(v.get(0));
                y = toFloat(v.get(1));
                z = toFloat(v.get(2));
                if (kfObj.containsKey("interp")) {
                    // Aero_Easing.byName accepts every registered curve
                    // name (linear/catmullrom/step plus the easeIn*/easeOut*
                    // /easeInOut* family), and falls back to LINEAR for
                    // unknown strings.
                    interp = Aero_Easing.byName((String) kfObj.get("interp"));
                }
            } else {
                // Legacy format: [x, y, z]
                List v = (List) val;
                x = toFloat(v.get(0));
                y = toFloat(v.get(1));
                z = toFloat(v.get(2));
            }

            entries.add(new float[]{t, x, y, z, (float) interp});
        }

        Collections.sort(entries, new Comparator() {
            public int compare(Object a, Object b) {
                return Float.compare(((float[]) a)[0], ((float[]) b)[0]);
            }
        });

        int count = entries.size();
        ParsedChannel ch = new ParsedChannel();
        ch.times   = new float[count];
        ch.values  = new float[count][];
        ch.interps = new int[count];
        for (int i = 0; i < count; i++) {
            float[] row = (float[]) entries.get(i);
            ch.times[i]   = row[0];
            ch.values[i]  = new float[]{row[1], row[2], row[3]};
            ch.interps[i] = (int) row[4];
        }
        return ch;
    }

    private static float toFloat(Object o) {
        if (o instanceof Float)   return ((Float) o).floatValue();
        if (o instanceof Double)  return ((Double) o).floatValue();
        if (o instanceof Integer) return ((Integer) o).floatValue();
        if (o instanceof Long)    return ((Long) o).floatValue();
        return Float.parseFloat(o.toString());
    }

    // -----------------------------------------------------------------------
    // Minimal JSON Parser (recursive descent)
    // -----------------------------------------------------------------------

    private static class JsonParser {
        private final String s;
        private int pos;

        JsonParser(String src) { this.s = src; this.pos = 0; }

        Object parseValue() {
            skipWs();
            if (pos >= s.length()) throw new RuntimeException("Unexpected end of JSON at pos " + pos);
            char c = s.charAt(pos);
            if (c == '{')  return parseObject();
            if (c == '[')  return parseArray();
            if (c == '"')  return parseString();
            if (c == 't')  { pos += 4; return Boolean.TRUE; }
            if (c == 'f')  { pos += 5; return Boolean.FALSE; }
            if (c == 'n')  { pos += 4; return null; }
            return parseNumber();
        }

        private Map parseObject() {
            Map map = new HashMap();
            pos++; // '{'
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') { pos++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (pos >= s.length()) break;
                char ch = s.charAt(pos);
                if (ch == '}') { pos++; break; }
                if (ch == ',') { pos++; continue; }
                throw new RuntimeException("Expected ',' or '}' at pos " + pos);
            }
            return map;
        }

        private List parseArray() {
            List list = new ArrayList();
            pos++; // '['
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') { pos++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (pos >= s.length()) break;
                char ch = s.charAt(pos);
                if (ch == ']') { pos++; break; }
                if (ch == ',') { pos++; continue; }
                throw new RuntimeException("Expected ',' or ']' at pos " + pos);
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\' && pos < s.length()) {
                    char esc = s.charAt(pos++);
                    if      (esc == '"')  sb.append('"');
                    else if (esc == '\\') sb.append('\\');
                    else if (esc == '/')  sb.append('/');
                    else if (esc == 'n')  sb.append('\n');
                    else if (esc == 'r')  sb.append('\r');
                    else if (esc == 't')  sb.append('\t');
                    else sb.append(esc);
                } else {
                    sb.append(c);
                }
            }
            throw new RuntimeException("Unterminated string");
        }

        private Float parseNumber() {
            int start = pos;
            if (pos < s.length() && s.charAt(pos) == '-') pos++;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isDigit(c) || c == '.') { pos++; continue; }
                if (c == 'e' || c == 'E') {
                    pos++;
                    if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                    continue;
                }
                break;
            }
            return Float.valueOf(Float.parseFloat(s.substring(start, pos)));
        }

        private void skipWs() {
            while (pos < s.length() && s.charAt(pos) <= ' ') pos++;
        }

        private void expect(char c) {
            if (pos >= s.length() || s.charAt(pos) != c)
                throw new RuntimeException("Expected '" + c + "' at pos " + pos
                    + ", got '" + (pos < s.length() ? s.charAt(pos) : '?') + "'");
            pos++;
        }
    }
}
