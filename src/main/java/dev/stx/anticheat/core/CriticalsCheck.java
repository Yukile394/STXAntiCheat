package dev.stx.anticheat.core;

/**
 * Sahte Criticals: istemci vuruştan hemen önce zemindeyken küçük "mikro zıplama" paketleri yollar
 * (y+0.0625, y+0.00001 ...). Gerçek zıplama dy=0.42 ile başlar. Desen: önceki konumda bir bloğun
 * üstünde DURUYORDU, şimdi 0<dy<0.2 yukarıda ve artık duruyor değil. Halı/kar katmanına çıkış bu
 * desene girmez çünkü yeni konumda da "duruyor" olur.
 */
public final class CriticalsCheck {
    private long lastHopTick = Long.MIN_VALUE;
    private final FlagBuffer buf = new FlagBuffer(3, 0.5);

    public void onMove(long tick, MoveSample s, boolean wasStanding) {
        if (s.exempt() || s.special() || s.velocity()) return;
        if (wasStanding && !s.standing() && s.dy() > 0.0 && s.dy() < 0.2) lastHopTick = tick;
    }

    public Result onAttack(long tick) {
        if (lastHopTick != Long.MIN_VALUE && tick - lastHopTick <= 2) {
            lastHopTick = Long.MIN_VALUE;
            if (buf.fail(1)) return Result.of(1.0, "Criticals: vuruştan önce zeminden mikro-zıplama paketi");
        } else buf.pass();
        return null;
    }
}
