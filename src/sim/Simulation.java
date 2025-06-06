package sim;

import java.util.*;
import java.util.stream.Collectors;

public class Simulation {
    public static final int WORLD_W = 800, WORLD_H = 600;

    private final Random rng = new Random();
    final List<Creature> population = new ArrayList<>();
    private final List<Biome> biomes = new ArrayList<>();

    private final int genomeLength;
    private final double deathProbPerStep;
    private final double birthProbPerPair;

    static final int MAX_AGE = 100;
    private static final double AGE_DEATH_INCREASE_FACTOR = 1.0;
    private static final int JITTER_WINDOW = 4;
    private static final double FOOD_BREED_BONUS = 3.5;
    
    private int currentStep = 0;
    private final Events events = new Events();
    public interface MutationListener { void onMutation(Creature c); }
    private final List<MutationListener> mutationListeners = new ArrayList<>();
    public void addMutationListener(MutationListener l) {
        mutationListeners.add(l);
    }
    private void notifyMutation(Creature c) {
        for (var l : mutationListeners) l.onMutation(c);
    }
    
    // — Seasonal cycle configuration —
    public enum Season { SPRING, SUMMER, FALL, WINTER }
    private Season currentSeason = Season.SPRING;
    private int seasonLength = 25;        // number of ticks per season
    private int ticksIntoSeason = 0;
    
    private final double[][] heightMap;
    
    public Simulation(int initialPop, int genomeLength,
                      double deathProbPerStep, double birthProbPerPair) {
        this.genomeLength     = genomeLength;
        this.deathProbPerStep = deathProbPerStep;
        this.birthProbPerPair = birthProbPerPair;
        
        // seed initial creatures
        for (int i = 0; i < initialPop; i++) {
            population.add(Creature.randomCreature(genomeLength));
        }

        // Define biomes in order so that getBiomeAt finds the first match:
        biomes.clear();
        // Desert in top-left quadrant
        biomes.add(new Biome("Desert",
                             0, 0,
                             WORLD_W/2, WORLD_H/2,
                             /*forageMod=*/0.2,
                             /*deathMod=*/1.5));
        // Oasis in center
        biomes.add(new Biome("Oasis",
                             WORLD_W/4, WORLD_H/4,
                             WORLD_W/2, WORLD_H/2,
                             /*forageMod=*/3.0,
                             /*deathMod=*/0.9));
        // Plains as catch-all
        biomes.add(new Biome("Plains",
                             0, 0,
                             WORLD_W, WORLD_H,
                             /*forageMod=*/1.0,
                             /*deathMod=*/1.0));
        // Ensure seasonal modifiers are applied for SPRING
        applySeasonalModifiers();
        
        // 3) Generate & smooth a random heightMap
        heightMap = new double[WORLD_W][WORLD_H];
        generateHeightMap();
    }

    public void run(int steps) {
        for (int i = 0; i < steps; i++) {
            step();
            currentStep++;
        }
    }


    
    /** one full tick: events → regen → act → death → birth → aging */
    void step() {
    	  // 1) Advance seasonal clock
        ticksIntoSeason++;
        if (ticksIntoSeason >= seasonLength) {
            ticksIntoSeason = 0;
            currentSeason = nextSeason(currentSeason);
            applySeasonalModifiers();
        }
     // 2) Global events update
        events.update();

     // 3) Creature actions (foraging, movement, learning)
        Iterator<Creature> cit = population.iterator();
        while (cit.hasNext()) {
            Creature c = cit.next();
            c.act(this);
            if (!c.isAlive()) cit.remove();
        }
         
        // 3) Death phase (old‐age + random, modified by biome)
        double eventDeathMod = events.getDeathModifier();
        population.removeIf(c -> {
            boolean old = c.getAge() > MAX_AGE;
            Biome b = getBiomeAt(c.getX(), c.getY());
            double ageFactor = 1.0 + AGE_DEATH_INCREASE_FACTOR * c.getAge()/MAX_AGE;
            double fitRaw = c.getFitness(this);       
            double fitNorm = fitRaw / 100.0;
            double fitnessScale = 1.0 - 0.5 * fitNorm;
            double pDie = deathProbPerStep
                        * eventDeathMod
                        * b.deathModifier
                        * ageFactor
                        * fitnessScale;
            boolean rand = rng.nextDouble() < pDie;
            if (old || rand) c.die();
            return old || rand;
        });

        // 4) Birth phase (always allowed, bonus if in high‐forage biome)
        List<Creature> survivors = new ArrayList<>(population);
        survivors.sort(Comparator.comparingInt(Creature::getAge));
        for (int i = 0; i < survivors.size(); i += JITTER_WINDOW) {
            int end = Math.min(i+JITTER_WINDOW, survivors.size());
            Collections.shuffle(survivors.subList(i,end), rng);
        }
        double eventBirthMod = events.getBirthModifier();
        for (int i = 0; i + 1 < survivors.size(); i += 2) {
            Creature a = survivors.get(i), b = survivors.get(i+1);
            if (a.getSex() != b.getSex()) {
                // base chance
                double chance = birthProbPerPair * eventBirthMod;
                //fitness modifier
                double fitA = a.getFitness(this) / 100.0;
                double fitB = b.getFitness(this) / 100.0;
                double pairFit = (fitA + fitB) / 2.0;       // [0.01…1.0]
                double fitnessScale = 1.0 + 0.5 * pairFit;  // [1.005…1.5]
                double fchance = chance * fitnessScale;
                // if both in a high‐forage biome (e.g. Oasis), boost chance
                Biome ba = getBiomeAt(a.getX(), a.getY());
                Biome bb = getBiomeAt(b.getX(), b.getY());
                if (ba.forageModifier > 1.0 && bb.forageModifier > 1.0) {
                    fchance *= FOOD_BREED_BONUS;
                }
                if (rng.nextDouble() < fchance) {
                    Creature child = a.mateWith(b);
                    // spawn child at parents' midpoint
                    int cx = (a.getX() + b.getX())/2;
                    int cy = (a.getY() + b.getY())/2;
                    child.setPosition(cx, cy);
                    population.add(child);
                    notifyMutation(child);
                }
            }
        }

        // 8) age all survivors
        population.forEach(Creature::stepAge);
    }
    /** Returns the next season in the cycle. */
    private Season nextSeason(Season s) {
        switch (s) {
            case SPRING: return Season.SUMMER;
            case SUMMER: return Season.FALL;
            case FALL:   return Season.WINTER;
            case WINTER: return Season.SPRING;
            default:     return Season.SPRING;
        }
    }
    
