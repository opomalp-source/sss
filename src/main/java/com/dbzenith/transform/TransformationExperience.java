package com.dbzenith.transform;

import com.dbzenith.data.PlayerData;

/** Per-form first successful transformation history, persisted through the existing player flags. */
public final class TransformationExperience {
    private static final String PREFIX="form_experienced:";
    private TransformationExperience() {}
    public static int duration(int original,PlayerData data,Form form) {
        if(original<=0||form.isBase()) return original;
        int repeat=original*2;
        // Existing mastery is evidence that this form was already used before this update.
        if(data.hasFlag(PREFIX+form.id())||data.getMastery(form.id())>0||data.getFormId().equals(form.id())) return repeat;
        return Math.max(300+40*Math.min(8,Math.max(0,form.tier())),repeat*4);
    }
    public static void completed(PlayerData data,Form form) {
        if(!form.isBase()) data.setFlag(PREFIX+form.id(),true);
    }
}
