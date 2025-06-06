package sim;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class SimulationGUI extends JFrame {
    private static final int WINDOW_W = 800;
    private static final int WINDOW_H = 600;

    // Grab the Simulation instance from Main
    private Simulation simulation = Main.getSimulationInstance();
    private final MutationVisualizer viz = new MutationVisualizer();
    private SimulationPanel simPanel;
    private FamilyTreePanel familyPanel = new FamilyTreePanel();
    private final DefaultListModel<Creature> listModel = new DefaultListModel<>();
    private final JList<Creature> creatureList = new JList<>(listModel);
    private JTextArea infoArea;
    private JButton startBtn, pauseBtn, resetBtn, stepBtn;
    private JLabel stepLabel, seasonLabel;
    private Timer timer;

    // — Statistics chart (JFreeChart) —
    private XYSeries popSeries;     // series for population size
    private XYSeries fitSeries;     // series for average fitness
    private XYSeriesCollection dataset;
    private ChartPanel chartPanel;
    
    public SimulationGUI() {
        super("Creature Simulation GUI");

        // Tab 1: simulation view
        simPanel = new SimulationPanel();
        simPanel.setPreferredSize(new Dimension(WINDOW_W, WINDOW_H));
        
        // Tab 2: Family Tree view
       // familyPanel = new FamilyTreePanel();
        creatureList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        creatureList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    Creature sel = creatureList.getSelectedValue();
                    if (sel != null) {
                        familyPanel.showLineage(sel);
                    }
                }
            }
        });
        JScrollPane listScroll = new JScrollPane(creatureList);
        listScroll.setPreferredSize(new Dimension(200, WINDOW_H));
        JPanel familyTab = new JPanel(new BorderLayout());
        familyTab.add(listScroll, BorderLayout.WEST);
        familyTab.add(familyPanel, BorderLayout.CENTER);
        
        // Tab 3: textual info
        infoArea = new JTextArea();
        infoArea.setEditable(false);
        JScrollPane infoScroll = new JScrollPane(infoArea);

        // 4) Build the Stats tab (JFreeChart line chart)
        popSeries = new XYSeries("Population");
        fitSeries = new XYSeries("Avg. Fitness");
        dataset = new XYSeriesCollection();
        dataset.addSeries(popSeries);
        dataset.addSeries(fitSeries);

        JFreeChart lineChart = ChartFactory.createXYLineChart(
            "Population & Fitness Over Time",
            "Step",
            "Value",
            dataset,
            PlotOrientation.VERTICAL,
            true,   // legend
            false,  // tooltips
            false   // URLs
        );
        
        chartPanel = new ChartPanel(lineChart);
        chartPanel.setPreferredSize(new Dimension(WINDOW_W, WINDOW_H));
        
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Simulation", simPanel);
        tabs.addTab("Family-Tree", familyTab);
        tabs.addTab("Info", infoScroll);
        tabs.addTab("Stats",       chartPanel);

        // 5) Season label (displays current season)
        seasonLabel = new JLabel("Season: " + simulation.getCurrentSeason());
        seasonLabel.setFont(seasonLabel.getFont().deriveFont(Font.BOLD, 16f));
        
        // Controls
        startBtn = new JButton("Start");
        pauseBtn = new JButton("Pause");
        resetBtn = new JButton("Reset");
        stepBtn  = new JButton("Step");
        stepLabel = new JLabel("Step: 0");

        startBtn.addActionListener(e -> timer.start());
        pauseBtn.addActionListener(e -> timer.stop());
        resetBtn.addActionListener(e -> {
            timer.stop();
            simulation = new Simulation(150, 10, 0.006, 0.025);
            updateStepLabel();
            simPanel.repaint();
            updateInfoArea();
            simulation.addMutationListener(viz::registerMutation);
            refreshCreatureList();
            // Clear the chart series:
            popSeries.clear();
            fitSeries.clear();
        });
        stepBtn.addActionListener(e -> {
            simulation.run(1);
            updateStepLabel();
            simPanel.repaint();
            updateInfoArea();
            simulation.addMutationListener(viz::registerMutation);
            refreshCreatureList();
        });

        JPanel controls = new JPanel();
        controls.add(startBtn);
        controls.add(pauseBtn);
        controls.add(resetBtn);
        controls.add(stepBtn);
        controls.add(stepLabel);

        // Layout
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(tabs,     BorderLayout.CENTER);
        getContentPane().add(controls, BorderLayout.SOUTH);

        // Timer (~30 FPS)
        timer = new Timer(33, e -> {
        	simulation.addMutationListener(viz::registerMutation);
        	viz.tick(); 
        	simulation.run(1);
            updateStepLabel();
            simPanel.repaint();
            updateInfoArea();
            refreshCreatureList();
            
            // Append new data point to the chart
            int step = simulation.getCurrentStep();
            int popSize = simulation.getCreatures().size();
            popSeries.add(step, popSize);

            // Compute average fitness
            double sumFit = 0;
            for (Creature c : simulation.getCreatures()) {
                sumFit += c.getFitness(simulation);
            }
            double avgFit = simulation.getCreatures().isEmpty() ? 0 : sumFit / simulation.getCreatures().size();
            fitSeries.add(step, avgFit);
        
        });

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        refreshCreatureList();
        updateInfoArea();
        
    }

    private void refreshCreatureList() {
        listModel.clear();
        for (Creature c : simulation.getCreatures()) {
            listModel.addElement(c);
        }
        if (!listModel.isEmpty()) {
            creatureList.setSelectedIndex(0);
        }
    }
    
    private void updateStepLabel() {
        stepLabel.setText("Step: " + simulation.getCurrentStep());
    }

    /** Update season display. */
    private void updateSeasonLabel() {
        seasonLabel.setText("Season: " + simulation.getCurrentSeason());
    }
    
    private void updateInfoArea() {
        StringBuilder sb = new StringBuilder();
        sb.append("Step ").append(simulation.getCurrentStep()).append("\n\n");
        sb.append("Season: ").append(simulation.getCurrentSeason()).append("\n\n");
        sb.append("Biomes:\n");
        for (Biome b : simulation.getBiomes()) {
        	 long count = simulation.getCreatures().stream()
        	            .filter(c -> simulation.getBiomeAt(c.getX(), c.getY()) == b)
        	            .count();
        	        sb.append(String.format(
        	            "  %s @(%d,%d %dx%d) — forage×%.2f, death×%.2f, creatures=%d%n",
        	            b.name, b.x, b.y, b.width, b.height,
        	            b.forageModifier, b.deathModifier, count
        	        ));
        	    }
        
        sb.append("\nCreatures:\n");
        for (Creature c : simulation.getCreatures()) {
        	double f = c.getFitness(simulation);
            sb.append(String.format("  #%d: (%d,%d) Age:%d Hunger:%d%n Fitness:%.1f%n",
                         simulation.getCreatures().indexOf(c),
                         c.getX(), c.getY(), c.getAge(), /*c.getHunger()*/0, f));
        }
        infoArea.setText(sb.toString());
        infoArea.setCaretPosition(0);
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimulationGUI().setVisible(true));
    }

    /**
     * Panel that draws food patches and creatures in 2D space.
     */
    private class SimulationPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Simulation sim = simulation; // from outer class

           
            // 1) Draw the Plains base (always full size)
            g.setColor(new Color(173, 216, 230));
            g.fillRect(0, 0, WINDOW_W, WINDOW_H);

            // 2) Draw the other biomes (Desert and Oasis) on top
            for (Biome b : simulation.getBiomes()) {
                if (b.name.equals("Plains")) continue;  // skip, already drawn
            // Draw biomes
                Color base;
                switch (b.name) {
                    case "Desert": base = new Color(236, 182, 142); break;
                    case "Oasis":  base = new Color(9, 148, 9); break;
                    case "Plains": base = new Color(2, 94, 123); break;
                    default:       base = Color.LIGHT_GRAY;          break;
                }
                Color fill = seasonTint(base, simulation.getCurrentSeason());
                g.setColor(fill);
                g.fillRect(b.x, b.y, b.width, b.height);
            }
            
            // 1) Draw elevation as a semi‐transparent grayscale overlay
            Graphics2D g2 = (Graphics2D) g.create();
            // We’ll sample every 10×10 pixels to lighten the drawing load:
            int step = 1;
            for (int i = 0; i < Simulation.WORLD_W; i += step) {
                for (int j = 0; j < Simulation.WORLD_H; j += step) {
                    double h = sim.getHeight(i, j); // [0…1]
                    // Map to alpha in [0…100] (0 = no overlay, 100 = darkest)
                    int alpha = (int) (h * 200);
                    alpha = Math.min(200, Math.max(0, alpha));
                    g2.setColor(new Color(0, 0, 0, alpha));
                    g2.fillRect(i, j, step, step);
                }
            }
            g2.dispose();

            // Draw creatures
            for (Creature c : simulation.getCreatures()) {
                float ageRatio = Math.min(1f, c.getAge() / 100f);
                float hue = 0.33f * (1f - ageRatio);
                // get possibly‐highlighted color
                Color baseColor = Color.getHSBColor(hue, 1f, 1f);
                Color col = viz.getColor(c, baseColor);
                
                g.setColor(col);
                g.fillOval(c.getX() - 5, c.getY() - 5, 10, 10);
            }

            // Draw current event
            g.setColor(Color.BLACK);
            g.drawString("Event: " + simulation.getCurrentEventName(),
                         20, getHeight() - 20);
        }

    }
    /**
     * Returns a seasonally‐tinted version of the given color:
     * - In SUMMER → brighten by 15%
     * - In WINTER → darken by 25%
     * - In SPRING/FALL → no change
     */
    private Color seasonTint(Color c, Simulation.Season season) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        switch (season) {
            case SUMMER:
                // Increase brightness up to 1.0
                hsb[2] = Math.min(1f, hsb[2] * 1.15f);
                break;
            case WINTER:
                // Decrease brightness
                hsb[2] = Math.max(0f, hsb[2] * 0.75f);
                break;
            default:
                // SPRING/FALL: no change
                break;
        }
        return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }
}


