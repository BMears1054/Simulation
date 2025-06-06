package sim;

import javax.swing.SwingUtilities;

public class Main {
	private static Simulation simulation;

	public static Simulation getSimulationInstance() {
		return simulation;
	}

public static void main(String[] args) {

    	
    	
    	simulation = new Simulation(
            /* initialPop */      100,
            /* genomeLength */    10,
            /* deathProbPerStep */0.006,
            /* birthProbPerPair */0.025
        );
        SwingUtilities.invokeLater(() -> {
            SimulationGUI gui = new SimulationGUI();
            gui.setVisible(true);
        });
    }
}

//ideas to add:  Subject to change
//				
//						
//			
//				
//			



//				(Likely) Complex:
//			
//			Hunting/Gathering for food and other resources  partially done
//			
//			Add speed controls, let the user toggle between different speeds, and make the program not run all timesteps immediately. partially done
//				Unsure:
//			7 Advanced Visualization: 3D Rendering or Heatmaps
//			
//			5 Terrain Elevation & Movement Cost
//			6 External “Weather” or “Event” API Integration
//			add a more visual implementation of genetic mutation. So that we can see the mutations between generations. kinda done
//			8 Evolving Neural Architecture	
//			