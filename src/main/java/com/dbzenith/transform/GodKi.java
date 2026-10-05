package com.dbzenith.transform;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * God ki levels 1-10. Awakening godly ki (the ritual quest or a wish) is level 1; time spent in god forms (and
 * meditating with god ki) grows it. Each level makes god forms stronger and cheaper to hold, gives a sharper edge
 * against lesser god ki, and the highest god forms ask for more of it.
 */
public final class GodKi {
    public static final String FLAG = "god_ki";
    public static final int MAX = 10;

    private GodKi() {}

    /** Experience needed for a level (seconds in god forms): 300 for level 2, 1200 for 3, ... 24300 for 10. */
    public static double xpFor(int level) {
        return level <= 1 ? 0 : 300.0 * (level - 1) * (level - 1);
    }

    public static int level(PlayerData d) {
        if (!d.hasFlag(FLAG)) return 0;
        int l = 1;
        while (l < MAX && d.getGodKiXp() >= xpFor(l + 1)) l++;
        return l;
    }

    /** Progress 0..1 towards the next level (1 at the top). */
    public static double progress(PlayerData d) {
        int l = level(d);
        if (l == 0) return 0;
        if (l >= MAX) return 1;
        return (d.getGodKiXp() - xpFor(l)) / (xpFor(l + 1) - xpFor(l));
    }

    /** A form that needs god ki, or grows out of one that does. */
    public static boolean isGodForm(Form f) {
        for (Form x = f; !x.isBase(); x = Forms.byId(x.parent())) {
            if (FLAG.equals(x.requiredFlag())) return true;
        }
        return false;
    }

    /** God ki level a form asks for: 1 for the first god forms, 2 for later ones, 3 for forms grown out of a god form, 5 for the last. */
    public static int required(Form f) {
        if (!isGodForm(f)) return 0;
        if (!FLAG.equals(f.requiredFlag())) return 3;
        return f.unlockLevel() >= 1800 ? 5 : f.unlockLevel() >= 1500 ? 2 : 1;
    }

    /** Multiplier on a god form's power: +2% a level past the first. */
    public static double powerFactor(PlayerData d, Form f) {
        int l = level(d);
        return l > 1 && isGodForm(f) ? 1.0 + 0.02 * (l - 1) : 1.0;
    }

    /** Factor on a god form's drain: 5% less a level past the first. */
    public static double drainFactor(PlayerData d, Form f) {
        int l = level(d);
        return l > 1 && isGodForm(f) ? 1.0 - 0.05 * (l - 1) : 1.0;
    }

    /** Called once a second: grow god ki in god forms and while meditating with it. */
    public static void tickSecond(ServerPlayer player, PlayerData d) {
        if (!d.hasFlag(FLAG)) return;
        double rate = DBZConfig.SERVER.godKiXpPerSecond.get();
        double gain = isGodForm(Forms.byId(d.getFormId())) ? rate : d.isMeditating() ? rate * 0.5 : 0;
        if (gain <= 0 || level(d) >= MAX) return;
        int before = level(d);
        d.setGodKiXp(d.getGodKiXp() + gain);
        int after = level(d);
        if (after > before) {
            player.displayClientMessage(Component.translatable("message.dbzenith.god_ki_level", after), false);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.ZENKAI.get(),
                    SoundSource.PLAYERS, 1f, 0.6f + after * 0.08f);
            ModNetwork.sendToTrackingAndSelf(player, ImpactPacket.at(player.position().add(0, 1, 0), new net.minecraft.world.phys.Vec3(0, 1, 0),
                    ImpactPacket.EXPLOSION, 0.8f, Forms.byId(d.getFormId()).auraColor(), player.getId()));
        }
    }
}
