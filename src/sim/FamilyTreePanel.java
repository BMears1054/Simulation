package sim;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;

public class FamilyTreePanel extends JPanel {
	 private final JTree tree;
	    private final DefaultTreeModel model;

	    public FamilyTreePanel() {
	        setLayout(new BorderLayout());
	        DefaultMutableTreeNode root = new DefaultMutableTreeNode("No creature selected");
	        model = new DefaultTreeModel(root);
	        tree  = new JTree(model);
	        add(new JScrollPane(tree), BorderLayout.CENTER);
	    }

	    /** Call this when a creature is selected */
	    public void showLineage(Creature c) {
	        DefaultMutableTreeNode root = buildNode(c);
	        model.setRoot(root);
	        tree.expandRow(0);
	    }

	    private DefaultMutableTreeNode buildNode(Creature c) {
	        DefaultMutableTreeNode node = new DefaultMutableTreeNode(c);
	        if (c.getParentA() != null) node.add(buildNode(c.getParentA()));
	        if (c.getParentB() != null) node.add(buildNode(c.getParentB()));
	        return node;
	    }
	}
