//Originally made by Tanonev.
/*Small(ish) edit by Sylcium.
 * 
 * Known bugs
 * -visual glitch when highlighting text in the main scrollpane when there is more than ~100 lines. functionally it works fine.
 * -supposedly saving forage only DR causes a crash. haven't tested this myself.
 * -saying "/e has left the vessel." will get picked up by getStayers. additionally someone saying:
 * 		"Somedick says, "blah blah blah blah
 * 		[12:34:56] Someoneelse has come aboard.
 * 		[98:76:54] Otherdude has left the vessel.
 * 		blah blah blah""
 * 		the only way do deal with this would be to make your own chatlog that logs more data (which chat, player message/game message, etc.) to make it possible to remove false positives.
 * 
 * 
 * 
 */


package main.java.general;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;





public class DRGizmo {
	public static final String CHATLOG = "chatlog";
	public static final String DRLOG = "drlog";

	Window window;
	JTextArea status;
	boolean debug;
	DRGroupData data = new DRGroupData();

	DRGizmo(Window window, JTextArea status, boolean writeDebug) {
		this.window = window;
		this.status = status;
		debug = writeDebug;
	}

	private void printStatus(String s, boolean isDebug) {
		if (!(isDebug) ||  ((isDebug) && (debug))) {
			status.append(s + System.getProperty("line.separator"));
			status.setCaretPosition(status.getText().length());
		}
	}
	
	
	private void printStatusStackTrace(Exception e) {
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		String exceptionAsString = sw.toString();
		
		printStatus(exceptionAsString, true);
	}


	private int getTotalScore(String station, int[] array) {
		if (array == null)
			return 0;
		
		int result = 0;
		if (station.matches("Foraging")) {
			for(int i = 0; i < array.length; i++) {
				result += array[i] * (i + 1);
			}
		} else if (station.matches("Carpentry") && array.length == 3) {
			result += array[0] * -1;
			result += array[1];
			result += array[2] * 2;
		} else {
			for(int i = 0; i < array.length; i++) {
				result += array[i];
			}
		}
		return result;
	}

	public DRGroupData scanDutyReport() {
		try {
			// findDutyReport2 includes Steam client compatibility fixes and should always be used.
			if (findDutyReport2()) 
				return data;
			return null;
		} catch (Exception e) {
			return null;
		}

	}

	public void scanAllNodes() {
		try {
			printAllNodes(window, 1, new StringBuilder());
		} catch (Exception e) {
			
		}

	}
	

	private boolean findDutyReport2() {
		
		data = new DRGroupData();
		Component node = window;

		//this gets us 90% of the way there.
		//int[] path = scanRecursiveSearcher("class com.threerings.piracy.swing.TwoColumnPanel");

		int[] steam = findClassRecursive(node, "class com.threerings.yohoho.steam.client.SteamOverlayCanvas", 1, new ArrayList<Integer>());

		if (steam != null) {
			// If we detect steam, do some shifting Before calling Class Recursive.
			// This Shifting is to bypass the Steam Overlay that is inserted into the game client, allowing full traversal to the Duty Reports on all devices.
			printStatus("found steam", true);

			if (null == (node = descendNodes(node, new int[] {0, 1, 1}))) {
				printStatus("findDutyReport failed 1.1", true);
			}
			if (null == (node = descendNodes(node, new int[] {0}))) {
				printStatus("findDutyReport failed 1.2", true);
			}
		}

		int[] path = findClassRecursive(node, "class com.threerings.piracy.swing.TwoColumnPanel", 1, new ArrayList<Integer>());
		printStatus(Arrays.toString(path), true);
		if (path == null) {
			printStatus("findDutyReport failed 0", true);
			return false;
		}
		
		if (null == (node = descendNodes(node, path))) {
			printStatus("findDutyReport failed 1", true);
			return false; 
		}

		printStatus("node 5: " + node.getClass().toString(), true);
		Component test = descend(node, 0);
		printStatus("test 1: " + test.getClass().toString(), true);
		test = descend(test, 0);
		printStatus("test 2: " + test.getClass().toString(), true);
		if (test.getClass().toString().equals("class com.threerings.yohoho.sea.vessel.client.DutyReportView$DutyHeader")) {
			printStatus("test 3: " + test.getClass().toString(), true);
			
			
			printStatus("the right node is: " + node.getClass().toString(), true);
			Container branch = (Container) node;
			processBranch(branch);
		} else {
			
			printStatus("test 4: " + test.getClass().toString(), true);
			for (int i = 0; i < ((Container) node).getComponentCount(); i++) {
				Container branch = (Container) descend(node, i);
				printStatus("processing branch " + i , true);
				processBranch(branch);
			}
		}
		printStatus("findDutyReport is true", true);
		return true;

	}
	
