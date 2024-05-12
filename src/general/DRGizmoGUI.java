package general;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Random;
import java.util.prefs.BackingStoreException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.TitledBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.NumberFormatter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import prefobj.PrefObj;

public class DRGizmoGUI extends JFrame implements ActionListener{

	private Window window;

	public static final String[] OCEANS = new String[] {"Cerulean", "Emerald", "Meridian", "Obsidian", "No Ocean"};

	public static final String AUTOCOPY = "autocopy", AUTOREAD = "autoread", BROKENRECORDALERT = "brokenrecordalert",
			DEBUG = "debug", IGNOREAI = "ignoreai",

			SCANWRITEDATE = "scanwritedate", SCANUSETABS = "scanusetabs", SCANOCEAN = "scanocean",
			SCANSTATIONHEADERS = "scanstationheaders", SCANSTATION = "scanstation", SCANPIRATE = "scanpirate",
			SCANRATING = "scanrating", SCANTOKENSCORE = "scantokenscore", SCANTOKENARRAY = "scantokenarray",

			SCOREWRITEDATE = "scorewritedate", SCOREUSETABS = "scoreusetabs", SCOREOCEAN = "scoreocean",
			SCORESTATIONHEADERS = "scorestationheaders", SCORESTATION = "scorestation", SCOREPIRATE = "scorepirate",
			SCORERATING = "scorerating", SCORETOKENSCORE = "scoretokenscore", SCORETOKENARRAY = "scoretokenarray",

			TOKENCIRCLE = "tokencircle", TOKENDIAMOND = "tokendiamond", TOKENPLUS = "tokenplus",
			TOKENCROSS = "tokencross", TOKENTHRALL = "tokenthrall",
			TOKENCANNONBALL = "tokencannonball",
			CHESTSMALL = "chestsmall", CHESTMEDIUM = "chestmedium", CHESTLARGE = "chestlarge",

			CCENTRYINDIVIDUAL = "ccentryindividual",
			CCENTRY7 = "ccentry7", CCENTRY6 = "ccentry6", CCENTRY5 = "ccentry5", CCENTRY4 = "ccentry4",
			CCENTRY3 = "ccentry3", CCENTRY2 = "ccentry2", CCENTRY1 = "ccentry1",

			CCFORAGEINDIVIDUAL = "ccforageindividual",
			CCFORAGE7 = "ccforage7", CCFORAGE6 = "ccforage6", CCFORAGE5 = "ccforage5", CCFORAGE4 = "ccforage4",
			CCFORAGE3 = "ccforage3", CCFORAGE2 = "ccforage2", CCFORAGE1 = "ccforage1",

			TREEDATA = "treedata";


	private static JTextArea textAreaOutput, textAreaTests;
	private JButton buttonOutputClear, buttonDRTreeRemove, buttonDRTreeClear, buttonSavePrefs, buttonScan, buttonScore, buttonTestsWho, buttonTestsBootyDiv, buttonTestsClear, buttonOutputAvg, buttonOutputCC;
	private JCheckBox checkBoxAutoCopy, checkBoxAutoRead, checkBoxBrokenRecordAlert;
	private JCheckBox checkBoxDebug, checkBoxIgnoreAI;
	private JLabel labelDRCount;

	private JCheckBox checkBoxScanWriteDate, checkBoxScanUseTabs, checkBoxScanStationHeaders, checkBoxScan2Station, checkBoxScan2Pirate, checkBoxScan2Rating, checkBoxScan2TokenScore, checkBoxScan2TokenArray;
	private JComboBox comboBoxScanOcean;

	private JCheckBox checkBoxScoreWriteDate, checkBoxScoreUseTabs, checkBoxScoreStationHeaders, checkBoxScore2Station, checkBoxScore2Pirate, checkBoxScore2Rating, checkBoxScore2TokenScore, checkBoxScore2TokenArray;
	private JComboBox comboBoxScoreOcean;

	private JTree drTree;
	private DefaultMutableTreeNode drTreeRootNode;
	private DefaultTreeModel drTreeModel;

	private JFormattedTextField formattedTextFieldTokenCircle, formattedTextFieldTokenDiamond, formattedTextFieldTokenPlus, formattedTextFieldTokenCross, formattedTextFieldTokenThrall;
	private JFormattedTextField formattedTextFieldCannonball;
	private JFormattedTextField formattedTextFieldSmallChest, formattedTextFieldMediumChest, formattedTextFieldLargeChest;
	private JFormattedTextField formattedTextFieldEntryIndividual, formattedTextFieldEntry7, formattedTextFieldEntry6,
		formattedTextFieldEntry5, formattedTextFieldEntry4, formattedTextFieldEntry3, formattedTextFieldEntry2, formattedTextFieldEntry1;
	private JFormattedTextField formattedTextFieldForageIndividual, formattedTextFieldForage7, formattedTextFieldForage6,
	formattedTextFieldForage5, formattedTextFieldForage4, formattedTextFieldForage3, formattedTextFieldForage2, formattedTextFieldForage1;

	public void printStatus(String text, boolean isDebug) {
		if (textAreaOutput != null && checkBoxDebug != null) {
			if (!(isDebug) ||  ((isDebug) && (checkBoxDebug.isSelected()))) {
				textAreaOutput.append(text + System.getProperty("line.separator"));
				textAreaOutput.setCaretPosition(textAreaOutput.getText().length());
			}
		}
	}

	public void printTestStatus(String text, boolean isDebug) {
		if (textAreaTests != null && checkBoxDebug != null) {
			if (!(isDebug) ||  ((isDebug) && (checkBoxDebug.isSelected()))) {
				textAreaTests.append(text + System.getProperty("line.separator"));
				textAreaTests.setCaretPosition(textAreaTests.getText().length());
			}
		}
	}


