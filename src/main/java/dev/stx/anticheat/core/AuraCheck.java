package dev.stx.anticheat.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** KillAura davranış kontrolleri: çoklu hedef + vuruştan hemen önce aşırı keskin rotasyon. */
public final class AuraCheck {
    private record Hit(long tick, int targetId) {}

    private final ArrayDeque<Hit> hits = new ArrayDeque<>();
    private final ArrayDeque<Boolean> flickWindow = new ArrayDeque<>();
    private final int multiTargets, multiTicks;
    private final double flickDegrees;
    private final FlagBuffer multiBuf = new FlagBuffer(2, 0.5);
    private final FlagBuffer flickBuf = new FlagBuffer(3, 0.5);

    public AuraCheck(int multiTargets, int multiTicks, double flickDegrees) {
        this.multiTargets = multiTargets; this.multiTicks = multiTicks; this.flickDegrees = flickDegrees;
    }

    /**
     * @param maxRecentYawDelta vuruştan önceki 2 tik içindeki en büyük |yaw farkı|
     */
    public Result onHit(long tick, int targetId, double maxRecentYawDelta) {
        hits.addLast(new Hit(tick, targetId));
        while (!hits.isEmpty() && tick - hits.peekFirst().tick() > multiTicks) hits.pollFirst();

        Result r = null;
        Set<Integer> ids = new HashSet<>();
        for (Hit h : hits) ids.add(h.targetId());
        if (ids.size() >= multiTargets) {
            if (multiBuf.fail(1)) r = Result.of(1.0, "Aura çoklu hedef: " + ids.size() + " farklı varlık / " + multiTicks + " tik");
        } else multiBuf.pass();

        boolean flick = maxRecentYawDelta >= flickDegrees;
        flickWindow.addLast(flick);
        if (flickWindow.size() > 8) flickWindow.pollFirst();
        if (flickWindow.size() == 8) {
            int c = 0; for (boolean b : flickWindow) if (b) c++;
            if (c >= 5 && flickBuf.fail(1) && r == null)
                r = Result.of(1.0, "Aura snap: son 8 vuruşun " + c + " tanesinden önce >" + (int) flickDegrees + "° dönüş");
            else if (c < 5) flickBuf.pass();
        }
        return r;
    }
}
