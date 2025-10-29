package test.java.general;

import main.java.general.DRGizmoGUI;
import main.java.general.DRGizmoMain;
import main.java.general.DRGroupData;
import org.junit.jupiter.api.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.tree.*;

class DRGizmoGUITest {
    private DRGizmoGUI gui;

    @BeforeEach
    void setUp() {
        DRGizmoMain.prefs = java.util.prefs.Preferences.userRoot().node("testNode");
        gui = new DRGizmoGUI(new JFrame());
    }

    @AfterEach
    void tearDown() throws Exception {
        DRGizmoMain.prefs.removeNode();
    }

    @Test
    void testInitTreeModelAndRootNode() {
        Assertions.assertNotNull(gui.drTreeModel);
        Assertions.assertNotNull(gui.drTreeRootNode);
    }

    @Test
    void testAddDRToTreeIncreasesCount() {
        int initialCount = gui.drTreeRootNode.getChildCount();

        gui.addDRToTree(new DRGroupData());

        Assertions.assertTrue(gui.drTreeRootNode.getChildCount() > initialCount);
    }

    @Test
    void testRemoveButtonExists() {
        Assertions.assertNotNull(gui.buttonDRTreeRemove);
    }

    @Test
    void testMultiSelectAndRemove() {
        // Setup: Add 3 nodes for testing
        for (int i = 0; i < 3; i++) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode("Node " + i);
            gui.drTreeModel.insertNodeInto(node, gui.drTreeRootNode, gui.drTreeRootNode.getChildCount());
        }

        // Select first and third nodes
        DefaultMutableTreeNode node0 = (DefaultMutableTreeNode) gui.drTreeRootNode.getChildAt(0);
        DefaultMutableTreeNode node2 = (DefaultMutableTreeNode) gui.drTreeRootNode.getChildAt(2);

        TreePath path0 = new TreePath(node0.getPath());
        TreePath path2 = new TreePath(node2.getPath());

        gui.drTree.setSelectionPaths(new TreePath[] { path0, path2 });

        // Simulate remove button press
        gui.buttonDRTreeRemove.doClick();

        // Assert only the second node remains
        Assertions.assertEquals(1, gui.drTreeRootNode.getChildCount());
        Assertions.assertEquals("Node 1", gui.drTreeRootNode.getChildAt(0).toString());
    }

}