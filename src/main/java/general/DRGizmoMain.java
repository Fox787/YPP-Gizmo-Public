package main.java.general;

import java.awt.Window;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.java.accessibility.util.EventQueueMonitor;
import com.sun.java.accessibility.util.GUIInitializedListener;
import com.sun.java.accessibility.util.TopLevelWindowListener;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import javax.swing.*;

public class DRGizmoMain implements TopLevelWindowListener, GUIInitializedListener {
	public static final double VERSION = 1.0;

	public static final boolean DEVELOPERS = false;
	
	public static Preferences prefs;
	
	protected DRGizmoGUI frame = null;
	Window window = null;
	
	public DRGizmoMain() {
		EventQueueMonitor.addTopLevelWindowListener(this);
		if (EventQueueMonitor.isGUIInitialized()) {
			initialize();
		} else {
			EventQueueMonitor.addGUIInitializedListener(this);
		}
	}
	
	public void initialize() {

		if (window != null) {
			if (window.getAccessibleContext().getAccessibleName().equals("Puzzle Pirates")) {
				
				window.addPropertyChangeListener(new PropertyChangeListener() {
					Pattern title = Pattern.compile("^Puzzle Pirates - (\\w+) on the (\\w+) ocean$");
					@Override
					public void propertyChange(PropertyChangeEvent evt) {
						Matcher matcher = title.matcher(evt.getNewValue().toString());
						if (matcher.find() && frame == null) {
							//the frame has to be made after we log in because for some reason on java 7 and 8
							//the look and feel of JCheckBox is not initialized until after we log in.
							//probably because it is never used until then.
							prefs = Preferences.userNodeForPackage(DRGizmoMain.class);
							
							frame = new DRGizmoGUI(window);
							frame.pack();
							frame.setSize(prefs.getInt("width", frame.getMinimumSize().width+50), 
									prefs.getInt("height",frame.getHeight()));
							frame.setVisible(true);
							frame.addWindowListener(new java.awt.event.WindowAdapter() {
							    @Override
							    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
							    	frame = null;
							    }
							});
						}
					}
				});
			}
			return;
		}
	}

	public static void main(String[] args) {new DRGizmoMain();}
	
	public void guiInitialized() {}

	public void topLevelWindowCreated(Window w) {
		window = w;
		EventQueueMonitor.removeTopLevelWindowListener(this);
		initialize();
	}

	public void topLevelWindowDestroyed(Window w) {}

	public static void agentmain(String args, Instrumentation inst) {
		/* The VM is already up, GUI may already exist, so start immediately */
		javax.swing.SwingUtilities.invokeLater(DRGizmoMain::new);
	}

	public static void premain(String args, Instrumentation inst) {
		agentmain(args, inst);          // same code for both paths
	}

	public class GizmoLauncher {
		public static void main(String[] args) throws Exception {
			List<VirtualMachineDescriptor> list =
					VirtualMachine.list().stream()
							.filter(d -> d.displayName().contains("Puzzle Pirates"))
							.toList();

			if (list.isEmpty()) {
				JOptionPane.showMessageDialog(null,
						"No Puzzle Pirates client found.\nStart the game first.",
						"Gizmo", JOptionPane.ERROR_MESSAGE);
				return;
			}

			String pid;
			if (list.size() == 1) {
				pid = list.get(0).id();
			} else {                       // several clients – let user choose
				String[] names = list.stream()
						.map(d -> d.displayName() + "  (pid "+d.id()+")")
						.toArray(String[]::new);
				int ix = JOptionPane.showOptionDialog(
						null, "Which client?", "Gizmo",
						JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
						null, names, names[0]);
				if (ix < 0) return;        // cancelled
				pid = list.get(ix).id();
			}

			// copy the agent jar out of our own executable (or use the path we shipped)
			Path agent = Path.of("GizmoAgent.jar");   // shipped next to exe
			VirtualMachine vm = VirtualMachine.attach(pid);
			vm.loadAgent(agent.toAbsolutePath().toString());
			vm.detach();

			JOptionPane.showMessageDialog(null,
					"Gizmo attached to "+pid, "Done", JOptionPane.INFORMATION_MESSAGE);
		}
	}
}