	public String scanAhoyTab() {
		try {
			if (findWhoResult()) 
				return "Howdy";
			return null;
		} catch (Exception e) {
			return null;
		}

	}
	
	
	
	
	private boolean findWhoResult() {
		
		Component node = window;
		//had to change this to get it to work with steam
		/*
		printStatus("node 1: " + node.getClass().toString(), true);
		if (null == (node = descendNodes(node, new int[] {0, 1, 0, 0}))) {
			printStatus("findDutyReport failed 1", true);
			return false; 
		}
		*/
		
		
		//this gets us 90% of the way there.
		int[] path = scanRecursiveSearcher("class com.threerings.piracy.info.client.WhoInfoView");
		
		if (path == null) {
			printStatus("findWhoResult failed 0", true);
			return false;
		}
		

		
		
		if (null == (node = descendNodes(node, path))) {
			printStatus("findWhoResult failed 1", true);
			return false; 
		}
		
		
		printStatus("node 2: " + node.getClass().toString(), true);
		List<Component> handyTextPanes = findAllComponents(node, "class com.threerings.piracy.swing.HandyTextPane", new ArrayList<Component> ());
		
		
		printStatus("" + handyTextPanes.size(), true);
		for (Component c : handyTextPanes) {
			JEditorPane temp = (JEditorPane) c;
			printStatus(temp.getText(), false);
		}
		
		
		return false;
	}
	
	
	
	
	/**
	 * hopefully scans everything in the fucking game until it finds something with the string you are looking for
	 * @param searchFor
	 * @return some good info
	 */
	public int[] scanRecursiveSearcher(String searchFor) {
		try {
			int[] path = findClassRecursive(window, searchFor, 1, new ArrayList<Integer>());
			if (path != null) {
				return path;
			}
			return null;
		} catch (Exception e) {
			return null;
		}

	}
	
	
	
	
	
	/**
	 * searches every component looking for the specified one. Stops at the first instance of that component found.
	 * @param parent
	 * @param searchFor
	 * @param depth
	 * @param path
	 * @return Returns an int[] that will lead you to the found component from the initial parent
	 */
	private int[] findClassRecursive(Component parent, String searchFor, int depth, List<Integer> path) {
		
		
		Component[] children = ((Container) parent).getComponents();
		
		for (int i = 0; i < children.length; i++)
		{
			StringBuilder whitespace = new StringBuilder();
			for (int j = 0; j < depth; j++) {
				whitespace.append(" ");
			}

			
			if (children[i].getClass().toString().equals(searchFor)) {
				//printStatus("WINNER depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString(), true);
				path.add(i);
				return DRGizmoUtil.ToIntArray(path);
			} else {
				List<Integer> temp = new ArrayList<Integer>(path);
				temp.add(i);
				//printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString(), true);
				int[] result = findClassRecursive(children[i], searchFor, depth + 1, temp);
				if (result != null)
					return result;
			}
			
		}
		
		return null;
	}
	
	
	
	
	
	private List<Component> findAllComponents(Component parent, String searchFor, List<Component> results) {
		
		
		Component[] children = ((Container) parent).getComponents();
		
		for (int i = 0; i < children.length; i++)
		{
			if (children[i].getClass().toString().equals(searchFor))
				results.add(children[i]);
				
			findAllComponents(children[i], searchFor, results);
			
		}
		
		return results;
	}
	
	

	
	
	
	
	/**
	 * this processes a "Who's online" given the right branch
	 * @param branch
	 */
	private void processWhoBranch(Container branch) {
		if (branch.getComponentCount() == 0) {
			printStatus("nothing to process", true);
			return;
		}
		
		
	}
	
