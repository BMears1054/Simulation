package sim;

import java.util.*;
import java.util.stream.Collectors;

public class Creature {
    private static final Random RNG = new Random();
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    public enum Sex { MALE, FEMALE }

    // — Genome and life state —
    private final char[] genome;
    private final Sex sex;
    private int age = 0;
    private boolean alive = true;

    // — Spatial, hunger, neural “brain,” memory, and foraging —
    private int x, y;
    private int hunger = 0;
    private static final int MAX_HUNGER     = 25;
    private static final int MOVE_SPEED     = 6;
    static final int HOVER_DISTANCE = 10;
    private static final int PACK_RADIUS    = 30;
    private static final double FORAGE_PROB = 0.10; // 10% chance per tick
    private final NeuralNet brain;
    private final Deque<double[]> memory;
    private static final int MEMORY_SIZE    = 5;
    private final Creature parentA, parentB;

    public Creature(char[] genome, Sex sex) {
    	this.parentA = null;
    	this.parentB = null;
        this.genome = genome;
        this.sex    = sex;
        this.x      = RNG.nextInt(Simulation.WORLD_W);
        this.y      = RNG.nextInt(Simulation.WORLD_H);
        this.brain  = new NeuralNet(5, 8, 2);
        this.memory = new ArrayDeque<>(MEMORY_SIZE);
        
        // Pre‐bias the "survival" inputs (indices 3 and 4)
        double biasAmount = 0.2;
        brain.addInputBias(3, biasAmount);  // dxSurv
        brain.addInputBias(4, biasAmount);  // dySurv
    }

    private Creature(Creature parentA, Creature parentB, char[] genome, Sex sex, NeuralNet brain, Deque<double[]> mem ) {
        this.parentA = parentA;
        this.parentB = parentB;
    	this.genome = genome;
        this.sex    = sex;
        this.x       = (parentA.x + parentB.x) / 2;
        this.y       = (parentA.y + parentB.y) / 2;
        this.brain  = brain;
        this.memory = new ArrayDeque<>(mem);
    }

    /** Helper to generate a random root creature */
    public static Creature randomCreature(int genomeLength) {
        char[] g = new char[genomeLength];
        for (int i = 0; i < genomeLength; i++)
            g[i] = (char)('A' + RNG.nextInt(26));
        Sex s = RNG.nextBoolean() ? Sex.MALE : Sex.FEMALE;
        return new Creature(g, s);
    }


    public Creature mateWith(Creature other) {
        // genome crossover + mutation
        int len = genome.length;
        char[] childG = new char[len];
        int cp = RNG.nextInt(len);
        for (int i = 0; i < len; i++) {
            childG[i] = (i < cp ? this.genome[i] : other.genome[i]);
            if (RNG.nextDouble() < 0.01)
                childG[i] = (char)('A' + RNG.nextInt(26));
        }
        Sex childSex = RNG.nextBoolean() ? Sex.MALE : Sex.FEMALE;

        // brain crossover + mutation
        NeuralNet childBrain = this.brain.crossover(other.brain);
        childBrain.mutate(0.05, 0.2);

        // memory mixing
        Deque<double[]> childMem = new ArrayDeque<>(MEMORY_SIZE);
        List<double[]> aMem = new ArrayList<>(this.memory);
        List<double[]> bMem = new ArrayList<>(other.memory);
        for (int i = 0; i < MEMORY_SIZE/2 && i < aMem.size(); i++) {
            childMem.addLast(aMem.get(i));
        }
        for (int i = 0; childMem.size() < MEMORY_SIZE && i < bMem.size(); i++) {
            childMem.addLast(bMem.get(i));
        }
 
        return new Creature(this, other, childG, childSex, childBrain, childMem);
    }

    /** Set initial position (used for children) */
    public void setPosition(int x, int y) {
        this.x = clamp(x, 0, Simulation.WORLD_W-1);
        this.y = clamp(y, 0, Simulation.WORLD_H-1);
    }
    
    public void die()     			{ alive = false; }
    public void stepAge() 			{ if (alive) age++; }
    public Creature getParentA() 	{ return parentA; }
    public Creature getParentB() 	{ return parentB; }
    public int  getAge()            { return age; }
    public boolean isAlive()        { return alive; }
    public Sex getSex()             { return sex; }
    public String getGenomeString() { return new String(genome); }
    public int  getX()              { return x; }
    public int  getY()              { return y; }
    
    
 
    /**
     * Returns a fitness score between 1.0 and 100.0, computed as a weighted
     * combination of:
     *   1) Hunger sub‐score   (higher if well‐fed)
     *   2) Age sub‐score      (peaks around middle age)
     *   3) Biome sub‐score    (based on current biome’s forageMod)
     *   4) Pack sub‐score     (based on number of neighbors)
     */
    public double getFitness(Simulation sim) {
        // 1) Hunger sub‐score in [0…1]
        double hungerScore = 1.0 - ((double) hunger / MAX_HUNGER);
        hungerScore = Math.max(0, Math.min(1, hungerScore));

        // ── (2) Age sub‐score using a Gaussian [0…1], peaking at mid‐life ──
        double mid     = Simulation.MAX_AGE / 2.0;
        double sigma   = mid / 2.0;  
        // Gaussian: exp(-((age - mid)^2) / (2*sigma^2))
        double ageDiff = age - mid;
        double ageScore = Math.exp(- (ageDiff * ageDiff) / (2 * sigma * sigma));
        // ageScore is in (0…1], with age == mid → 1.0, and tails → 0.0

        // 3) Biome sub‐score in [0…1]
        Biome b = sim.getBiomeAt(x, y);
        double maxForage = 3.0;  // adjust if your highest forageMod differs
        double biomeScore = b.forageModifier / maxForage;
        biomeScore = Math.max(0, Math.min(1, biomeScore));

        // 4) Pack sub‐score in [0…1], based on neighbor count
        List<Creature> neighbors = sim.getCreatures().stream()
            .filter(c -> c != this)
            .filter(c -> {
                int dx = c.x - x, dy = c.y - y;
                return dx*dx + dy*dy <= PACK_RADIUS * PACK_RADIUS;
            })
            .collect(Collectors.toList());
        int n = neighbors.size();
        double packScore = Math.min(n / 10.0, 1.0);  // ideal ≤10 neighbors

        // 5) Weighted combination (all in [0…1])
        double wH = 0.4, wA = 0.3, wB = 0.2, wP = 0.1;
        double raw = (hungerScore * wH)
                   + (ageScore    * wA)
                   + (biomeScore  * wB)
                   + (packScore   * wP);

        // 6) Scale to [1…100]
        return 1.0 + (raw * 99.0);
    } 
    @Override
    public String toString() {
        return String.format("Age:%2d\tSex:%-6s Genome:%s Hunger:%d",
                             age, sex, getGenomeString(), hunger);
    }
    