    /** Adjust each biome’s modifiers according to the current season. */
    private void applySeasonalModifiers() {
        for (Biome b : biomes) {
            switch (currentSeason) {
                case SPRING:
                    switch (b.name) {
                        case "Desert":
                            b.forageModifier = 0.3;
                            b.deathModifier  = 1.1;
                            break;
                        case "Oasis":
                            b.forageModifier = 2.0;
                            b.deathModifier  = 0.9;
                            break;
                        case "Plains":
                            b.forageModifier = 1.2;
                            b.deathModifier  = 0.8;
                            break;
                    }
                    break;
                case SUMMER:
                    switch (b.name) {
                        case "Desert":
                            b.forageModifier = 0.1;
                            b.deathModifier  = 1.5;
                            break;
                        case "Oasis":
                            b.forageModifier = 3.0;
                            b.deathModifier  = 0.7;
                            break;
                        case "Plains":
                            b.forageModifier = 1.0;
                            b.deathModifier  = 1.0;
                            break;
                    }
                    break;
                case FALL:
                    switch (b.name) {
                        case "Desert":
                            b.forageModifier = 0.2;
                            b.deathModifier  = 1.2;
                            break;
                        case "Oasis":
                            b.forageModifier = 1.75;
                            b.deathModifier  = 1.0;
                            break;
                        case "Plains":
                            b.forageModifier = 1.0;
                            b.deathModifier  = 0.9;
                            break;
                    }
                    break;
                case WINTER:
                    switch (b.name) {
                        case "Desert":
                            b.forageModifier = 0.05;
                            b.deathModifier  = 1.8;
                            break;
                        case "Oasis":
                            b.forageModifier = 1.5;
                            b.deathModifier  = 1.2;
                            break;
                        case "Plains":
                            b.forageModifier = 0.8;
                            b.deathModifier  = 1.3;
                            break;
                    }
                    break;
            }
        }
    }
    
    /**
     * Fills heightMap using 2D fractal Perlin noise. The result is in [0,1].
     * We sample at scaled coordinates to control “hill size.” 
     */
    private void generateHeightMap() {
        int octaves = 5;
        double persistence = 0.5;
        double scale = 0.005;  
        // scale < 0.01 → large, smooth hills; scale > 0.01 → smaller, bumpier hills

        for (int i = 0; i < WORLD_W; i++) {
            for (int j = 0; j < WORLD_H; j++) {
                // Evaluate fractal noise in [−1…+1]
                double nx = i * scale;
                double ny = j * scale;
                double val = PerlinNoise.fractal(nx, ny, octaves, persistence);

                // Map from [−1…+1] to [0…1]
                heightMap[i][j] = (val + 1) * 0.5;
            }
        }
    }

    /**
     * Returns a value in [0,1] at (x,y), where 0 = lowest elevation, 1 = highest.
     * Coordinates are clamped to [0..WORLD_W-1]×[0..WORLD_H-1].
     */
    public double getHeight(int x, int y) {
        int cx = Math.max(0, Math.min(WORLD_W - 1, x));
        int cy = Math.max(0, Math.min(WORLD_H - 1, y));
        return heightMap[cx][cy];
    }
    
    public Biome getBiomeAt(int px, int py) {
        for (Biome b : biomes) {
            if (b.contains(px, py)) {
                return b;
            }
        }
        return biomes.get(biomes.size() - 1);
    }
    
    public int getCurrentStep()           { return currentStep; }
    public List<Creature> getCreatures()  { return Collections.unmodifiableList(population); }
    public Events getEvents()             { return events; }
    public String getCurrentEventName()   { return events.getCurrentEventName(); }
    public List<Biome> getBiomes() 		  { return Collections.unmodifiableList(biomes);}
    public Season getCurrentSeason()       { return currentSeason; }
    
    public String getCreatureReport() {
        StringBuilder sb = new StringBuilder("Step ").append(currentStep).append("\n");
        population.forEach(c -> sb.append(c).append("\n"));
        return sb.toString();
    }
    private int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