	/**
	 * this processes a duty report given the right container
	 * todo make this method eat a twocolumnpanel instead of a difficult to track jpanel near the twocolumnpanel
	 * @param branch
	 */
	@SuppressWarnings("unchecked")
	private void processBranch(Container branch) {
		if (branch.getComponentCount() == 0) {
			printStatus("nothing to process", true);
			return;
		}

		printStatus("branches to process:", true);
		for (int j = 0; j < branch.getComponentCount(); j++) {
			printStatus(j + ": " + branch.getComponent(j).getClass().toString(), true);
		}
		for (int i = 0; i < branch.getComponentCount(); i++) {
			printStatus("branch " + i + ": " + branch.getComponent(i).getClass().toString(), true);
			Container station = (Container) descend(branch, i);
			Component stationLabel = descendNodes(station, new int[] {0, 1, 0});
			/*
			Component stationLabel = descend(station, 0);
			stationLabel = descend(stationLabel, 1);
			stationLabel = descend(stationLabel, 0);
			*/
			String stationName = ((JLabel) stationLabel).getText(); 
			printStatus(stationName, true);
			for (int j = 1; j < station.getComponentCount(); j++) {
				Component pirateData = descend(station, j);
				pirateData = descend(pirateData, 1);
				Component nameLabel = descend(pirateData, 0);
				String name = ((JLabel) nameLabel).getText();
				printStatus(name, true);
				Component drLabel = descend(pirateData, 1);
				Object innerLabel = getField(drLabel, "_label", Object.class);
				String dr = getField(innerLabel, "b", String.class);
				printStatus(dr, true);
				int[] tokens = null;
				Component tokenPanel = descend(pirateData, 2);
				if (tokenPanel != null) {
					ArrayList<int[]> tokensArrayList = getField(tokenPanel, "_rows", ArrayList.class);
					tokens = new int[tokensArrayList.get(0).length];
					
					//tokensArrayList has an array for every row of tokens
					for (int[] array : tokensArrayList) {
						for (int k = 0; k < array.length; k++) {
							tokens[k] += array[k];
						}
					}

					printStatus(Arrays.toString(tokens), true);
				}
				//printStatus(name + " " + dr + " " + Arrays.toString(tokens));
				data.put(stationName, name, dr, tokens, getTotalScore(stationName, tokens));
				//printStatus(name + " " + dr + " " + Arrays.toString(tokens));
			}
		}
	}
	
