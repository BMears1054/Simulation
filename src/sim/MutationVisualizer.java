package sim;

import java.awt.Color;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks recently‐mutated creatures and fades a highlight color over time.
 */
public class MutationVisualizer {
    private static final int HIGHLIGHT_TICKS = 25;
    private static final Color HIGHLIGHT_COLOR = new Color(255, 0, 255);
    private final Map<Creature,Integer> timers = new WeakHashMap<>();

    /** Call when a creature is born (and thus has just mutated). */
    public void registerMutation(Creature c) {
        timers.put(c, HIGHLIGHT_TICKS);
    }

    /** Advance all timers; drop entries that reach zero. */
    public void tick() {
        Iterator<Map.Entry<Creature,Integer>> it = timers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Creature,Integer> e = it.next();
            int t = e.getValue() - 1;
            if (t <= 0) it.remove();
            else        e.setValue(t);
        }
    }

    /**
     * Blend from bright red → defaultColor based on how recently this creature mutated.
     * If no recent mutation, returns defaultColor unchanged.
     */
    public Color getColor(Creature c, Color defaultColor) {
        Integer t = timers.get(c);
        if (t == null) return defaultColor;
        float ratio = t / (float)HIGHLIGHT_TICKS; // 1.0 → just mutated, 0.0 → faded out
        int r = (int)(HIGHLIGHT_COLOR.getRed()   * ratio + defaultColor.getRed()   * (1 - ratio));
        int g = (int)(HIGHLIGHT_COLOR.getGreen() * ratio + defaultColor.getGreen() * (1 - ratio));
        int b = (int)(HIGHLIGHT_COLOR.getBlue()  * ratio + defaultColor.getBlue()  * (1 - ratio));
        return new Color(r, g, b);
    }
}
