package aero.modellib;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads .anim.json files and returns a cached Aero_AnimationBundle.
 *
 * Loads .anim.json files at format_version "1.0" with strict validation.
 * Pose keyframes use { "value": [x, y, z], "interp": "linear" }, loop is
 * a string, and non-pose events use { "name": "...", "locator": "..." }
 * objects.
 */
public class Aero_AnimationLoader {

    /** Schema version this loader understands. */
    public static final String SUPPORTED_FORMAT_VERSION = "1.0";

    private static final int MAX_CACHE_ENTRIES =
        Integer.getInteger("aero.modellib.cache.maxEntries", 512).intValue();

    private static final Map cache = new LinkedHashMap(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return MAX_CACHE_ENTRIES > 0 && size() > MAX_CACHE_ENTRIES;
        }
    };

    /** Loads and caches a .anim.json from the classpath. */
    public static synchronized Aero_AnimationBundle load(String resourcePath) {
        if (cache.containsKey(resourcePath)) {
            return (Aero_AnimationBundle) cache.get(resourcePath);
        }
        try {
            InputStream is = Aero_AnimationLoader.class.getResourceAsStream(resourcePath);
            if (is == null) {
                throw new RuntimeException("Aero_AnimationLoader: resource not found: " + resourcePath);
            }
            StringBuilder sb = new StringBuilder();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            } finally {
                is.close();
            }

            Map root = (Map) new JsonParser(sb.toString()).parseValue();
            Aero_AnimationBundle bundle = buildBundle(root);
            cache.put(resourcePath, bundle);
            return bundle;
        } catch (Exception e) {
            throw new RuntimeException("Aero_AnimationLoader: failed to load " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Drops every cached bundle. Intended for tests that load multiple
     * fixtures from the same resource path or reload a hot-swapped
     * {@code .anim.json}; production code should not need this.
     */
    public static synchronized void clearCache() {
        cache.clear();
    }

    static synchronized int cacheSize() {
        return cache.size();
    }

    static Aero_AnimationBundle loadFromString(String json) {
        try {
            Map root = (Map) new JsonParser(json).parseValue();
            return buildBundle(root);
        } catch (Exception e) {
            throw new RuntimeException("Aero_AnimationLoader: failed to parse animation JSON: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Bundle builder
    // -----------------------------------------------------------------------

    private static Aero_AnimationBundle buildBundle(Map root) {
        // --- format_version ---
        // The strict schema declares format_version "1.0". Reject anything else loudly so
        // future schema bumps surface as a clear loader error rather than
        // a silent half-parsed bundle.
        if (!root.containsKey("format_version")) {
            throw new RuntimeException("missing required \"format_version\" — "
                + "expected \"" + SUPPORTED_FORMAT_VERSION + "\"");
        }
        Object versionObj = root.get("format_version");
        if (!(versionObj instanceof String)) {
            throw new RuntimeException("format_version must be a string");
        }
        if (!SUPPORTED_FORMAT_VERSION.equals(versionObj)) {
            throw new RuntimeException("unsupported format_version \"" + versionObj
                + "\" — this loader supports \"" + SUPPORTED_FORMAT_VERSION + "\"");
        }

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
        Aero_AnimationLoop loop = Aero_AnimationLoop.PLAY_ONCE;
        if (clipData.containsKey("loop")) {
            Object loopVal = clipData.get("loop");
            if (!(loopVal instanceof String)) {
                throw new RuntimeException("loop must be a string in clip " + clipName);
            }
            loop = Aero_AnimationLoop.fromName((String) loopVal);
        }
        float   length = clipData.containsKey("length") ? toFloat(clipData.get("length")) : 1f;

        Map bonesIn = clipData.containsKey("bones") ? (Map) clipData.get("bones") : new HashMap();
        Aero_AnimationClip.Builder builder = Aero_AnimationClip.builder(clipName)
            .loop(loop)
            .length(length);

        Iterator it = bonesIn.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String boneName = (String) entry.getKey();
            Aero_AnimationClip.BoneBuilder bone = builder.bone(boneName);
            Map channels  = (Map) entry.getValue();

            if (channels.containsKey("rotation")) {
                ParsedChannel ch = parseChannel((Map) channels.get("rotation"));
                bone.rotation(ch.times, ch.values, ch.easings);
            }
            if (channels.containsKey("position")) {
                ParsedChannel ch = parseChannel((Map) channels.get("position"));
                bone.position(ch.times, ch.values, ch.easings);
            }
            if (channels.containsKey("scale")) {
                ParsedChannel ch = parseChannel((Map) channels.get("scale"));
                bone.scale(ch.times, ch.values, ch.easings);
            }
        }

        if (clipData.containsKey("keyframes")) {
            parseEvents((Map) clipData.get("keyframes"), builder);
        }

        return builder.build();
    }

    private static void parseEvents(Map kfRoot, Aero_AnimationClip.Builder builder) {
        // Flatten {channel: {time: payload}} into a list, then sort by time.
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
                if (!(payload instanceof Map)) {
                    throw new RuntimeException("event keyframe must be an object at " + channel + "@" + t);
                }
                Map kf = (Map) payload;
                Object n = kf.get("name");
                if (!(n instanceof String)) {
                    throw new RuntimeException("event name must be a string at " + channel + "@" + t);
                }
                Object loc = kf.get("locator");
                String locator = loc != null ? String.valueOf(loc) : null;
                String data = (String) n;
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
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = (Object[]) rows.get(i);
            builder.event(((Float) row[0]).floatValue(),
                (String) row[1], (String) row[2], (String) row[3]);
        }
    }

    /** Parsed channel data with times, values and interp modes */
    private static class ParsedChannel {
        float[] times;
        float[][] values;
        Aero_Easing[] easings;
    }

    /**
     * Parses a strict channel map:
     *   "0.0": { "value": [x, y, z], "interp": "catmullrom" }
     */
    private static ParsedChannel parseChannel(Map kfMap) {
        List entries = new ArrayList(); // Object[]{Float time, float[] value, Aero_Easing easing}
        Iterator it = kfMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            float t = Float.parseFloat((String) entry.getKey());
            Object val = entry.getValue();

            if (!(val instanceof Map)) {
                throw new RuntimeException("pose keyframe must be an object at t=" + t);
            }
            Map kfObj = (Map) val;
            Object valueObj = kfObj.get("value");
            if (!(valueObj instanceof List)) {
                throw new RuntimeException("pose keyframe value must be an array at t=" + t);
            }
            Object interpObj = kfObj.get("interp");
            if (!(interpObj instanceof String)) {
                throw new RuntimeException("pose keyframe interp must be a string at t=" + t);
            }
            List v = (List) valueObj;
            float[] xyz = new float[]{toFloat(v.get(0)), toFloat(v.get(1)), toFloat(v.get(2))};
            Aero_Easing easing = Aero_Easing.fromName((String) interpObj);

            entries.add(new Object[]{Float.valueOf(t), xyz, easing});
        }

        Collections.sort(entries, new Comparator() {
            public int compare(Object a, Object b) {
                return ((Float) ((Object[]) a)[0]).compareTo((Float) ((Object[]) b)[0]);
            }
        });

        int count = entries.size();
        ParsedChannel ch = new ParsedChannel();
        ch.times   = new float[count];
        ch.values  = new float[count][];
        ch.easings = new Aero_Easing[count];
        for (int i = 0; i < count; i++) {
            Object[] row = (Object[]) entries.get(i);
            ch.times[i] = ((Float) row[0]).floatValue();
            ch.values[i] = (float[]) row[1];
            ch.easings[i] = (Aero_Easing) row[2];
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
