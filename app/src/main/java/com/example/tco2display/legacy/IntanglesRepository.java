package com.example.tco2display.legacy;

import com.google.gson.*;
import java.util.*;

public class IntanglesRepository {

    private static final double SAVINGS_PER_KG = 0.926;
    private final IntanglesApi api;

    public IntanglesRepository(IntanglesApi api) { this.api = api; }

    private Map<String, String> headers(String token) {
        Map<String, String> h = new HashMap<String, String>();
        h.put("Accept", "application/json, text/plain, */*");
        h.put("intangles-session-type", "web");
        h.put("intangles-user-lang", "en");
        h.put("intangles-user-token", token == null ? "" : token);
        h.put("intangles-user-tz", "Asia/Calcutta");
        h.put("Referer", "https://bemblueedge.intangles.com/");
        h.put("Origin", "https://bemblueedge.intangles.com");
        h.put("User-Agent", "android-okhttp/3.12");
        return h;
    }

    public double fetchAndSumTco2(String token,
                                  String accId,
                                  String specIds,
                                  int psize,
                                  String lang,
                                  boolean noDefaultFields,
                                  String proj,
                                  String groups,
                                  boolean lastloc,
                                  String lngUnit,
                                  double lngDensity) throws Exception {

        double totalInput = 0.0;
        String fuelKey = null;
        int pnum = 1;

        while (true) {
            JsonElement payload = api.fuelConsumed(
                    headers(token), pnum, psize, noDefaultFields, proj, specIds, groups, lastloc, accId, lang
            ).execute().body();

            List<JsonObject> rows = iterPayloadRows(payload);
            if (rows.isEmpty()) break;

            if (fuelKey == null) {
                fuelKey = detectFuelKey(rows);
                if (fuelKey == null) throw new RuntimeException("Could not detect a fuel field");
            }

            double pageSum = 0.0;
            for (JsonObject r : rows) {
                Double v = getValueByDotted(r, fuelKey);
                if (v != null) pageSum += v;
            }
            totalInput += pageSum;

            if (rows.size() < psize) break;
            pnum++;
        }

        double totalLngKg;
        if ("kg".equalsIgnoreCase(lngUnit)) totalLngKg = totalInput;
        else if ("l".equalsIgnoreCase(lngUnit) || "lt".equalsIgnoreCase(lngUnit)
              || "litre".equalsIgnoreCase(lngUnit) || "liter".equalsIgnoreCase(lngUnit))
            totalLngKg = totalInput * lngDensity;
        else throw new IllegalArgumentException("Invalid lngUnit: " + lngUnit);

        return (totalLngKg * SAVINGS_PER_KG) / 1000.0;
    }

    /* ------------ helpers (mirror Python) ------------ */

    private List<JsonObject> iterPayloadRows(JsonElement payload) {
        List<JsonObject> out = new ArrayList<JsonObject>();
        if (payload == null) return out;

        if (payload.isJsonArray()) {
            for (JsonElement e : payload.getAsJsonArray())
                if (e.isJsonObject()) out.add(e.getAsJsonObject());
            return out;
        }
        if (payload.isJsonObject()) {
            JsonObject o = payload.getAsJsonObject();
            for (String k : Arrays.asList("result", "data")) {
                if (o.has(k)) {
                    JsonElement v = o.get(k);
                    if (v.isJsonArray()) {
                        for (JsonElement e : v.getAsJsonArray())
                            if (e.isJsonObject()) out.add(e.getAsJsonObject());
                        return out;
                    } else if (v.isJsonObject()) {
                        out.add(v.getAsJsonObject());
                        return out;
                    }
                }
            }
            out.add(o);
        }
        return out;
    }

    private String detectFuelKey(List<JsonObject> sampleRows) {
        Set<String> lowers = new HashSet<String>();
        for (JsonObject row : sampleRows) {
            walkKeys(row, "", new WalkCb() {
                public void onLeaf(String key, JsonElement v) {
                    if (key != null && key.length() > 0 && (v.isJsonPrimitive() || v.isJsonNull()))
                        lowers.add(key.toLowerCase(Locale.US));
                }
            });
        }
        String[] preferred = new String[] {
            "total_fuel_consumed","data.total_fuel_consumed","fuel_consumed",
            "total_fuel","fuel_total","fuel"
        };
        for (String p : preferred) if (lowers.contains(p.toLowerCase(Locale.US))) return p;
        for (String k : lowers) if (k.contains("fuel") && (k.contains("consum") || k.contains("total"))) return k;
        return null;
    }

    private interface WalkCb { void onLeaf(String key, JsonElement v); }
    private void walkKeys(JsonElement elem, String prefix, WalkCb cb) {
        if (elem == null) return;
        if (elem.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : elem.getAsJsonObject().entrySet()) {
                String nk = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
                walkKeys(e.getValue(), nk, cb);
            }
        } else if (elem.isJsonArray()) {
            for (JsonElement v : elem.getAsJsonArray()) walkKeys(v, prefix, cb);
        } else {
            cb.onLeaf(prefix, elem);
        }
    }

    private Double getValueByDotted(JsonObject row, String dotted) {
        String[] parts = dotted.split("\\.");
        JsonElement cur = row;
        for (String p : parts) {
            if (cur != null && cur.isJsonObject() && cur.getAsJsonObject().has(p)) {
                cur = cur.getAsJsonObject().get(p);
            } else {
                final Double[] found = new Double[1];
                walkKeys(row, "", new WalkCb() {
                    public void onLeaf(String key, JsonElement v) {
                        if (key.equalsIgnoreCase(dotted)) {
                            if (v != null && v.isJsonPrimitive()) {
                                try { found[0] = v.getAsDouble(); } catch (Exception ignored) {}
                            }
                        }
                    }
                });
                return found[0];
            }
        }
        if (cur != null && cur.isJsonPrimitive()) {
            JsonPrimitive p = cur.getAsJsonPrimitive();
            if (p.isNumber()) return p.getAsDouble();
            if (p.isString()) {
                try { return Double.parseDouble(p.getAsString().trim().replace(",", "")); } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
