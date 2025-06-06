package sim;

import java.util.Random;
import java.util.function.DoubleUnaryOperator;

public class NeuralNet {
	/**
	 * A simple feed‐forward multilayer perceptron with:
	 *  - variable number of layers and neurons per layer
	 *  - tanh activations in hidden layers, linear outputs
	 *  - per‐weight crossover and Gaussian‐noise mutation
	 */

	    private final int[] layerSizes;      // e.g. {5, 8, 2}
	    private final double[][][] weights;  // weights[layer][i][j]
	    private static final Random RNG = new Random();
	    private final double[][] activations;  // newly added: stores per‐layer activations
	    /**
	     * @param layerSizes full network architecture (inputs, hidden…, outputs)
	     */
	    public NeuralNet(int... layerSizes) {
	        this.layerSizes = layerSizes;
	        this.weights    = new double[layerSizes.length - 1][][];
	        this.activations = new double[layerSizes.length][];
	        for (int L = 0; L < weights.length; L++) {
	            int inN  = layerSizes[L], outN = layerSizes[L+1];
	            weights[L] = new double[inN][outN];
	            for (int i = 0; i < inN; i++)
	                for (int j = 0; j < outN; j++)
	                    weights[L][i][j] = RNG.nextGaussian() * 0.5;
	        }
	    }

	    /**
	     * Forward‐propagate an input vector through the network.
	     * Uses tanh for hidden layers and linear output.
	     *
	     * @param input length must equal layerSizes[0]
	     * @return      array of length layerSizes[last]
	     */
	    public double[] forward(double[] input) {
	        activations[0] = input.clone();
	        double[] act = input;
	        for (int L = 0; L < weights.length; L++) {
	            double[] next = new double[layerSizes[L+1]];
	            for (int j = 0; j < next.length; j++) {
	                double sum = 0;
	                for (int i = 0; i < act.length; i++)
	                    sum += act[i] * weights[L][i][j];
	                next[j] = (L < weights.length - 1) ? Math.tanh(sum) : sum;
	            }
	            activations[L+1] = next;
	            act = next;
	        }
	        return act.clone();
	    }

	    public void addInputBias(int inputIndex, double delta) {
	        double[][] firstLayer = weights[0];
	        for (int j = 0; j < firstLayer[inputIndex].length; j++) {
	            firstLayer[inputIndex][j] += delta;
	        }
	    }
	    
	    /**
	     * Apply a simple delta‐rule update using the last activations:
	     * Δw = rate * reward * preActivation * postActivation
	     *
	     * @param rate    learning‐rate multiplier
	     * @param reward  scalar reward signal (positive or negative)
	     */
	    public void reward(double rate, double reward) {
	        for (int L = 0; L < weights.length; L++) {
	            double[] pre  = activations[L];
	            double[] post = activations[L + 1];
	            for (int i = 0; i < pre.length; i++) {
	                for (int j = 0; j < post.length; j++) {
	                    weights[L][i][j] += rate * reward * pre[i] * post[j];
	                }
	            }
	        }
	    }
	    
	    /**
	     * Create a child net by 50/50 per‐weight crossover from this and other.
	     */
	    public NeuralNet crossover(NeuralNet other) {
	        NeuralNet child = new NeuralNet(layerSizes);
	        for (int L = 0; L < weights.length; L++) {
	            for (int i = 0; i < weights[L].length; i++) {
	                for (int j = 0; j < weights[L][i].length; j++) {
	                    child.weights[L][i][j] =
	                        RNG.nextBoolean()
	                        ? this.weights[L][i][j]
	                        : other.weights[L][i][j];
	                }
	            }
	        }
	        return child;
	    }

	    /**
	     * Mutate the net by adding Gaussian noise to each weight with given probability.
	     *
	     * @param rate      chance per weight to mutate
	     * @param magnitude standard deviation of added Gaussian noise
	     */
	    public void mutate(double rate, double magnitude) {
	        for (int L = 0; L < weights.length; L++) {
	            for (int i = 0; i < weights[L].length; i++) {
	                for (int j = 0; j < weights[L][i].length; j++) {
	                    if (RNG.nextDouble() < rate) {
	                        weights[L][i][j] += RNG.nextGaussian() * magnitude;
	                    }
	                }
	            }
	        }
	    }
	}