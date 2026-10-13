package com.dbzenith.client.render;

import com.dbzenith.appearance.HairCode;
import com.dbzenith.appearance.HairCode.Preset;
import com.dbzenith.transform.Form;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Character hair sculpted in head space, independent of the editor's 15-degree strand grid. */
public final class SculptedHair {
    private record Look(Preset preset, Form.HairStyle style) {}
    private static final Map<String, Look> LOOKS = looks();
    private static final Map<String, Optional<Look>> MATCHES = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Optional<Look>> e) { return size() > 256; }
    };

    private static Map<String, Look> looks() {
        Map<String, Look> map = new HashMap<>();
        for (Preset p : Preset.values()) {
            if (p == Preset.BALD) continue;
            map.put(p.code(), new Look(p, Form.HairStyle.NONE));
            for (Form.HairStyle style : Form.HairStyle.values()) {
                if (style == Form.HairStyle.NONE) continue;
                String code = HairCode.forForm(p.code(), Form.builder("hair_sculpt").hair(style).build());
                map.putIfAbsent(code, new Look(p, style));
            }
        }
        // Bald characters transforming use these legacy fallback codes.
        map.putIfAbsent(HairCode.forStyle(Form.HairStyle.SPIKY_TALL), new Look(Preset.SPIKY, Form.HairStyle.SPIKY_TALL));
        map.putIfAbsent(HairCode.forStyle(Form.HairStyle.LONG), new Look(Preset.SPIKY, Form.HairStyle.LONG));
        return Map.copyOf(map);
    }

    private static Look find(String code) {
        if (code == null || code.isEmpty()) return null;
        return MATCHES.computeIfAbsent(code, c -> {
            String canonical = HairCode.sanitize(c);
            return canonical == null ? Optional.empty() : Optional.ofNullable(LOOKS.get(canonical));
        }).orElse(null);
    }

    static List<HairMesh.Segment> transition(String from,String to,float progress) {
        Look a=find(from),b=find(to);
        return a==null||b==null||a.preset!=b.preset?null:HairPresetLibrary.create(a.preset,a.style,b.style,progress);
    }

    public static boolean isStock(String code) { return find(code) != null; }

    /** Null means user-authored hair: render the editable strand code instead. */
    static List<HairMesh.Segment> create(String code) {
        Look look = find(code);
        return look == null ? null : HairPresetLibrary.create(look.preset, look.style);
    }
}
