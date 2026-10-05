package com.dbzenith.transform;

/**
 * Dragon Block V's multipliers (1.5x to 76x) live on a different stat model. They are mapped onto this mod's balance
 * curve (BALANCE.md: best forms around x4.5-6.5 by level 2000) with {@code 1 + 1.1 ln m}: the order and spacing of
 * the tiers survive (2x 1.76, 6x 2.97, 16x 4.05, 22x 4.40, 32x 4.81, 56x 5.43, 76x 5.76) and the balance tests keep
 * meaning something.
 */
public final class FormScale {
    private FormScale() {}

    public static double fromDbv(double m) {
        return m <= 1 ? m : Math.round((1 + 1.1 * Math.log(m)) * 100) / 100.0;
    }
}