    /**
     * Called once per timestep:
     * 1) Attempt forage based on biome.
     * 2) If unsuccessful, increase hunger and possibly die.
     * 3) Sense pack centroid & density.
     * 4) Sense direction toward highest-survival biome.
     * 5) Move according to the net’s output.
     */
    public void act(Simulation sim) {
    	  // 1) Forage based on current biome AND elevation
        Biome bCur = sim.getBiomeAt(x, y);
        double elevationAtCurrent = sim.getHeight(x, y);
        // reduce forage probability at higher elevation: multiply by (1 - elevation)
        double pForage = FORAGE_PROB 
                * bCur.forageModifier 
                * elevationAtCurrent;
        
        if (RNG.nextDouble() < pForage) {
            hunger = 0;
        } else {
            // 2) Hunger check
            hunger++;
            if (hunger > MAX_HUNGER) {
                die();
                return;
            }
        }

        // 3) Pack sensing: centroid & density within PACK_RADIUS
        List<Creature> neighbors = sim.getCreatures().stream()
            .filter(c -> c != this)
            .filter(c -> {
                int dx = c.x - x, dy = c.y - y;
                return dx*dx + dy*dy <= PACK_RADIUS*PACK_RADIUS;
            })
            .collect(Collectors.toList());

        double dxPack = 0, dyPack = 0, density = 0;
        if (!neighbors.isEmpty()) {
            double avgX = neighbors.stream().mapToDouble(c -> c.x).average().orElse(x);
            double avgY = neighbors.stream().mapToDouble(c -> c.y).average().orElse(y);
            dxPack  = (avgX - x) / (double)Simulation.WORLD_W;
            dyPack  = (avgY - y) / (double)Simulation.WORLD_H;
            density = neighbors.size() / (Math.PI * PACK_RADIUS * PACK_RADIUS);
        }
       
        double oldScore = bCur.forageModifier / bCur.deathModifier;

        double dxSurv = 0, dySurv = 0;
        {
            Biome best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Biome b : sim.getBiomes()) {
                double score = b.forageModifier / b.deathModifier;
                if (score > bestScore) {
                    bestScore = score;
                    best = b;
                }
            }
            if (best != null) {
                double centerX = best.x + best.width  / 2.0;
                double centerY = best.y + best.height / 2.0;
                dxSurv = (centerX - x) / (double)Simulation.WORLD_W;
                dySurv = (centerY - y) / (double)Simulation.WORLD_H;
            }
        }

        // --- Build inputs & forward through net ---
        double[] inputs = { dxPack, dyPack, density, dxSurv, dySurv };
        double[] out    = brain.forward(inputs);

        // Intended movement at full MOVE_SPEED
        int intendedVX = (int) Math.signum(out[0]) * MOVE_SPEED;
        int intendedVY = (int) Math.signum(out[1]) * MOVE_SPEED;

        // Compute elevation difference to scale movement
        int tx = clamp(x + intendedVX, 0, Simulation.WORLD_W - 1);
        int ty = clamp(y + intendedVY, 0, Simulation.WORLD_H - 1);
        double elevationOld = sim.getHeight(x, y);
        double elevationNew = sim.getHeight(tx, ty);
        double slope = elevationNew - elevationOld;  // positive = uphill

        // Scale factor: uphill (slope>0) slows you down, downhill (slope<0) speeds you up.
        // We clamp factor between [0.5 .. 1.5].
        double factor = 1.0 - slope;
        factor = Math.max(0.5, Math.min(1.5, factor));

        int actualVX = (int) Math.signum(out[0] == 0 ? 0 : intendedVX) * (int) Math.max(1, Math.round(Math.abs(intendedVX) * factor));
        int actualVY = (int) Math.signum(out[1] == 0 ? 0 : intendedVY) * (int) Math.max(1, Math.round(Math.abs(intendedVY) * factor));

        // Apply movement
        x = clamp(x + actualVX, 0, Simulation.WORLD_W - 1);
        y = clamp(y + actualVY, 0, Simulation.WORLD_H - 1);

        // --- Compute newScore after the move ---
        Biome bNew     = sim.getBiomeAt(x, y);
        double newScore = bNew.forageModifier / bNew.deathModifier;

        // --- Reward = positive if we moved closer to high‐survival biome, negative otherwise ---
        double reward = newScore - oldScore;
        brain.reward(0.05, reward);
    }

    /** Clamp v into the [lo, hi] range. */
    private int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}