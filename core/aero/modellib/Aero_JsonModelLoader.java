package aero.modellib;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AeroModel JSON Loader by lucasrgt - aerocoding.dev
 * Loads Blockbench models directly at runtime — no conversion pipeline.
 *
 * Usage:
 *   Aero_JsonModel model = Aero_JsonModelLoader.load("/models/my_machine.json");
 *
 * Place JSONs in src/retronism/assets/models/ — the transpiler injects them
 * into the jar automatically. Export from Blockbench: File > Export > Export as JSON.
 */
public class Aero_JsonModelLoader {

    private static final int MAX_CACHE_ENTRIES =
        Aero_PerfConfig.intProperty("aero.modellib.cache.maxEntries",
            512, -1, -1, Integer.MAX_VALUE);

    private static final Map cache = new LinkedHashMap(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return MAX_CACHE_ENTRIES > 0 && size() > MAX_CACHE_ENTRIES;
        }
    };

    /** Loads and caches a Blockbench model from the classpath. */
    public static synchronized Aero_JsonModel load(String resourcePath) {
        return load(resourcePath, resourcePath);
    }

    /** Loads and caches a Blockbench model from the classpath with an explicit name. */
    public static synchronized Aero_JsonModel load(String resourcePath, String name) {
        if (cache.containsKey(resourcePath)) {
            return (Aero_JsonModel) cache.get(resourcePath);
        }
        try {
            InputStream is = Aero_JsonModelLoader.class.getResourceAsStream(resourcePath);
            if (is == null) {
                throw new RuntimeException("AeroModelLoader: resource not found: " + resourcePath);
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            try {
                while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
            } finally {
                is.close();
            }
            Aero_JsonModel model = fromJson(parseJson(buf.toString("UTF-8"), new int[]{0}), name);
            cache.put(resourcePath, model);
            return model;
        } catch (Exception e) {
            throw new RuntimeException("AeroModelLoader: failed to load " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /** Drops all cached JSON models. Useful for tests and hot-reload tooling. */
    public static synchronized void clearCache() {
        cache.clear();
    }

    static synchronized int cacheSize() {
        return cache.size();
    }

    // -----------------------------------------------------------------------
    // JSON → Aero_JsonModel conversion
    // -----------------------------------------------------------------------

    private static Aero_JsonModel fromJson(Object root, String name) {
        Map obj = (Map) root;

        // textureSize: resolution.width → fallback 128
        float textureSize = 128.0f;
        if (obj.containsKey("resolution")) {
            Map res = (Map) obj.get("resolution");
            if (res.containsKey("width")) textureSize = toFloat(res.get("width"));
        }

        List elements = (List) obj.get("elements");
        if (elements == null || elements.isEmpty()) {
            throw new RuntimeException("AeroModelLoader: no elements in " + name);
        }

        // Filter only elements with from+to (skip meshes and other bbmodel types)
        List cubes = new ArrayList();
        for (int i = 0; i < elements.size(); i++) {
            Object e = elements.get(i);
            if (e instanceof Map) {
                Map m = (Map) e;
                if (m.containsKey("from") && m.containsKey("to")) cubes.add(m);
            }
        }

        float[][] parts = new float[cubes.size()][30];
        String[] FACE_ORDER = {"down", "up", "north", "south", "west", "east"};

        for (int i = 0; i < cubes.size(); i++) {
            Map el = (Map) cubes.get(i);
            List from = (List) el.get("from");
            List to   = (List) el.get("to");
            float[] p = parts[i];

            float inf = el.containsKey("inflate") ? toFloat(el.get("inflate")) : 0.0f;
            p[0] = toFloat(from.get(0)) - inf;  p[1] = toFloat(from.get(1)) - inf;  p[2] = toFloat(from.get(2)) - inf;
            p[3] = toFloat(to.get(0))   + inf;  p[4] = toFloat(to.get(1))   + inf;  p[5] = toFloat(to.get(2))   + inf;

            Map faces = el.containsKey("faces") ? (Map) el.get("faces") : new HashMap();
            for (int f = 0; f < 6; f++) {
                int base = 6 + f * 4;
                Object faceObj = faces.get(FACE_ORDER[f]);
                if (faceObj instanceof Map) {
                    List uv = (List) ((Map) faceObj).get("uv");
                    if (uv != null && uv.size() >= 4) {
                        p[base]   = toFloat(uv.get(0));
                        p[base+1] = toFloat(uv.get(1));
                        p[base+2] = toFloat(uv.get(2));
                        p[base+3] = toFloat(uv.get(3));
                        continue;
                    }
                }
                // Missing face or no UV — sentinel -1 (Aero_JsonModelRenderer skips)
                p[base] = p[base+1] = p[base+2] = p[base+3] = -1.0f;
            }
        }

        return new Aero_JsonModel(name, parts, textureSize, 16.0f);
    }

    private static float toFloat(Object o) {
        if (o instanceof Float)   return (Float) o;
        if (o instanceof Double)  return ((Double) o).floatValue();
        if (o instanceof Integer) return (float)(int)(Integer) o;
        if (o instanceof Long)    return (float)(long)(Long) o;
        return 0.0f;
    }

    // -----------------------------------------------------------------------
    // Minimal recursive-descent JSON parser
    // pos[0] = current index in the string
    // -----------------------------------------------------------------------

    private static Object parseJson(String s, int[] pos) {
        skipWs(s, pos);
        if (pos[0] >= s.length()) throw new RuntimeException("Unexpected end of JSON");
        char c = s.charAt(pos[0]);
        if (c == '{') return parseObject(s, pos);
        if (c == '[') return parseArray(s, pos);
        if (c == '"') return parseString(s, pos);
        if (c == 't') { pos[0] += 4; return Boolean.TRUE; }
        if (c == 'f') { pos[0] += 5; return Boolean.FALSE; }
        if (c == 'n') { pos[0] += 4; return null; }
        return parseNumber(s, pos);
    }

    private static Map parseObject(String s, int[] pos) {
        pos[0]++; // '{'
        Map map = new HashMap();
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == '}') { pos[0]++; return map; }
        while (pos[0] < s.length()) {
            skipWs(s, pos);
            String key = parseString(s, pos);
            skipWs(s, pos);
            pos[0]++; // ':'
            skipWs(s, pos);
            map.put(key, parseJson(s, pos));
            skipWs(s, pos);
            if (pos[0] >= s.length()) break;
            char next = s.charAt(pos[0]);
            if (next == '}') { pos[0]++; break; }
            if (next == ',') pos[0]++;
        }
        return map;
    }

    private static List parseArray(String s, int[] pos) {
        pos[0]++; // '['
        List list = new ArrayList();
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == ']') { pos[0]++; return list; }
        while (pos[0] < s.length()) {
            skipWs(s, pos);
            list.add(parseJson(s, pos));
            skipWs(s, pos);
            if (pos[0] >= s.length()) break;
            char next = s.charAt(pos[0]);
            if (next == ']') { pos[0]++; break; }
            if (next == ',') pos[0]++;
        }
        return list;
    }

    private static String parseString(String s, int[] pos) {
        pos[0]++; // '"'
        StringBuilder sb = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]++);
            if (c == '"') break;
            if (c == '\\' && pos[0] < s.length()) {
                char esc = s.charAt(pos[0]++);
                if      (esc == '"')  sb.append('"');
                else if (esc == '\\') sb.append('\\');
                else if (esc == '/')  sb.append('/');
                else if (esc == 'n')  sb.append('\n');
                else if (esc == 'r')  sb.append('\r');
                else if (esc == 't')  sb.append('\t');
                else                  sb.append(esc);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Float parseNumber(String s, int[] pos) {
        int start = pos[0];
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == ',' || c == ']' || c == '}' || c <= ' ') break;
            pos[0]++;
        }
        return Float.parseFloat(s.substring(start, pos[0]));
    }

    private static void skipWs(String s, int[] pos) {
        while (pos[0] < s.length() && s.charAt(pos[0]) <= ' ') pos[0]++;
    }
}