	private void printAllNodes(Component parent, int depth, StringBuilder path) {
		
		Component[] children = ((Container) parent).getComponents();
		
		for (int i = 0; i < children.length; i++)
		{
			if (children[i] instanceof JLabel) {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (((JLabel) children[i]).getText()), true);
			} else if (children[i] instanceof JTextPane) {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (((JTextPane) children[i]).getText()), true);
			} else if (children[i] instanceof JEditorPane) {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (((JEditorPane) children[i]).getText()), true);
			} else if (children[i] instanceof JButton) {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (((JButton) children[i]).getText()), true);
			} else if (children[i].getClass().toString().equals("class com.threerings.piracy.swing.YoMultiLineLabel")) {
				Object innerLabel = getField(children[i], "_label", Object.class);
				String asdf = getField(innerLabel, "b", String.class);
				//printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (((JLabel) innerLabel).getText()), true);
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (asdf), true);
			} else if (children[i].getClass().toString().equals("class com.threerings.piracy.item.client.PoeContainer")) {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + readPoeContainer(children[i]), true);
			} else if (children[i].getClass().toString().equals("class com.threerings.piracy.item.client.ItemContainer")) {
				try {
					printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + readItemContainer(children[i]), true);
					
				} catch (Exception e) {
					// TODO: handle exception
					printStatusStackTrace(e);
				}
			} else if (children[i].getClass().toString().equals("class com.threerings.piracy.item.client.InventoryItemContainer")) {
				try {
					printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + readInventoryItemContainer(children[i]), true);
					
				} catch (Exception e) {
					// TODO: handle exception
					printStatusStackTrace(e);
				}
			} else {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString(), true);
			}
				
			printAllNodes(children[i], depth + 1, new StringBuilder(path).append(i + ", "));
			
		}
		
	}

	private Component descendNodes(Component parent, int[] path) {
		for(int i=0;i<path.length;i++) {
			printStatus("decendNodes: parent " + i + ": " + parent.getClass().toString(), true);
			for (int j = 0; j < ((Container) parent).getComponentCount(); j++) {
				printStatus("decendNodes: children " + i + ": " + ((Container) parent).getComponent(j).getClass().toString(), true);
			}
			if (null == (parent = descend(parent, path[i]))) return null;
		}
		return parent;
	}

	private Component descend(Component parent, int childNum) {
		if (parent == null) return null;
		if (!(parent instanceof Container)) return null;
		if (childNum >= ((Container) parent).getComponentCount()) return null;
		return ((Container) parent).getComponent(childNum);
	}
	
	private <T> T getField(Object o, String fieldName, Class<T> clazz) {
		Class<?> c = o.getClass();
		try {
			while (!c.equals(Object.class)) {
				final Field[] fields = c.getDeclaredFields();
				for (int i = 0; i < fields.length; ++i) {
					if (fieldName.equals(fields[i].getName())) {
						fields[i].setAccessible(true);
						Object ret = fields[i].get(o);
						try {
							return clazz.cast(ret);
						} catch (ClassCastException e) {
							return null;
						}
					}
				}
				c = c.getSuperclass();
			}
		} catch (Exception ignored) {}
		return null;
	}

	
	/**
	 * Pass this a poeContainer and it'll poop out the amount
	 * @return
	 */
	private int readPoeContainer(Object poeContainer) {
		
		Integer amount = getField(poeContainer, "_amount", Integer.class);
		return amount.intValue();
				
	}
	
	private String readItemContainer(Object itemContainer) {
		
		//get the _item field
		Object item = getField(itemContainer, "_item", Object.class);
		
		//now comes the tricky part, we have to get the name of the item
		//get the item class, this should be com.threerings.piracy.item.client.data.Item
		Class itemClass = item.getClass();
		
		//get the getName method
		Method methodGetName = null;
		try {
			methodGetName = itemClass.getMethod("getName", null);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
		
		//now we have to invoke the item.getName() method.
		String itemName = null;
		try {
			itemName = (String) methodGetName.invoke(item);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
				
		return itemName;
	}
		
	
	private String readInventoryItemContainer(Object inventoryItemContainer) {
		
		JPanel asdf = (JPanel) inventoryItemContainer;
		
		//replace breaks with newlines for fun
		String tooltipText = asdf.getToolTipText().replaceAll("<br>", "\n");
		
		//remove html tags
		//  this has its issues (items with custom messages and maybe more) but its good enough
		tooltipText = tooltipText.replaceAll("\\<.*?\\>", "");
		
		//printStatus(tooltipText, true);
		
		//get the _item field
		Object item = getField(inventoryItemContainer, "_item", Object.class);
		
		//now comes the tricky part, we have to get the name of the item
		//get the item class, this should be com.threerings.piracy.item.client.data.Item
		Class itemClass = item.getClass();
		
		//get the getName method
		Method methodGetName = null;
		try {
			methodGetName = itemClass.getMethod("getName", null);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
		
		//now we have to invoke the item.getName() method.
		String itemName = null;
		try {
			itemName = (String) methodGetName.invoke(item);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
		
		//get the ToString method
		Method methodToString = null;
		try {
			methodToString = itemClass.getMethod("toString", null);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
		
		//now we have to invoke the item.getName() method.
		String itemToString = null;
		try {
			itemToString = (String) methodToString.invoke(item);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
				
		return tooltipText + ", " + itemName + ", " + itemToString;
		
	}

	
	private void printAllFieldsAndMethods(Class clazz) {
		printStatus("\n\n\nFields", true);
		printAllFields(clazz);
		printStatus("\nMethods", true);
		printAllMethods(clazz);
	}
	
	private void printAllFields(Class clazz) {
		//Class<?> clazz = o.getClass();
		
		/*
		Class<?> c = o.getClass();
		try {
			while (!c.equals(Object.class)) {
				final Field[] fields = c.getDeclaredFields();
				for (int i = 0; i < fields.length; ++i) {
					if (fieldName.equals(fields[i].getName())) {
						fields[i].setAccessible(true);
						Object ret = fields[i].get(o);
						try {
							return clazz.cast(ret);
						} catch (ClassCastException e) {
							return null;
						}
					}
				}
				c = c.getSuperclass();
			}
		} catch (Exception ignored) {}
		return null;
		*/
		
		

		try {
			Field[] fields = clazz.getFields();
	         for(int i = 0; i < fields.length; i++) {
	            printStatus(fields[i].getName(), true);
	            printStatus(fields[i].toString(), true);
	         }
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
	}
	
	
	private void printAllMethods(Class clazz) {
		try {
			Method[] methods = clazz.getMethods();
	         for(int i = 0; i < methods.length; i++) {
	            printStatus(methods[i].getName(), true);
	            printStatus(methods[i].toString(), true);
	         }
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
	}
	
	
	
}





