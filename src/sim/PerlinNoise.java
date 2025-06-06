package sim;

public class PerlinNoise {
/**
 * A simple 2D Perlin‐noise implementation (adapted from Ken Perlin’s improved noise).
 * Call noise(x,y) with floating‐point coordinates; it returns a value in [−1, +1].
 */
    private static final int[] p = new int[512];
    private static final int[] permutation = {
        151,160,137,91,90,15,
        131,13,201,95,96,53,194,233,7,225,
        140,36,103,30,69,142,8,99,37,240,21,10,
        23,190, 6,148,247,120,234,75,0,26,197,62,
        94,252,219,203,117,35,11,32,57,177,33,
        88,237,149,56,87,174,20,125,136,171,
        168, 68,175,74,165,71,134,139,48,27,
        166,77,146,158,231,83,111,229,122,60,
        211,133,230,220,105,92,41,55,46,245,40,
        244,102,143,54, 65,25,63,161, 1,216,
        80,73,209,76,132,187,208, 89,18,169,
        200,196,135,130,116,188,159,86,164,100,
        109,198,173,186, 3,64,52,217,226,250,
        124,123,5,202,38,147,118,126,255,82,
        85,212,207,206,59,227,47,16,58,17,182,
        189,28,42,223,183,170,213,119,248,152,
        2,44,154,163,70,221,153,101,155,167,
        43,172,9,129,22,39,253, 19,98,108,
        110,79,113,224,232,178,185,112,104,218,
        246,97,228,251,34,242,193,238,210,144,
        12,191,179,162,241, 81,51,145,235,249,
        14,239,107,49,192,214, 31,181,199,106,
        157,184, 84,204,176,115,121,50,45,127,
         4,150,254,138,236,205,93,222,114, 67,
        29,24,72,243,141,128,195,78,66,215,61,
        156,180
    };

    static {
        // Duplicate the permutation array
        for (int i = 0; i < 256; i++) {
            p[256 + i] = p[i] = permutation[i];
        }
    }

    /** Fade curve: 6t^5 − 15t^4 + 10t^3 */
    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** Linear interpolation */
    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** Gradient function for 2D */
    private static double grad(int hash, double x, double y) {
        int h = hash & 0x3F;        // take lower 6 bits
        double u = (h < 4) ? x : y;
        double v = (h < 4) ? y : x;
        return (((h & 1) == 0) ? u : -u) + (((h & 2) == 0) ? v : -v);
    }

    /**
     * Returns Perlin noise in [−1, +1] at coords (x, y).
     * Larger‐scale features emerge at lower frequency.
     */
    public static double noise(double x, double y) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        double u = fade(x);
        double v = fade(y);

        int aa = p[p[X] + Y];
        int ab = p[p[X] + Y + 1];
        int ba = p[p[X + 1] + Y];
        int bb = p[p[X + 1] + Y + 1];

        double gradAA = grad(aa, x    , y    );
        double gradBA = grad(ba, x - 1, y    );
        double gradAB = grad(ab, x    , y - 1);
        double gradBB = grad(bb, x - 1, y - 1);

        double lerpX1 = lerp(u, gradAA, gradBA);
        double lerpX2 = lerp(u, gradAB, gradBB);
        return lerp(v, lerpX1, lerpX2);
    }

    /**
     * Fractal (octave) Perlin noise: sum of multiple frequencies.
     * @param x          sample x coordinate
     * @param y          sample y coordinate
     * @param octaves    number of layers; higher→more detail
     * @param persistence usually in [0.3…0.8]: lower→higher contrast
     */
    public static double fractal(double x, double y, int octaves, double persistence) {
        double total = 0;
        double freq = 1;
        double amp = 1;
        double max  = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise(x * freq, y * freq) * amp;
            max   += amp;
            amp    *= persistence;
            freq   *= 2;
        }
        return total / max;  // normalized to [−1…+1]
    }
}