	public DRGizmoGUI(Window window) {

		this.window = window;


		setTitle("Gizmo the Magic Counter v" + DRGizmoMain.VERSION + "");

		getContentPane().setBounds(100, 100, 250, 628);
		getContentPane().setPreferredSize(new Dimension(250, 628));
		getContentPane().setLayout(new BorderLayout(0, 0));


		NumberFormat format = NumberFormat.getIntegerInstance();
		//format.setGroupingUsed(false);
	    NumberFormatter integerFormatter = new NumberFormatter(format);
	    integerFormatter.setValueClass(Integer.class);
	    integerFormatter.setMinimum(0);
	    integerFormatter.setMaximum(Integer.MAX_VALUE);
	    integerFormatter.setAllowsInvalid(true);


		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		getContentPane().add(tabbedPane, BorderLayout.CENTER);

		JPanel panelOutput = new JPanel();
		panelOutput.setDoubleBuffered(false);
		tabbedPane.addTab("Output", null, panelOutput, null);
		panelOutput.setLayout(new BorderLayout(0, 0));

			JScrollPane scrollPaneOutput = new JScrollPane();
			panelOutput.add(scrollPaneOutput, BorderLayout.CENTER);






			LookAndFeel yoLookAndFeel = UIManager.getLookAndFeel();
			try {
				UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
			} catch (ClassNotFoundException e) {
			} catch (InstantiationException e) {
			} catch (IllegalAccessException e) {
			} catch (UnsupportedLookAndFeelException e) {}

			//CHEEZUS VUCKING CHRISTMAS I CAN"T SET THIS FUCIKING BACKNGOURD TO ANYTHING BUT WHITE UNLESS I CHANGETH E LOOK AND FEEL TO SOMETHING ELSE
			textAreaOutput = new JTextArea();
			textAreaOutput.setBackground(new Color(169, 185, 193)); //highlighted dr color
			//textAreaOutput.setBackground(new Color(140, 162, 172)); //non-highlighted dr color
			textAreaOutput.addCaretListener(new CaretListener() {
				public void caretUpdate(CaretEvent arg0) {
					repaint();
				}
			});
			scrollPaneOutput.setViewportView(textAreaOutput);




			/*
			JScrollPane scrollPaneTests = new JScrollPane();

			//CHEEZUS VUCKING CHRISTMAS I CAN"T SET THIS FUCIKING BACKNGOURD TO ANYTHING BUT WHITE UNLESS I CHANGETH E LOOK AND FEEL TO SOMETHING ELSE
			textAreaTests = new JTextArea();
			textAreaTests.setBackground(new Color(50, 50, 50)); //highlighted dr color
			//textAreaOutput.setBackground(new Color(140, 162, 172)); //non-highlighted dr color
			textAreaTests.addCaretListener(new CaretListener() {
				public void caretUpdate(CaretEvent arg0) {
					repaint();
				}
			});
			scrollPaneTests.setViewportView(textAreaTests);
			 */




			try {
				UIManager.setLookAndFeel(yoLookAndFeel);
			} catch (UnsupportedLookAndFeelException e) {}

			JPanel panelOutputButtonPanel = new JPanel();
			panelOutput.add(panelOutputButtonPanel, BorderLayout.SOUTH);
			panelOutputButtonPanel.setLayout(new BoxLayout(panelOutputButtonPanel, BoxLayout.X_AXIS));

			Component hGlue_1 = Box.createHorizontalGlue();
			panelOutputButtonPanel.add(hGlue_1);

			buttonOutputAvg = new JButton("Averages");
			buttonOutputAvg.addActionListener(this);
			buttonOutputAvg.setPreferredSize(new Dimension(80, 23));
			panelOutputButtonPanel.add(buttonOutputAvg);

			buttonOutputCC = new JButton("CC Totals");
			buttonOutputCC.addActionListener(this);
			buttonOutputCC.setPreferredSize(new Dimension(80, 23));
			panelOutputButtonPanel.add(buttonOutputCC);

			buttonOutputClear = new JButton("Clear");
			buttonOutputClear.addActionListener(this);
			buttonOutputClear.setPreferredSize(new Dimension(80, 23));
			panelOutputButtonPanel.add(buttonOutputClear);




			JPanel panelDRTree = new JPanel();
			tabbedPane.addTab("DR Tree", null, panelDRTree, null);
			panelDRTree.setLayout(new BorderLayout(0, 0));

			JScrollPane scrollPaneDRTree = new JScrollPane();
			panelDRTree.add(scrollPaneDRTree, BorderLayout.CENTER);




			drTreeRootNode = new DefaultMutableTreeNode("Root Node");
			drTreeModel = new DefaultTreeModel(drTreeRootNode);

			drTree = new JTree();
			//drTree.setBackground(new Color(197, 199, 174)); //same color as tabs
			drTree.setBackground(new Color(169, 185, 193)); //highlighted dr color
			//drTree.setBackground(new Color(140, 162, 172)); //non-highlighted dr color

			//set getBackgroundNonSelectionColor for drTree... wow
			drTree.setCellRenderer(new DefaultTreeCellRenderer() {

	        	@Override
	            public Color getBackgroundNonSelectionColor() {
	                return (this.getBackground());
	            }
				@Override
				public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel, final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
			        final Component ret = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			        this.setText(value.toString());
			        return ret;
			    }
	        });

			drTree.setShowsRootHandles(true);
			drTree.setRootVisible(false);
			drTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
			drTree.setModel(drTreeModel);
			//populate dr tree
			try {
				//TODO make sure this works
				DRPersistantData tempDRPersistantData = (DRPersistantData) PrefObj.getObject(DRGizmoMain.prefs, TREEDATA);
				for (DRGroupData group : tempDRPersistantData.getAll()) {
					addDRToTree(group);
				}
				printStatus("Tree Loaded", true);
			} catch (IOException e) {
				printStatus("Loading Tree Failed: IO", true);
			} catch (BackingStoreException e) {
				printStatus("Loading Tree Failed: BSE", true);
			} catch (ClassNotFoundException e) {
				printStatus("Loading Tree Failed: CNFE", true);
			} catch (Exception e) {
				printStatus("Loading Tree Failed: any Exception", true);
			}
			scrollPaneDRTree.setViewportView(drTree);

			JPanel panelDRTreeButtonPanel = new JPanel();
			panelDRTree.add(panelDRTreeButtonPanel, BorderLayout.SOUTH);
			panelDRTreeButtonPanel.setLayout(new BoxLayout(panelDRTreeButtonPanel, BoxLayout.X_AXIS));

			Component hGlue_2 = Box.createHorizontalGlue();
			panelDRTreeButtonPanel.add(hGlue_2);

			buttonDRTreeRemove = new JButton("Remove");
			buttonDRTreeRemove.addActionListener(this);
			buttonDRTreeRemove.setPreferredSize(new Dimension(80, 23));
			panelDRTreeButtonPanel.add(buttonDRTreeRemove);

			buttonDRTreeClear = new JButton("Clear");
			buttonDRTreeClear.addActionListener(this);
			buttonDRTreeClear.setPreferredSize(new Dimension(80, 23));
			panelDRTreeButtonPanel.add(buttonDRTreeClear);



		JPanel panelOptions = new JPanel();
		tabbedPane.addTab("Options", null, panelOptions, null);
		panelOptions.setLayout(new BorderLayout(0, 0));

			JScrollPane scrollPaneOptions = new JScrollPane();
			panelOptions.add(scrollPaneOptions, BorderLayout.CENTER);

			JPanel panelOptions_2 = new JPanel();
			scrollPaneOptions.setViewportView(panelOptions_2);
			panelOptions_2.setLayout(new BoxLayout(panelOptions_2, BoxLayout.Y_AXIS));

			JPanel panelOptionsGeneral = new JPanel();
			panelOptionsGeneral.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelOptions_2.add(panelOptionsGeneral);
			panelOptionsGeneral.setLayout(new BoxLayout(panelOptionsGeneral, BoxLayout.Y_AXIS));

				buttonSavePrefs = new JButton("Save Prefs");
				buttonSavePrefs.setToolTipText("Also saves window height and width");
				buttonSavePrefs.addActionListener(this);
				panelOptionsGeneral.add(buttonSavePrefs);

				checkBoxDebug = new JCheckBox("Debug");
				checkBoxDebug.setSelected(DRGizmoMain.prefs.getBoolean(DEBUG, false));
				panelOptionsGeneral.add(checkBoxDebug);

				checkBoxIgnoreAI = new JCheckBox("Ignore AI");
				checkBoxIgnoreAI.setSelected(DRGizmoMain.prefs.getBoolean(IGNOREAI, false));
				panelOptionsGeneral.add(checkBoxIgnoreAI);

			JPanel panelOptionsPrintOptions = new JPanel();
			panelOptionsPrintOptions.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelOptions_2.add(panelOptionsPrintOptions);
			panelOptionsPrintOptions.setLayout(new BoxLayout(panelOptionsPrintOptions, BoxLayout.X_AXIS));

				JPanel panelPrintOptionsScan = new JPanel();
				panelPrintOptionsScan.setBorder(new TitledBorder(null, "Scanning", TitledBorder.LEADING, TitledBorder.TOP, null, null));
				panelOptionsPrintOptions.add(panelPrintOptionsScan);
				panelPrintOptionsScan.setLayout(new BoxLayout(panelPrintOptionsScan, BoxLayout.Y_AXIS));

					checkBoxScanWriteDate = new JCheckBox("Write Date");
					checkBoxScanWriteDate.setSelected(DRGizmoMain.prefs.getBoolean(SCANWRITEDATE, false));
					panelPrintOptionsScan.add(checkBoxScanWriteDate);

					checkBoxScanUseTabs = new JCheckBox("Use Tabs");
					checkBoxScanUseTabs.setSelected(DRGizmoMain.prefs.getBoolean(SCANUSETABS, false));
					panelPrintOptionsScan.add(checkBoxScanUseTabs);

					comboBoxScanOcean = new JComboBox();
					comboBoxScanOcean.setAlignmentX(Component.LEFT_ALIGNMENT);
					panelPrintOptionsScan.add(comboBoxScanOcean);
					comboBoxScanOcean.setModel(new DefaultComboBoxModel(OCEANS));
					comboBoxScanOcean.setSelectedIndex(DRGizmoMain.prefs.getInt(SCANOCEAN, 3));
					comboBoxScanOcean.setMaximumSize(new Dimension(120, 23));

