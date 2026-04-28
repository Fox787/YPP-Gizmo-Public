package main.java.general;

import com.sun.java.accessibility.util.EventQueueMonitor;
import com.sun.java.accessibility.util.GUIInitializedListener;
import com.sun.java.accessibility.util.TopLevelWindowListener;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DRGizmoMain implements TopLevelWindowListener, GUIInitializedListener {
	/** The original work had a Hardcoded Version number and Developer flags
	 *  I've kept them in place only because it's easier to maintain and I may one day want to do some more test features also.
	 */
	public static final double VERSION = 1.0;

	public static final boolean DEVELOPERS = false;

	private static volatile boolean agentLoaded = false;

	public static Preferences prefs;

	protected DRGizmoGUI frame = null;
	Window window = null;
	private boolean attachedMidGame = false;

	public DRGizmoMain() {
		this(false);
	}

	public DRGizmoMain(boolean midGameAttach) {
		this.attachedMidGame = midGameAttach;

		// If attaching mid-game, check for existing windows first
		if (midGameAttach) {
			// Try multiple times with delays to handle race conditions
			for (int attempt = 0; attempt < 5; attempt++) {
				if (EventQueueMonitor.isGUIInitialized()) {
					Window[] windows = Window.getWindows();
					for (Window w : windows) {
						if (w.isDisplayable() && w.getAccessibleContext() != null) {
							String name = w.getAccessibleContext().getAccessibleName();
							if (name != null && name.startsWith("Puzzle Pirates")) {
								window = w;
								break;
							}
						}
					}

					// If we found the window, check if logged in
					if (window != null) {
						String title = window.getAccessibleContext().getAccessibleName();
						if (title != null && title.contains(" on the ")) {
							// Already logged in - show GUI now
							showGizmoGUI();
							return;
						}
						// Found window but not logged in yet - break and use event listener
						break;
					}
				}

				// Wait a bit before retry
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					break;
				}
			}
		}

		// Otherwise, use the normal event-based approach
		EventQueueMonitor.addTopLevelWindowListener(this);
		if (EventQueueMonitor.isGUIInitialized()) {
			initialize();
		} else {
			EventQueueMonitor.addGUIInitializedListener(this);
		}
	}

	private void showGizmoGUI() {
		if (frame != null) return; // Already showing

		prefs = Preferences.userNodeForPackage(DRGizmoMain.class);

		frame = new DRGizmoGUI(window);
		frame.pack();
		frame.setSize(prefs.getInt("width", frame.getMinimumSize().width+50),
				prefs.getInt("height", frame.getHeight()));
		frame.setVisible(true);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				frame = null;
			}
		});
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
							showGizmoGUI();
						}
					}
				});
			}
			return;
		}
	}

	public static void main(String[] args) {
		new DRGizmoMain();
	}

	public void guiInitialized() {}

	public void topLevelWindowCreated(Window w) {
		window = w;
		EventQueueMonitor.removeTopLevelWindowListener(this);
		initialize();
	}

	public void topLevelWindowDestroyed(Window w) {}

	public static void agentmain(String args, Instrumentation inst) {
		/* The VM is already up, GUI may already exist, so start immediately */
		javax.swing.SwingUtilities.invokeLater(() -> new DRGizmoMain(true));
	}

	public static void premain(String args, Instrumentation inst) {
		// When loaded at startup, use normal flow (not mid-game)
		javax.swing.SwingUtilities.invokeLater(DRGizmoMain::new);
	}

	public static class GizmoLauncher {
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
			} else {
				String[] names = list.stream()
						.map(d -> d.displayName() + "  (pid "+d.id()+")")
						.toArray(String[]::new);
				int ix = JOptionPane.showOptionDialog(
						null, "Which client?", "Gizmo",
						JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
						null, names, names[0]);
				if (ix < 0) return;
				pid = list.get(ix).id();
			}

			Path agent = Path.of("GizmoAgent.jar");
			VirtualMachine vm = VirtualMachine.attach(pid);
			vm.loadAgent(agent.toAbsolutePath().toString());
			vm.detach();

			JOptionPane.showMessageDialog(null,
					"Gizmo attached to "+pid, "Done", JOptionPane.INFORMATION_MESSAGE);
		}
	}
}