import java.io.*;
import java.util.*;

/**
 * AeroModelLib — Blockbench .bbmodel to .anim.json converter.
 *
 * Extracts pivots, childMap, and animation keyframes from a Blockbench
 * project file and writes an .anim.json compatible with Aero_AnimationLoader.
 *
 * Usage:
 *   javac Aero_Convert.java && java Aero_Convert MyMachine.bbmodel
 *   javac Aero_Convert.java && java Aero_Convert MyMachine.bbmodel output.anim.json
 *
 * Requires: JDK 8+
 *
 * by lucasrgt — aerocoding.dev
 */
public class Aero_Convert {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("AeroModelLib Converter");
            System.out.println();
            System.out.println("Usage: java Aero_Convert <input.bbmodel> [output.anim.json]");
            System.out.println();
            System.out.println("Converts Blockbench .bbmodel files to .anim.json format");
            System.out.println("for use with AeroModelLib's animation system.");
            System.out.println();
            System.out.println("The OBJ model must be exported manually from Blockbench:");
            System.out.println("  File > Export > Export OBJ Model");
            System.out.println();
            System.out.println("See README.md for the full workflow.");
            return;
        }

        String input = args[0];
        File inputFile = new File(input);
        if (!inputFile.exists()) {
            System.err.println("Error: file not found: " + input);
            System.exit(1);
        }

        String output;
        if (args.length >= 2) {
            output = args[1];
        } else {
            output = input.replaceAll("\\.bbmodel$", ".anim.json");
            if (output.equals(input)) output = input + ".anim.json";
        }

        try {
            String src = readFile(inputFile);
            Map root = (Map) new JsonParser(src).parseValue();

            // 1. Build UUID → group map
            Map groupByUuid = new HashMap(); // uuid → Map (group)
            List groups = root.containsKey("groups") ? (List) root.get("groups") : new ArrayList();
            for (int i = 0; i < groups.size(); i++) {
                Map g = (Map) groups.get(i);
                String uuid = (String) g.get("uuid");
                if (uuid != null) groupByUuid.put(uuid, g);
            }

            // 2. Build UUID → element map
            Map elementByUuid = new HashMap();
            List elements = root.containsKey("elements") ? (List) root.get("elements") : new ArrayList();
            for (int i = 0; i < elements.size(); i++) {
                Map el = (Map) elements.get(i);
                String uuid = (String) el.get("uuid");
                if (uuid != null) elementByUuid.put(uuid, el);
            }

            // 3. Extract pivots from outliner
            Map pivots = new LinkedHashMap(); // name → float[3]
            List outliner = root.containsKey("outliner") ? (List) root.get("outliner") : new ArrayList();
            collectPivots(outliner, groupByUuid, pivots);

            // 4. Extract childMap from outliner
            Map childMap = new LinkedHashMap(); // child → parent
            for (int i = 0; i < outliner.size(); i++) {
                Object item = outliner.get(i);
                if (item instanceof String) continue;
                if (!(item instanceof Map)) continue;
                Map node = (Map) item;
                String uuid = (String) node.get("uuid");
                Map g = uuid != null ? (Map) groupByUuid.get(uuid) : null;
                String name = g != null ? (String) g.get("name") : (String) node.get("name");
                List children = node.containsKey("children") ? (List) node.get("children") : null;
                if (children != null && name != null) {
                    collectChildren(children, name, groupByUuid, elementByUuid, childMap);
                }
            }

            // 5. Extract animations
            Map animations = new LinkedHashMap(); // clipName → clip data
            List bbAnims = root.containsKey("animations") ? (List) root.get("animations") : new ArrayList();
            for (int a = 0; a < bbAnims.size(); a++) {
                Map bbAnim = (Map) bbAnims.get(a);
                String animName = bbAnim.containsKey("name") ? (String) bbAnim.get("name") : "clip_" + a;
                if (animName.startsWith("animation.")) {
                    animName = animName.substring("animation.".length());
                }

                Map clip = new LinkedHashMap();
                Object loopVal = bbAnim.get("loop");
                clip.put("loop", "loop".equals(loopVal) || Boolean.TRUE.equals(loopVal));
                clip.put("length", bbAnim.containsKey("length") ? toNumber(bbAnim.get("length")) : 1.0);

                Map bones = new LinkedHashMap();
                Map animators = bbAnim.containsKey("animators") ? (Map) bbAnim.get("animators") : new HashMap();
                Iterator ait = animators.entrySet().iterator();
                while (ait.hasNext()) {
                    Map.Entry ae = (Map.Entry) ait.next();
                    Map animator = (Map) ae.getValue();
                    String type = (String) animator.get("type");
                    if (!"bone".equals(type)) continue;

                    String boneName = (String) animator.get("name");
                    if (boneName == null) {
                        Map bg = (Map) groupByUuid.get(ae.getKey());
                        if (bg != null) boneName = (String) bg.get("name");
                    }
                    if (boneName == null) continue;

                    List keyframes = animator.containsKey("keyframes") ? (List) animator.get("keyframes") : new ArrayList();
                    if (keyframes.isEmpty()) continue;

                    Map boneData = new LinkedHashMap();
                    for (int k = 0; k < keyframes.size(); k++) {
                        Map kf = (Map) keyframes.get(k);
                        String channel = (String) kf.get("channel");
                        if (!"rotation".equals(channel) && !"position".equals(channel)) continue;

                        if (!boneData.containsKey(channel)) boneData.put(channel, new LinkedHashMap());
                        Map channelMap = (Map) boneData.get(channel);

                        List dataPoints = (List) kf.get("data_points");
                        Map dp = (Map) dataPoints.get(0);
                        double x = parseCoord(dp.get("x"));
                        double y = parseCoord(dp.get("y"));
                        double z = parseCoord(dp.get("z"));

                        Object timeObj = kf.get("time");
                        String timeKey = formatTime(toNumber(timeObj));
                        channelMap.put(timeKey, new double[]{x, y, z});
                    }

                    if (!boneData.isEmpty()) {
                        bones.put(boneName, boneData);
                    }
                }

                if (!bones.isEmpty()) {
                    clip.put("bones", bones);
                    animations.put(animName, clip);
                }
            }

            // 6. Write output
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"format_version\": \"1.0\",\n");

            // Pivots
            sb.append("  \"pivots\": {\n");
            writePivots(sb, pivots);
            sb.append("  },\n");

            // ChildMap
            sb.append("  \"childMap\": {\n");
            writeChildMap(sb, childMap);
            sb.append("  },\n");

            // Animations
            sb.append("  \"animations\": {\n");
            writeAnimations(sb, animations);
            sb.append("  }\n");

            sb.append("}\n");

            FileOutputStream fos = new FileOutputStream(output);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();

            // 7. Summary
            int boneCount = 0;
            Iterator cit = animations.values().iterator();
            while (cit.hasNext()) {
                Map cl = (Map) cit.next();
                Map bn = (Map) cl.get("bones");
                if (bn != null) boneCount += bn.size();
            }

            System.out.println("Converted: " + input + " -> " + output);
            System.out.println();
            System.out.println("  Pivots:     " + pivots.size() + " bones");
            System.out.println("  ChildMap:   " + childMap.size() + " entries");
            System.out.println("  Animations: " + animations.size() + " clips, " + boneCount + " animated bones");
            System.out.println();

            if (animations.isEmpty()) {
                System.out.println("  ! No animations found. If your model has animations,");
                System.out.println("    make sure they are created in Blockbench before exporting.");
                System.out.println();
            }

            System.out.println("Next steps:");
            System.out.println("  1. Export OBJ from Blockbench: File > Export > Export OBJ Model");
            System.out.println("  2. Place both files in your mod resources (e.g. /models/)");
            System.out.println("  3. See README.md for the Java integration code");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // Outliner traversal
    // -----------------------------------------------------------------------

    private static void collectPivots(List items, Map groupByUuid, Map pivots) {
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof String) continue;
            if (!(item instanceof Map)) continue;

            Map node = (Map) item;
            String uuid = (String) node.get("uuid");
            Map g = uuid != null ? (Map) groupByUuid.get(uuid) : null;
            String name = g != null ? (String) g.get("name") : (String) node.get("name");
            List origin = g != null ? (List) g.get("origin") : (List) node.get("origin");

            if (name != null && origin != null) {
                pivots.put(name, new double[]{
                    toNumber(origin.get(0)),
                    toNumber(origin.get(1)),
                    toNumber(origin.get(2))
                });
            }

            List children = node.containsKey("children") ? (List) node.get("children") : null;
            if (children != null) collectPivots(children, groupByUuid, pivots);
        }
    }

    private static void collectChildren(List items, String parentName, Map groupByUuid, Map elementByUuid, Map childMap) {
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof String) {
                // Element UUID
                Map el = (Map) elementByUuid.get(item);
                if (el != null && parentName != null) {
                    String elName = (String) el.get("name");
                    if (elName != null) childMap.put(elName, parentName);
                }
                continue;
            }
            if (!(item instanceof Map)) continue;

            Map node = (Map) item;
            String uuid = (String) node.get("uuid");
            Map g = uuid != null ? (Map) groupByUuid.get(uuid) : null;
            String name = g != null ? (String) g.get("name") : (String) node.get("name");

            if (parentName != null && name != null) {
                childMap.put(name, parentName);
            }

            List children = node.containsKey("children") ? (List) node.get("children") : null;
            if (children != null) collectChildren(children, name, groupByUuid, elementByUuid, childMap);
        }
    }

    // -----------------------------------------------------------------------
    // JSON output helpers
    // -----------------------------------------------------------------------

    private static void writePivots(StringBuilder sb, Map pivots) {
        Iterator it = pivots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            double[] v = (double[]) e.getValue();
            sb.append("    \"").append(e.getKey()).append("\": [");
            sb.append(fmtNum(v[0])).append(", ").append(fmtNum(v[1])).append(", ").append(fmtNum(v[2]));
            sb.append("]");
            if (it.hasNext()) sb.append(",");
            sb.append("\n");
        }
    }

    private static void writeChildMap(StringBuilder sb, Map childMap) {
        Iterator it = childMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            sb.append("    \"").append(e.getKey()).append("\": \"").append(e.getValue()).append("\"");
            if (it.hasNext()) sb.append(",");
            sb.append("\n");
        }
    }

    private static void writeAnimations(StringBuilder sb, Map animations) {
        Iterator ait = animations.entrySet().iterator();
        while (ait.hasNext()) {
            Map.Entry ae = (Map.Entry) ait.next();
            String clipName = (String) ae.getKey();
            Map clip = (Map) ae.getValue();

            sb.append("    \"").append(clipName).append("\": {\n");
            sb.append("      \"loop\": ").append(clip.get("loop")).append(",\n");
            sb.append("      \"length\": ").append(fmtNum(((Number) clip.get("length")).doubleValue())).append(",\n");
            sb.append("      \"bones\": {\n");

            Map bones = (Map) clip.get("bones");
            Iterator bit = bones.entrySet().iterator();
            while (bit.hasNext()) {
                Map.Entry be = (Map.Entry) bit.next();
                String boneName = (String) be.getKey();
                Map boneData = (Map) be.getValue();

                sb.append("        \"").append(boneName).append("\": {\n");

                Iterator cit = boneData.entrySet().iterator();
                while (cit.hasNext()) {
                    Map.Entry ce = (Map.Entry) cit.next();
                    String channel = (String) ce.getKey();
                    Map keyframes = (Map) ce.getValue();

                    sb.append("          \"").append(channel).append("\": {\n");

                    // Sort keyframes by time
                    List timeEntries = new ArrayList(keyframes.entrySet());
                    Collections.sort(timeEntries, new Comparator() {
                        public int compare(Object a, Object b) {
                            double ta = Double.parseDouble((String) ((Map.Entry) a).getKey());
                            double tb = Double.parseDouble((String) ((Map.Entry) b).getKey());
                            return Double.compare(ta, tb);
                        }
                    });

                    Iterator kit = timeEntries.iterator();
                    while (kit.hasNext()) {
                        Map.Entry ke = (Map.Entry) kit.next();
                        String time = (String) ke.getKey();
                        double[] val = (double[]) ke.getValue();
                        sb.append("            \"").append(time).append("\": [");
                        sb.append(fmtNum(val[0])).append(", ").append(fmtNum(val[1])).append(", ").append(fmtNum(val[2]));
                        sb.append("]");
                        if (kit.hasNext()) sb.append(",");
                        sb.append("\n");
                    }

                    sb.append("          }");
                    if (cit.hasNext()) sb.append(",");
                    sb.append("\n");
                }

                sb.append("        }");
                if (bit.hasNext()) sb.append(",");
                sb.append("\n");
            }

            sb.append("      }\n");
            sb.append("    }");
            if (ait.hasNext()) sb.append(",");
            sb.append("\n");
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static String readFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        byte[] data = new byte[(int) f.length()];
        fis.read(data);
        fis.close();
        return new String(data, "UTF-8");
    }

    private static double toNumber(Object o) {
        if (o instanceof Float) return ((Float) o).doubleValue();
        if (o instanceof Double) return (Double) o;
        if (o instanceof Integer) return ((Integer) o).doubleValue();
        if (o instanceof Long) return ((Long) o).doubleValue();
        return Double.parseDouble(o.toString());
    }

    private static double parseCoord(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(o.toString());
    }

    private static String formatTime(double t) {
        if (t == Math.floor(t) && t < 1e10) return String.valueOf((int) t);
        String s = String.valueOf(t);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

    private static String fmtNum(double v) {
        if (v == Math.floor(v) && Math.abs(v) < 1e10) return String.valueOf((int) v);
        // Trim trailing zeros
        String s = String.valueOf(v);
        if (s.contains(".") && !s.contains("E") && !s.contains("e")) {
            while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    // -----------------------------------------------------------------------
    // Minimal JSON Parser (recursive descent) — same pattern as
    // Aero_AnimationLoader but standalone (no package dependency)
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
            Map map = new LinkedHashMap(); // preserve insertion order
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
                    else if (esc == 'u') {
                        String hex = s.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    else sb.append(esc);
                } else {
                    sb.append(c);
                }
            }
            throw new RuntimeException("Unterminated string");
        }

        private Object parseNumber() {
            int start = pos;
            if (pos < s.length() && s.charAt(pos) == '-') pos++;
            boolean isFloat = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isDigit(c)) { pos++; continue; }
                if (c == '.') { isFloat = true; pos++; continue; }
                if (c == 'e' || c == 'E') {
                    isFloat = true;
                    pos++;
                    if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                    continue;
                }
                break;
            }
            String num = s.substring(start, pos);
            if (isFloat) return Double.valueOf(Double.parseDouble(num));
            long lv = Long.parseLong(num);
            if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE) return Integer.valueOf((int) lv);
            return Long.valueOf(lv);
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