					checkBoxScanStationHeaders = new JCheckBox("Station Headers");
					checkBoxScanStationHeaders.setSelected(DRGizmoMain.prefs.getBoolean(SCANSTATIONHEADERS, false));
					panelPrintOptionsScan.add(checkBoxScanStationHeaders);

					JPanel panelPrintOptionsScan_2 = new JPanel();
					panelPrintOptionsScan_2.setBorder(new TitledBorder(null, "IDK What to", TitledBorder.LEADING, TitledBorder.TOP, null, null));
					panelPrintOptionsScan.add(panelPrintOptionsScan_2);
					panelPrintOptionsScan_2.setLayout(new BoxLayout(panelPrintOptionsScan_2, BoxLayout.Y_AXIS));

					checkBoxScan2Station = new JCheckBox("Station");
					checkBoxScan2Station.setSelected(DRGizmoMain.prefs.getBoolean(SCANSTATION, false));
					panelPrintOptionsScan_2.add(checkBoxScan2Station);

					checkBoxScan2Pirate = new JCheckBox("Pirate");
					checkBoxScan2Pirate.setSelected(DRGizmoMain.prefs.getBoolean(SCANPIRATE, false));
					panelPrintOptionsScan_2.add(checkBoxScan2Pirate);

					checkBoxScan2Rating = new JCheckBox("Rating");
					checkBoxScan2Rating.setSelected(DRGizmoMain.prefs.getBoolean(SCANRATING, false));
					panelPrintOptionsScan_2.add(checkBoxScan2Rating);

					checkBoxScan2TokenScore = new JCheckBox("Token Score");
					checkBoxScan2TokenScore.setSelected(DRGizmoMain.prefs.getBoolean(SCANTOKENSCORE, false));
					panelPrintOptionsScan_2.add(checkBoxScan2TokenScore);

					checkBoxScan2TokenArray = new JCheckBox("Token Array");
					checkBoxScan2TokenArray.setSelected(DRGizmoMain.prefs.getBoolean(SCANTOKENARRAY, false));
					panelPrintOptionsScan_2.add(checkBoxScan2TokenArray);

				JPanel panelPrintOptionsScore = new JPanel();
				panelPrintOptionsScore.setBorder(new TitledBorder(null, "Scoring", TitledBorder.LEADING, TitledBorder.TOP, null, null));
				panelOptionsPrintOptions.add(panelPrintOptionsScore);
				panelPrintOptionsScore.setLayout(new BoxLayout(panelPrintOptionsScore, BoxLayout.Y_AXIS));

					checkBoxScoreWriteDate = new JCheckBox("Write Date");
					checkBoxScoreWriteDate.setSelected(DRGizmoMain.prefs.getBoolean(SCOREWRITEDATE, false));
					panelPrintOptionsScore.add(checkBoxScoreWriteDate);

					checkBoxScoreUseTabs = new JCheckBox("Use Tabs");
					checkBoxScoreUseTabs.setSelected(DRGizmoMain.prefs.getBoolean(SCOREUSETABS, false));
					panelPrintOptionsScore.add(checkBoxScoreUseTabs);

					comboBoxScoreOcean = new JComboBox();
					comboBoxScoreOcean.setAlignmentX(Component.LEFT_ALIGNMENT);
					comboBoxScoreOcean.setModel(new DefaultComboBoxModel(OCEANS));
					comboBoxScoreOcean.setSelectedIndex(DRGizmoMain.prefs.getInt(SCOREOCEAN, 3));
					comboBoxScoreOcean.setMaximumSize(new Dimension(120, 23));
					panelPrintOptionsScore.add(comboBoxScoreOcean);

					checkBoxScoreStationHeaders = new JCheckBox("Station Headers");
					checkBoxScoreStationHeaders.setSelected(DRGizmoMain.prefs.getBoolean(SCORESTATIONHEADERS, false));
					panelPrintOptionsScore.add(checkBoxScoreStationHeaders);

					JPanel panePrintOptionsScore_2 = new JPanel();
					panePrintOptionsScore_2.setBorder(new TitledBorder(null, "Name These", TitledBorder.LEADING, TitledBorder.TOP, null, null));
					panelPrintOptionsScore.add(panePrintOptionsScore_2);
					panePrintOptionsScore_2.setLayout(new BoxLayout(panePrintOptionsScore_2, BoxLayout.Y_AXIS));

					checkBoxScore2Station = new JCheckBox("Station");
					checkBoxScore2Station.setSelected(DRGizmoMain.prefs.getBoolean(SCORESTATION, false));
					panePrintOptionsScore_2.add(checkBoxScore2Station);

					checkBoxScore2Pirate = new JCheckBox("Pirate");
					checkBoxScore2Pirate.setSelected(DRGizmoMain.prefs.getBoolean(SCOREPIRATE, false));
					panePrintOptionsScore_2.add(checkBoxScore2Pirate);

					checkBoxScore2Rating = new JCheckBox("Rating");
					checkBoxScore2Rating.setSelected(DRGizmoMain.prefs.getBoolean(SCORERATING, false));
					panePrintOptionsScore_2.add(checkBoxScore2Rating);

					checkBoxScore2TokenScore = new JCheckBox("Token Score");
					checkBoxScore2TokenScore.setSelected(DRGizmoMain.prefs.getBoolean(SCORETOKENSCORE, false));
					panePrintOptionsScore_2.add(checkBoxScore2TokenScore);

					checkBoxScore2TokenArray = new JCheckBox("Token Array");
					checkBoxScore2TokenArray.setSelected(DRGizmoMain.prefs.getBoolean(SCORETOKENARRAY, false));
					panePrintOptionsScore_2.add(checkBoxScore2TokenArray);



