package general;

import java.awt.Color;
import java.awt.Window;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.sun.java.accessibility.util.EventQueueMonitor;
import com.sun.java.accessibility.util.GUIInitializedListener;
import com.sun.java.accessibility.util.TopLevelWindowListener;

public class DRGizmoMain implements TopLevelWindowListener, GUIInitializedListener {
	public static final double VERSION = 0.06;
	
	//god i'm lazy
	//  set this to true if you're working on this
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

}
