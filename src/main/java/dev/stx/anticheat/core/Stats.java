package dev.stx.anticheat.core;

import java.util.Collection;

public final class Stats {
    private Stats() {}

    public static double mean(Collection<? extends Number> v) {
        if (v.isEmpty()) return 0;
        double s = 0;
        for (Number n : v) s += n.doubleValue();
        return s / v.size();
    }

    public static double stdDev(Collection<? extends Number> v) {
        if (v.size() < 2) return 0;
        double m = mean(v), s = 0;
        for (Number n : v) { double d = n.doubleValue() - m; s += d * d; }
        return Math.sqrt(s / v.size());
    }

    /** Toleranslı Öklid GCD. Fare hassasiyeti adımını (derece) kestirmek için kullanılır. */
    public static double gcd(double a, double b, double tol) {
        a = Math.abs(a); b = Math.abs(b);
        while (b > tol) {
            double t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