			JPanel panelOptionsTokenAndChestValues = new JPanel();
			panelOptionsTokenAndChestValues.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelOptionsTokenAndChestValues.setBorder(new TitledBorder(null, "Token/Chest Values", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelOptions_2.add(panelOptionsTokenAndChestValues);
			panelOptionsTokenAndChestValues.setLayout(new BoxLayout(panelOptionsTokenAndChestValues, BoxLayout.X_AXIS));

			JPanel panelTokenValues = new JPanel();
			panelTokenValues.setBorder(new TitledBorder(null, "Tokens", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelOptionsTokenAndChestValues.add(panelTokenValues);
			panelTokenValues.setLayout(new BoxLayout(panelTokenValues, BoxLayout.Y_AXIS));

				Box horizontalBoxTokenCircle = Box.createHorizontalBox();
				horizontalBoxTokenCircle.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelTokenValues.add(horizontalBoxTokenCircle);

				JLabel labelTokenCircle = new JLabel("Circle");
				labelTokenCircle.setMinimumSize(new Dimension(60, 23));
				labelTokenCircle.setMaximumSize(new Dimension(60, 23));
				horizontalBoxTokenCircle.add(labelTokenCircle);

				formattedTextFieldTokenCircle = new JFormattedTextField(integerFormatter);
				formattedTextFieldTokenCircle.setValue(DRGizmoMain.prefs.getInt(TOKENCIRCLE, 1));
				formattedTextFieldTokenCircle.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldTokenCircle.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldTokenCircle.setMaximumSize(new Dimension(30, 20));
				horizontalBoxTokenCircle.add(formattedTextFieldTokenCircle);

				Box horizontalBoxTokenDiamond = Box.createHorizontalBox();
				horizontalBoxTokenDiamond.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelTokenValues.add(horizontalBoxTokenDiamond);

				JLabel labelTokenDiamond = new JLabel("Diamond");
				labelTokenDiamond.setMinimumSize(new Dimension(60, 23));
				labelTokenDiamond.setMaximumSize(new Dimension(60, 23));
				horizontalBoxTokenDiamond.add(labelTokenDiamond);

				formattedTextFieldTokenDiamond = new JFormattedTextField(integerFormatter);
				formattedTextFieldTokenDiamond.setValue(DRGizmoMain.prefs.getInt(TOKENDIAMOND, 1));
				formattedTextFieldTokenDiamond.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldTokenDiamond.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldTokenDiamond.setMaximumSize(new Dimension(30, 20));
				horizontalBoxTokenDiamond.add(formattedTextFieldTokenDiamond);

				Box horizontalBoxTokenPlus = Box.createHorizontalBox();
				horizontalBoxTokenPlus.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelTokenValues.add(horizontalBoxTokenPlus);

				JLabel labelTokenPlus = new JLabel("Plus");
				labelTokenPlus.setMinimumSize(new Dimension(60, 23));
				labelTokenPlus.setMaximumSize(new Dimension(60, 23));
				horizontalBoxTokenPlus.add(labelTokenPlus);

				formattedTextFieldTokenPlus = new JFormattedTextField(integerFormatter);
				formattedTextFieldTokenPlus.setValue(DRGizmoMain.prefs.getInt(TOKENPLUS, 1));
				formattedTextFieldTokenPlus.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldTokenPlus.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldTokenPlus.setMaximumSize(new Dimension(30, 20));
				horizontalBoxTokenPlus.add(formattedTextFieldTokenPlus);

				Box horizontalBoxTokenCross = Box.createHorizontalBox();
				horizontalBoxTokenCross.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelTokenValues.add(horizontalBoxTokenCross);

				JLabel labelTokenCross = new JLabel("Cross");
				labelTokenCross.setMinimumSize(new Dimension(60, 23));
				labelTokenCross.setMaximumSize(new Dimension(60, 23));
				horizontalBoxTokenCross.add(labelTokenCross);

				formattedTextFieldTokenCross = new JFormattedTextField(integerFormatter);
				formattedTextFieldTokenCross.setValue(DRGizmoMain.prefs.getInt(TOKENCROSS, 1));
				formattedTextFieldTokenCross.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldTokenCross.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldTokenCross.setMaximumSize(new Dimension(30, 20));
				horizontalBoxTokenCross.add(formattedTextFieldTokenCross);

				Box horizontalBoxTokenThrall = Box.createHorizontalBox();
				horizontalBoxTokenThrall.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelTokenValues.add(horizontalBoxTokenThrall);

				JLabel labelTokenThrall = new JLabel("Thrall");
				labelTokenThrall.setMinimumSize(new Dimension(60, 23));
				labelTokenThrall.setMaximumSize(new Dimension(60, 23));
				horizontalBoxTokenThrall.add(labelTokenThrall);

				formattedTextFieldTokenThrall = new JFormattedTextField(integerFormatter);
				formattedTextFieldTokenThrall.setValue(DRGizmoMain.prefs.getInt(TOKENTHRALL, 1));
				formattedTextFieldTokenThrall.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldTokenThrall.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldTokenThrall.setMaximumSize(new Dimension(30, 20));
				horizontalBoxTokenThrall.add(formattedTextFieldTokenThrall);

			JPanel panelCannonballAndChests = new JPanel();
			panelOptionsTokenAndChestValues.add(panelCannonballAndChests);
			panelCannonballAndChests.setLayout(new BoxLayout(panelCannonballAndChests, BoxLayout.Y_AXIS));

				Box horizontalBoxCannonball = Box.createHorizontalBox();
				panelCannonballAndChests.add(horizontalBoxCannonball);

				JLabel labelCannonball = new JLabel("Cannonball");
				horizontalBoxCannonball.add(labelCannonball);

				formattedTextFieldCannonball = new JFormattedTextField(integerFormatter);
				formattedTextFieldCannonball.setValue(DRGizmoMain.prefs.getInt(TOKENCANNONBALL, 1));
				formattedTextFieldCannonball.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldCannonball.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldCannonball.setMaximumSize(new Dimension(30, 20));
				horizontalBoxCannonball.add(formattedTextFieldCannonball);

			JPanel panelChests = new JPanel();
			panelChests.setBorder(new TitledBorder(null, "Chests", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelCannonballAndChests.add(panelChests);
			panelChests.setLayout(new BoxLayout(panelChests, BoxLayout.Y_AXIS));

				Box horizontalBoxSmallChest = Box.createHorizontalBox();
				panelChests.add(horizontalBoxSmallChest);

				JLabel labelSmallChest = new JLabel("Small");
				labelSmallChest.setMinimumSize(new Dimension(60, 23));
				labelSmallChest.setMaximumSize(new Dimension(60, 23));
				horizontalBoxSmallChest.add(labelSmallChest);

				formattedTextFieldSmallChest = new JFormattedTextField(integerFormatter);
				formattedTextFieldSmallChest.setValue(DRGizmoMain.prefs.getInt(CHESTSMALL, 1));
				formattedTextFieldSmallChest.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldSmallChest.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldSmallChest.setMaximumSize(new Dimension(30, 23));
				horizontalBoxSmallChest.add(formattedTextFieldSmallChest);

				Box horizontalBoxMediumChest = Box.createHorizontalBox();
				panelChests.add(horizontalBoxMediumChest);

				JLabel labelMediumChest = new JLabel("Medium");
				labelMediumChest.setMinimumSize(new Dimension(60, 23));
				labelMediumChest.setMaximumSize(new Dimension(60, 23));
				horizontalBoxMediumChest.add(labelMediumChest);

				formattedTextFieldMediumChest = new JFormattedTextField(integerFormatter);
				formattedTextFieldMediumChest.setValue(DRGizmoMain.prefs.getInt(CHESTMEDIUM, 2));
				formattedTextFieldMediumChest.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldMediumChest.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldMediumChest.setMaximumSize(new Dimension(30, 23));
				horizontalBoxMediumChest.add(formattedTextFieldMediumChest);

				Box horizontalBoxLargeChest = Box.createHorizontalBox();
				panelChests.add(horizontalBoxLargeChest);

				JLabel labelLargeChest = new JLabel("Large");
				labelLargeChest.setMinimumSize(new Dimension(60, 23));
				labelLargeChest.setMaximumSize(new Dimension(60, 23));
				horizontalBoxLargeChest.add(labelLargeChest);

				formattedTextFieldLargeChest = new JFormattedTextField(integerFormatter);
				formattedTextFieldLargeChest.setValue(DRGizmoMain.prefs.getInt(CHESTLARGE, 3));
				formattedTextFieldLargeChest.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldLargeChest.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldLargeChest.setMaximumSize(new Dimension(30, 23));
				horizontalBoxLargeChest.add(formattedTextFieldLargeChest);

			JPanel panelCCRecords = new JPanel();
			panelCCRecords.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelCCRecords.setBorder(new TitledBorder(null, "CC Records", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelOptions_2.add(panelCCRecords);
			panelCCRecords.setLayout(new BoxLayout(panelCCRecords, BoxLayout.X_AXIS));

			JPanel panelSingleEntry = new JPanel();
			panelSingleEntry.setBorder(new TitledBorder(null, "Single Entry", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelCCRecords.add(panelSingleEntry);
			panelSingleEntry.setLayout(new BoxLayout(panelSingleEntry, BoxLayout.Y_AXIS));

				Box horizontalBoxEntryIndividual = Box.createHorizontalBox();
				panelSingleEntry.add(horizontalBoxEntryIndividual);

				JLabel lblNewLabelEntryIndividual = new JLabel("Individual");
				lblNewLabelEntryIndividual.setPreferredSize(new Dimension(60, 20));
				lblNewLabelEntryIndividual.setMinimumSize(new Dimension(60, 20));
				lblNewLabelEntryIndividual.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntryIndividual.add(lblNewLabelEntryIndividual);

				formattedTextFieldEntryIndividual = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntryIndividual.setValue(DRGizmoMain.prefs.getInt(CCENTRYINDIVIDUAL, 0));
				formattedTextFieldEntryIndividual.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntryIndividual.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntryIndividual.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntryIndividual.add(formattedTextFieldEntryIndividual);

				Box horizontalBoxEntry7 = Box.createHorizontalBox();
				panelSingleEntry.add(horizontalBoxEntry7);

				JLabel labelEntry7 = new JLabel("7 Man");
				labelEntry7.setPreferredSize(new Dimension(60, 20));
				labelEntry7.setMinimumSize(new Dimension(60, 20));
				labelEntry7.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry7.add(labelEntry7);

				formattedTextFieldEntry7 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry7.setValue(DRGizmoMain.prefs.getInt(CCENTRY7, 0));
				formattedTextFieldEntry7.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry7.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry7.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry7.add(formattedTextFieldEntry7);

				Box horizontalBoxEntry6 = Box.createHorizontalBox();
				panelSingleEntry.add(horizontalBoxEntry6);

				JLabel labelEntry6 = new JLabel("6 Man");
				labelEntry6.setPreferredSize(new Dimension(60, 20));
				labelEntry6.setMinimumSize(new Dimension(60, 20));
				labelEntry6.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry6.add(labelEntry6);

				formattedTextFieldEntry6 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry6.setValue(DRGizmoMain.prefs.getInt(CCENTRY6, 0));
				formattedTextFieldEntry6.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry6.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry6.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry6.add(formattedTextFieldEntry6);

				Box horizontalBoxEntry5 = Box.createHorizontalBox();
				panelSingleEntry.add(horizontalBoxEntry5);

				JLabel labelEntry5 = new JLabel("5 Man");
				labelEntry5.setPreferredSize(new Dimension(60, 20));
				labelEntry5.setMinimumSize(new Dimension(60, 20));
				labelEntry5.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry5.add(labelEntry5);

				formattedTextFieldEntry5 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry5.setValue(DRGizmoMain.prefs.getInt(CCENTRY5, 0));
				formattedTextFieldEntry5.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry5.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry5.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry5.add(formattedTextFieldEntry5);

				Box horizontalBoxEntry4 = Box.createHorizontalBox();
				panelSingleEntry.add(horizontalBoxEntry4);

				JLabel labelEntry4 = new JLabel("4 Man");
				labelEntry4.setPreferredSize(new Dimension(60, 20));
				labelEntry4.setMinimumSize(new Dimension(60, 20));
				labelEntry4.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry4.add(labelEntry4);

				formattedTextFieldEntry4 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry4.setValue(DRGizmoMain.prefs.getInt(CCENTRY4, 0));
				formattedTextFieldEntry4.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry4.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry4.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry4.add(formattedTextFieldEntry4);

				Box horizontalBoxEntry3 = Box.createHorizontalBox();
				panelSingleEntry.add(horizontalBoxEntry3);

				JLabel labelEntry3 = new JLabel("3 Man");
				labelEntry3.setPreferredSize(new Dimension(60, 20));
				labelEntry3.setMinimumSize(new Dimension(60, 20));
				labelEntry3.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry3.add(labelEntry3);

				formattedTextFieldEntry3 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry3.setValue(DRGizmoMain.prefs.getInt(CCENTRY3, 0));
				formattedTextFieldEntry3.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry3.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry3.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry3.add(formattedTextFieldEntry3);

				Box horizontalBoxEntry2 = Box.createHorizontalBox();
				panelSingleEntry.add(horizontalBoxEntry2);

				JLabel labelEntry2 = new JLabel("2 Man");
				labelEntry2.setPreferredSize(new Dimension(60, 20));
				labelEntry2.setMinimumSize(new Dimension(60, 20));
				labelEntry2.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry2.add(labelEntry2);

				formattedTextFieldEntry2 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry2.setValue(DRGizmoMain.prefs.getInt(CCENTRY2, 0));
				formattedTextFieldEntry2.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry2.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry2.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry2.add(formattedTextFieldEntry2);

				Box horizontalBoxEntry1 = Box.createHorizontalBox();
				panelSingleEntry.add(horizontalBoxEntry1);

				JLabel labelEntry1 = new JLabel("1 Man");
				labelEntry1.setPreferredSize(new Dimension(60, 20));
				labelEntry1.setMinimumSize(new Dimension(60, 20));
				labelEntry1.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry1.add(labelEntry1);

				formattedTextFieldEntry1 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry1.setValue(DRGizmoMain.prefs.getInt(CCENTRY1, 0));
				formattedTextFieldEntry1.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry1.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry1.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry1.add(formattedTextFieldEntry1);

			JPanel panelSingleForage = new JPanel();
			panelSingleForage.setBorder(new TitledBorder(null, "Single Forage", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelCCRecords.add(panelSingleForage);
			panelSingleForage.setLayout(new BoxLayout(panelSingleForage, BoxLayout.Y_AXIS));

				Box horizontalBoxForageIndividual = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForageIndividual);

				JLabel lblNewLabelForageIndividual = new JLabel("Individual");
				lblNewLabelForageIndividual.setPreferredSize(new Dimension(60, 20));
				lblNewLabelForageIndividual.setMinimumSize(new Dimension(60, 20));
				lblNewLabelForageIndividual.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForageIndividual.add(lblNewLabelForageIndividual);

				formattedTextFieldForageIndividual = new JFormattedTextField(integerFormatter);
				formattedTextFieldForageIndividual.setValue(DRGizmoMain.prefs.getInt(CCFORAGEINDIVIDUAL, 0));
				formattedTextFieldForageIndividual.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForageIndividual.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForageIndividual.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForageIndividual.add(formattedTextFieldForageIndividual);

				Box horizontalBoxForage7 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage7);

				JLabel labelForage7 = new JLabel("7 Man");
				labelForage7.setPreferredSize(new Dimension(60, 20));
				labelForage7.setMinimumSize(new Dimension(60, 20));
				labelForage7.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage7.add(labelForage7);

				formattedTextFieldForage7 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage7.setValue(DRGizmoMain.prefs.getInt(CCFORAGE7, 0));
				formattedTextFieldForage7.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage7.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage7.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage7.add(formattedTextFieldForage7);

				Box horizontalBoxForage6 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage6);

				JLabel labelForage6 = new JLabel("6 Man");
				labelForage6.setPreferredSize(new Dimension(60, 20));
				labelForage6.setMinimumSize(new Dimension(60, 20));
				labelForage6.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage6.add(labelForage6);

				formattedTextFieldForage6 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage6.setValue(DRGizmoMain.prefs.getInt(CCFORAGE6, 0));
				formattedTextFieldForage6.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage6.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage6.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage6.add(formattedTextFieldForage6);

				Box horizontalBoxForage5 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage5);

				JLabel labelForage5 = new JLabel("5 Man");
				labelForage5.setPreferredSize(new Dimension(60, 20));
				labelForage5.setMinimumSize(new Dimension(60, 20));
				labelForage5.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage5.add(labelForage5);

				formattedTextFieldForage5 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage5.setValue(DRGizmoMain.prefs.getInt(CCFORAGE5, 0));
				formattedTextFieldForage5.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage5.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage5.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage5.add(formattedTextFieldForage5);

				Box horizontalBoxForage4 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage4);

				JLabel labelForage4 = new JLabel("4 Man");
				labelForage4.setPreferredSize(new Dimension(60, 20));
				labelForage4.setMinimumSize(new Dimension(60, 20));
				labelForage4.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage4.add(labelForage4);

				formattedTextFieldForage4 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage4.setValue(DRGizmoMain.prefs.getInt(CCFORAGE4, 0));
				formattedTextFieldForage4.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage4.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage4.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage4.add(formattedTextFieldForage4);

				Box horizontalBoxForage3 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage3);

				JLabel labelForage3 = new JLabel("3 Man");
				labelForage3.setPreferredSize(new Dimension(60, 20));
				labelForage3.setMinimumSize(new Dimension(60, 20));
				labelForage3.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage3.add(labelForage3);

				formattedTextFieldForage3 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage3.setValue(DRGizmoMain.prefs.getInt(CCFORAGE3, 0));
				formattedTextFieldForage3.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage3.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage3.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage3.add(formattedTextFieldForage3);

				Box horizontalBoxForage2 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage2);

				JLabel labelForage2 = new JLabel("2 Man");
				labelForage2.setPreferredSize(new Dimension(60, 20));
				labelForage2.setMinimumSize(new Dimension(60, 20));
				labelForage2.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage2.add(labelForage2);

				formattedTextFieldForage2 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage2.setValue(DRGizmoMain.prefs.getInt(CCFORAGE2, 0));
				formattedTextFieldForage2.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage2.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage2.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage2.add(formattedTextFieldForage2);

				Box horizontalBoxForage1 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage1);

				JLabel labelForage1 = new JLabel("1 Man");
				labelForage1.setPreferredSize(new Dimension(60, 20));
				labelForage1.setMinimumSize(new Dimension(60, 20));
				labelForage1.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage1.add(labelForage1);

				formattedTextFieldForage1 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage1.setValue(DRGizmoMain.prefs.getInt(CCFORAGE1, 0));
				formattedTextFieldForage1.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage1.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage1.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage1.add(formattedTextFieldForage1);










				JPanel panelTests = new JPanel();
				if (DRGizmoMain.DEVELOPERS) {
					tabbedPane.addTab("Tests", null, panelTests, null);
				}
				panelTests.setLayout(new BorderLayout(0, 0));

				JScrollPane scrollPaneTests = new JScrollPane();
				panelTests.add(scrollPaneTests, BorderLayout.CENTER);


				textAreaTests = new JTextArea();
				textAreaTests.setBackground(new Color(169, 185, 193)); //highlighted dr color

				textAreaTests.addCaretListener(new CaretListener() {
					public void caretUpdate(CaretEvent arg0) {
						repaint();
					}
				});
				scrollPaneTests.setViewportView(textAreaTests);






				//copied this from the output tab taht i made 5 years ago, *shrugs*
				//starts
				/*
				yoLookAndFeel = UIManager.getLookAndFeel();
				try {
					UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
				} catch (ClassNotFoundException e) {
				} catch (InstantiationException e) {
				} catch (IllegalAccessException e) {
				} catch (UnsupportedLookAndFeelException e) {}

				//CHEEZUS VUCKING CHRISTMAS I CAN"T SET THIS FUCIKING BACKNGOURD TO ANYTHING BUT WHITE UNLESS I CHANGETH E LOOK AND FEEL TO SOMETHING ELSE
				textAreaTests = new JTextArea();
				textAreaTests.setBackground(new Color(169, 185, 193)); //highlighted dr color
				//textAreaOutput.setBackground(new Color(140, 162, 172)); //non-highlighted dr color
				textAreaTests.addCaretListener(new CaretListener() {
					public void caretUpdate(CaretEvent arg0) {
						repaint();
					}
				});
				scrollPaneTests.setViewportView(textAreaTests);

				try {
					UIManager.setLookAndFeel(yoLookAndFeel);
				} catch (UnsupportedLookAndFeelException e) {}
				//end
				*/










				//textAreaTests = new JTextArea();
				//scrollPaneTests.setViewportView(textAreaTests);
				//textAreaTests.setBackground(new Color(169, 185, 193));

				JPanel panelTestsButtonsPanel = new JPanel();
				panelTests.add(panelTestsButtonsPanel, BorderLayout.SOUTH);
				panelTestsButtonsPanel.setLayout(new BoxLayout(panelTestsButtonsPanel, BoxLayout.X_AXIS));

				buttonTestsWho = new JButton("Scan /w");
				buttonTestsWho.addActionListener(this);
				buttonTestsWho.setPreferredSize(new Dimension(80, 23));
				panelTestsButtonsPanel.add(buttonTestsWho);

				buttonTestsBootyDiv = new JButton("Scan Divvy");
				buttonTestsBootyDiv.addActionListener(this);
				buttonTestsBootyDiv.setPreferredSize(new Dimension(80, 23));
				panelTestsButtonsPanel.add(buttonTestsBootyDiv);

				Component hGlue_1_1 = Box.createHorizontalGlue();
				panelTestsButtonsPanel.add(hGlue_1_1);

				buttonTestsClear = new JButton("Clear");
				buttonTestsClear.addActionListener(this);
				buttonTestsClear.setPreferredSize(new Dimension(80, 23));
				panelTestsButtonsPanel.add(buttonTestsClear);











		JPanel panelGeneral = new JPanel();
		getContentPane().add(panelGeneral, BorderLayout.SOUTH);
		panelGeneral.setLayout(new BorderLayout(0, 0));

		JPanel panelScan = new JPanel();
		panelGeneral.add(panelScan, BorderLayout.SOUTH);
		panelScan.setLayout(new BoxLayout(panelScan, BoxLayout.X_AXIS));

			buttonScan = new JButton("Scan");
			buttonScan.addActionListener(this);
			buttonScan.setMaximumSize(new Dimension(80, 23));
			buttonScan.setPreferredSize(new Dimension(80, 23));
			panelScan.add(buttonScan);

		JPanel panelQuickOptions = new JPanel();
		panelQuickOptions.setBorder(new TitledBorder(null, "Quick Options", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panelGeneral.add(panelQuickOptions, BorderLayout.CENTER);
		panelQuickOptions.setLayout(new BoxLayout(panelQuickOptions, BoxLayout.Y_AXIS));

			checkBoxAutoCopy = new JCheckBox("Auto Copy");
			checkBoxAutoCopy.setSelected(DRGizmoMain.prefs.getBoolean(AUTOCOPY, false));
			panelQuickOptions.add(checkBoxAutoCopy);

			checkBoxAutoRead = new JCheckBox("Auto Read");
			checkBoxAutoRead.setEnabled(false);
			//checkBoxAutoRead.setSelected(DRGizmoMain.prefs.getBoolean(AUTOREAD, false));
			panelQuickOptions.add(checkBoxAutoRead);

			checkBoxBrokenRecordAlert = new JCheckBox("Broken Record Alert");
			checkBoxBrokenRecordAlert.setSelected(DRGizmoMain.prefs.getBoolean(BROKENRECORDALERT, false));
			panelQuickOptions.add(checkBoxBrokenRecordAlert);

		JPanel panelScore = new JPanel();
		panelGeneral.add(panelScore, BorderLayout.NORTH);
		panelScore.setLayout(new BoxLayout(panelScore, BoxLayout.X_AXIS));

			buttonScore = new JButton("Score All");
			buttonScore.addActionListener(this);
			buttonScore.setPreferredSize(new Dimension(80, 23));
			panelScore.add(buttonScore);

			Component hGlue_3 = Box.createHorizontalGlue();
			panelScore.add(hGlue_3);

			labelDRCount = new JLabel("DR Count : " + (drTreeRootNode.getChildCount()));
			panelScore.add(labelDRCount);

			Component rigidArea = Box.createRigidArea(new Dimension(20, 20));
			panelScore.add(rigidArea);
	}

	@Override
	public void actionPerformed(ActionEvent e) {

		if (e.getSource().equals(buttonOutputClear)) {
			textAreaOutput.setText("");
			return;
		}

		if (e.getSource().equals(buttonDRTreeRemove)) {
	        TreePath currentSelection = drTree.getSelectionPath();
	        if (currentSelection != null) {
	            DefaultMutableTreeNode currentNode = (DefaultMutableTreeNode)
	                         (currentSelection.getLastPathComponent());
	            MutableTreeNode parent = (MutableTreeNode)(currentNode.getParent());
	            if (parent != null) {
	                drTreeModel.removeNodeFromParent(currentNode);
	                //update DRCount label
	        		labelDRCount.setText("DR Count : " + (drTreeRootNode.getChildCount()));

	    			try {
	    				//TODO make sure this works
	    				PrefObj.putObject(DRGizmoMain.prefs, TREEDATA, drTreeToPersistantData());
	    				printStatus("Tree Saved", true);
	    			} catch (IOException e1) {
	    				printStatus("Saving Tree Failed: IO", true);
	    			} catch (BackingStoreException e1) {
	    				printStatus("Saving Tree Failed: BSE", true);
	    			} catch (ClassNotFoundException e1) {
	    				printStatus("Saving Tree Failed: CNFE", true);
	    			}
	                return;
	            }
	        }

	        // Either there was no selection, or the root was selected.
	        Toolkit.getDefaultToolkit().beep();

			return;
		}

		if (e.getSource().equals(buttonDRTreeClear)) {
	        drTreeRootNode.removeAllChildren();
	        drTreeModel.reload();
	        //update DRCount label
			labelDRCount.setText("DR Count : " + (drTreeRootNode.getChildCount()));

			try {
				//TODO make sure this works
				PrefObj.putObject(DRGizmoMain.prefs, TREEDATA, drTreeToPersistantData());
				printStatus("Tree Saved", true);
			} catch (IOException e1) {
				printStatus("Saving Tree Failed: IO", true);
			} catch (BackingStoreException e1) {
				printStatus("Saving Tree Failed: BSE", true);
			} catch (ClassNotFoundException e1) {
				printStatus("Saving Tree Failed: CNFE", true);
			}

	        printStatus("DR Tree Cleared", false);
	        printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonSavePrefs)) {
			DRGizmoMain.prefs.putBoolean(AUTOCOPY, checkBoxAutoCopy.isSelected());
			DRGizmoMain.prefs.putBoolean(AUTOREAD, checkBoxAutoRead.isSelected());
			DRGizmoMain.prefs.putBoolean(BROKENRECORDALERT, checkBoxBrokenRecordAlert.isSelected());
			DRGizmoMain.prefs.putBoolean(DEBUG, checkBoxDebug.isSelected());
			DRGizmoMain.prefs.putBoolean(IGNOREAI, checkBoxIgnoreAI.isSelected());

			DRGizmoMain.prefs.putBoolean(SCANWRITEDATE, checkBoxScanWriteDate.isSelected());
			DRGizmoMain.prefs.putBoolean(SCANUSETABS, checkBoxScanUseTabs.isSelected());
			DRGizmoMain.prefs.putInt(SCANOCEAN, comboBoxScanOcean.getSelectedIndex());
			DRGizmoMain.prefs.putBoolean(SCANSTATIONHEADERS, checkBoxScanStationHeaders.isSelected());
			DRGizmoMain.prefs.putBoolean(SCANSTATION, checkBoxScan2Station.isSelected());
			DRGizmoMain.prefs.putBoolean(SCANPIRATE, checkBoxScan2Pirate.isSelected());
			DRGizmoMain.prefs.putBoolean(SCANRATING, checkBoxScan2Rating.isSelected());
			DRGizmoMain.prefs.putBoolean(SCANTOKENSCORE, checkBoxScan2TokenScore.isSelected());
			DRGizmoMain.prefs.putBoolean(SCANTOKENARRAY, checkBoxScan2TokenArray.isSelected());

			DRGizmoMain.prefs.putBoolean(SCOREWRITEDATE, checkBoxScoreWriteDate.isSelected());
			DRGizmoMain.prefs.putBoolean(SCOREUSETABS, checkBoxScoreUseTabs.isSelected());
			DRGizmoMain.prefs.putInt(SCOREOCEAN, comboBoxScoreOcean.getSelectedIndex());
			DRGizmoMain.prefs.putBoolean(SCORESTATIONHEADERS, checkBoxScoreStationHeaders.isSelected());
			DRGizmoMain.prefs.putBoolean(SCORESTATION, checkBoxScore2Station.isSelected());
			DRGizmoMain.prefs.putBoolean(SCOREPIRATE, checkBoxScore2Pirate.isSelected());
			DRGizmoMain.prefs.putBoolean(SCORERATING, checkBoxScore2Rating.isSelected());
			DRGizmoMain.prefs.putBoolean(SCORETOKENSCORE, checkBoxScore2TokenScore.isSelected());
			DRGizmoMain.prefs.putBoolean(SCORETOKENARRAY, checkBoxScore2TokenArray.isSelected());

			DRGizmoMain.prefs.putInt(TOKENCIRCLE, Integer.parseInt(formattedTextFieldTokenCircle.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(TOKENDIAMOND, Integer.parseInt(formattedTextFieldTokenDiamond.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(TOKENPLUS, Integer.parseInt(formattedTextFieldTokenPlus.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(TOKENCROSS, Integer.parseInt(formattedTextFieldTokenCross.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(TOKENTHRALL, Integer.parseInt(formattedTextFieldTokenThrall.getText().replaceAll(",", "")));

			DRGizmoMain.prefs.putInt(TOKENCANNONBALL, Integer.parseInt(formattedTextFieldCannonball.getText().replaceAll(",", "")));

			DRGizmoMain.prefs.putInt(CHESTSMALL, Integer.parseInt(formattedTextFieldSmallChest.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CHESTMEDIUM, Integer.parseInt(formattedTextFieldMediumChest.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CHESTLARGE, Integer.parseInt(formattedTextFieldLargeChest.getText().replaceAll(",", "")));

			DRGizmoMain.prefs.putInt(CCENTRYINDIVIDUAL, Integer.parseInt(formattedTextFieldEntryIndividual.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCENTRY7, Integer.parseInt(formattedTextFieldEntry7.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCENTRY6, Integer.parseInt(formattedTextFieldEntry6.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCENTRY5, Integer.parseInt(formattedTextFieldEntry5.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCENTRY4, Integer.parseInt(formattedTextFieldEntry4.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCENTRY3, Integer.parseInt(formattedTextFieldEntry3.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCENTRY2, Integer.parseInt(formattedTextFieldEntry2.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCENTRY1, Integer.parseInt(formattedTextFieldEntry1.getText().replaceAll(",", "")));

			DRGizmoMain.prefs.putInt(CCFORAGEINDIVIDUAL, Integer.parseInt(formattedTextFieldForageIndividual.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCFORAGE7, Integer.parseInt(formattedTextFieldForage7.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCFORAGE6, Integer.parseInt(formattedTextFieldForage6.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCFORAGE5, Integer.parseInt(formattedTextFieldForage5.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCFORAGE4, Integer.parseInt(formattedTextFieldForage4.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCFORAGE3, Integer.parseInt(formattedTextFieldForage3.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCFORAGE2, Integer.parseInt(formattedTextFieldForage2.getText().replaceAll(",", "")));
			DRGizmoMain.prefs.putInt(CCFORAGE1, Integer.parseInt(formattedTextFieldForage1.getText().replaceAll(",", "")));

			DRGizmoMain.prefs.putInt("width", getWidth());
			DRGizmoMain.prefs.putInt("height", getHeight());

			printStatus("Preferences Saved", false);
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonScan)) {
			DRGroupData data = new DRGizmo(window, textAreaOutput, checkBoxDebug.isSelected()).scanDutyReport();
			if (data != null && !data.isEmpty()) {

		/*
				Random r = new Random();
				int n = r.nextInt(7);
				for (int i = 0; i < 2; i++) {

					int[] tempChests = {0, 0, 0};
					for (int j = 0; j < 15; j++) {
						if (r.nextInt(2) == 0) {
							tempChests[0]++;
							continue;
						}
						if (r.nextInt(2) == 0) {
							tempChests[1]++;
							continue;
						}
						if (r.nextInt(2) == 0) {
							tempChests[2]++;
							continue;
						}
					}
				DRIndividualData temp = new DRIndividualData("Foraging", "Bob" + i + "", "Frenetic", tempChests, 15);
				data.put(temp);
				}
*/

				//add to drTree with the default tokenScore
				addDRToTree(data);
				//save new true to prefs
				try {
    				//TODO make sure this works
    				PrefObj.putObject(DRGizmoMain.prefs, TREEDATA, drTreeToPersistantData());
    				printStatus("Tree Saved", true);
    			} catch (IOException e1) {
    				printStatus("Saving Tree Failed: IO", true);
    			} catch (BackingStoreException e1) {
    				printStatus("Saving Tree Failed: BSE", true);
    			} catch (ClassNotFoundException e1) {
    				printStatus("Saving Tree Failed: CNFE", true);
    			}

				//change tokenScore based on the settings
				data.calculateCustomTokenScore(Integer.parseInt(formattedTextFieldTokenCircle.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenDiamond.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenPlus.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenCross.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenThrall.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldCannonball.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldSmallChest.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldMediumChest.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldLargeChest.getText().replaceAll(",", "")));
				//print dr to output with print settings
				String dataString = data.toString(checkBoxIgnoreAI.isSelected(), checkBoxScanWriteDate.isSelected(),
						checkBoxScanUseTabs.isSelected(), comboBoxScanOcean.getSelectedItem().toString(),
						checkBoxScanStationHeaders.isSelected(), checkBoxScan2Station.isSelected(), checkBoxScan2Pirate.isSelected(),
						checkBoxScan2Rating.isSelected(), checkBoxScan2TokenScore.isSelected(),
						checkBoxScan2TokenArray.isSelected());
				printStatus(dataString, false);
				//check for broken records
				if (checkBoxBrokenRecordAlert.isSelected()) {
					String brokenRecords = data.getBrokenRecords(Integer.parseInt(formattedTextFieldForageIndividual.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldForage1.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldForage2.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldForage3.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldForage4.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldForage5.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldForage6.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldForage7.getText().replaceAll(",", "")));
					if (!brokenRecords.isEmpty()) {
						printStatus(brokenRecords, false);
					}
				}

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
				    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				    clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("Scan Failed", false);
			}
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonScore)) {
			DRPersistantData data = drTreeToPersistantData();
			if (data != null && !data.isEmpty()) {
				//change tokenScore based on the settings
				data.calculateCustomTokenScore(Integer.parseInt(formattedTextFieldTokenCircle.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenDiamond.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenPlus.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenCross.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenThrall.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldCannonball.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldSmallChest.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldMediumChest.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldLargeChest.getText().replaceAll(",", "")));
				//print dr to output with print settings
				String dataString = data.toString(checkBoxIgnoreAI.isSelected(), checkBoxScoreWriteDate.isSelected(),
						checkBoxScoreUseTabs.isSelected(), comboBoxScoreOcean.getSelectedItem().toString(),
						checkBoxScoreStationHeaders.isSelected(), checkBoxScore2Station.isSelected(), checkBoxScore2Pirate.isSelected(),
						checkBoxScore2Rating.isSelected(), checkBoxScore2TokenScore.isSelected(),
						checkBoxScore2TokenArray.isSelected());
				printStatus(dataString, false);

				//check for broken records
				if (checkBoxBrokenRecordAlert.isSelected()) {
					String brokenRecords = data.getBrokenRecords(Integer.parseInt(formattedTextFieldEntryIndividual.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldEntry1.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldEntry2.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldEntry3.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldEntry4.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldEntry5.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldEntry6.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldEntry7.getText().replaceAll(",", "")));
					if (!brokenRecords.isEmpty()) {
						printStatus(brokenRecords, false);
					}
				}

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
				    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				    clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("Scoring Failed", false);
			}
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonOutputAvg)){
			DRPersistantData data = drTreeToPersistantData();
			if (data != null && !data.isEmpty()) {
				//change tokenScore based on the settings
				data.calculateCustomTokenScore(Integer.parseInt(formattedTextFieldTokenCircle.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenDiamond.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenPlus.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenCross.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenThrall.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldCannonball.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldSmallChest.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldMediumChest.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldLargeChest.getText().replaceAll(",", "")));
				//print dr to output with print settings
				String dataString = data.toAvg();
				printStatus(dataString, false);

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("Averages Failed", false);
			}
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonOutputCC)){
			DRPersistantData data = drTreeToPersistantData();
			if (data != null && !data.isEmpty()) {
				//change tokenScore based on the settings
				data.calculateCustomTokenScore(Integer.parseInt(formattedTextFieldTokenCircle.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenDiamond.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenPlus.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenCross.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldTokenThrall.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldCannonball.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldSmallChest.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldMediumChest.getText().replaceAll(",", "")),
						Integer.parseInt(formattedTextFieldLargeChest.getText().replaceAll(",", "")));
				//print dr to output with print settings
				String dataString = data.toCC();
				printStatus(dataString, false);

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("CCs Failed", false);
			}
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonTestsClear)) {
			textAreaTests.setText("3");
			return;
		}


		if (e.getSource().equals(buttonTestsWho)) {

			String s = new DRGizmo(window, textAreaTests, checkBoxDebug.isSelected()).scanAhoyTab();
			printTestStatus(s, false);

			return;
		}


		if (e.getSource().equals(buttonTestsBootyDiv)) {
			printTestStatus("you tried to scan for booty division but i haven't made that yet", false);
			printStatus("backup: you tried to scan for booty division but i haven't made that yet", false);

			//String s = new DRGizmo(window, textAreaTests, checkBoxDebug.isSelected()).scanRecursiveSearcher("class com.threerings.piracy.info.client.WhoInfoView");
			//printTestStatus(s, false);
			new DRGizmo(window, textAreaTests, checkBoxDebug.isSelected()).scanAllNodes();

			return;
		}





		textAreaOutput.append("unknown event: " + e.toString() + "\n" + e.getSource().toString() + "\n" + e.getSource().getClass().toString() + "\n");

	}



	private void addDRToTree(DRGroupData dr) {
		DefaultMutableTreeNode nodeNumber = new DefaultMutableTreeNode();
		//DefaultMutableTreeNode parent = new DefaultMutableTreeNode(userObject)

		//make a node (1, 2, 3, ...) for this dr and decend into it
		nodeNumber.setUserObject(drTreeRootNode.getChildCount()+1);

		for (String station : dr.data.keySet()) {
			DefaultMutableTreeNode nodeStation = new DefaultMutableTreeNode();
			nodeStation.setUserObject(station);
			//make a node for this station and decend into it
			for (String pirate : dr.data.get(station).keySet()) {
				//add dr.get(station, pirate).toString() as a leaf to this node
				DefaultMutableTreeNode nodeIndividualDR = new DefaultMutableTreeNode();
				nodeIndividualDR.setUserObject(new DRIndividualData(dr.get(station, pirate)));
				nodeStation.add(nodeIndividualDR);
			}
			//add station do nodeNumber
			nodeNumber.add(nodeStation);
		}
		drTreeModel.insertNodeInto(nodeNumber, drTreeRootNode, drTreeRootNode.getChildCount());

		//if we previously had no drs then we have to open up the root because we won't beable to click it
		if (drTreeRootNode.getChildCount() == 1) {
			drTree.expandPath(new TreePath(drTreeRootNode.getPath()));
		}
		//update DRCount label
		if (labelDRCount != null)
			labelDRCount.setText("DR Count : " + (drTreeRootNode.getChildCount()));
	}

	private DRPersistantData drTreeToPersistantData() {
		DRPersistantData result = new DRPersistantData();
		for (int groupIndex = 0; groupIndex < drTreeRootNode.getChildCount(); groupIndex++) {
			TreeNode groupNodes = drTreeRootNode.getChildAt(groupIndex);
			DRGroupData groupData = new DRGroupData();

			for (int stationIndex = 0; stationIndex < groupNodes.getChildCount(); stationIndex++) {
				TreeNode stationNodes = groupNodes.getChildAt(stationIndex);
				for (int pirateIndex = 0; pirateIndex < stationNodes.getChildCount(); pirateIndex++) {
					DefaultMutableTreeNode pirateNodes = (DefaultMutableTreeNode) stationNodes.getChildAt(pirateIndex);
					groupData.put((DRIndividualData) pirateNodes.getUserObject());
				}
			}
			result.add(groupData);
		}
		return result;
	}


}
