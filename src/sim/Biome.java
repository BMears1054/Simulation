package sim;


/**
 * A rectangular region of the world with its own
 * forage‐success and death‐rate modifiers.
 */
public class Biome {
    public final String name;
    public final int x, y, width, height;
    public double forageModifier;  // multiply base forage prob
    public double deathModifier;   // multiply base death prob

    public Biome(String name, int x, int y, int width, int height,
            double forageModifier, double deathModifier) {
   this.name          = name;
   this.x             = x;
   this.y             = y;
   this.width         = width;
   this.height        = height;
   this.forageModifier= forageModifier;
   this.deathModifier = deathModifier;
}

    /** True if the point (px,py) lies within this biome’s rectangle */
    public boolean contains(int px, int py) {
        return px >= x && px < x + width
            && py >= y && py < y + height;
    }
}
