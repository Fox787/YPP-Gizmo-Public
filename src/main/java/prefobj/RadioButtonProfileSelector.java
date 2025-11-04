package main.java.prefobj;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.prefs.*;

/**
 * A panel that allows users to create, switch between, and delete preference profiles.
 * Each profile stores a complete set of application preferences separately.
 */
public class RadioButtonProfileSelector extends JPanel {
    private ButtonGroup profileGroup;
    private JPanel radioPanel;
    private JButton btnNew, btnRename, btnDelete;
    private Preferences globalPrefs;
    private final String PROFILE_ROOT = "profiles";
    private final String CURRENT_PROFILE_KEY = "currentProfile";
    private final String DEFAULT_PROFILE = "Default";
    private String selectedProfile = null;
    private ProfileChangeListener changeListener;

    /**
     * Interface for listening to profile changes
     */
    public interface ProfileChangeListener {
        /**
         * Called when a profile is selected (switched to)
         * @param profileName the name of the newly selected profile
         */
        void onProfileChanged(String profileName);

        /**
         * Called when a profile is deleted
         * @param profileName the name of the deleted profile
         */
        void onProfileDeleted(String profileName);
    }

    /**
     * Creates a new profile selector using the given preferences root
     * @param globalPrefs the global preferences node (typically from Preferences.userNodeForPackage)
     */
    public RadioButtonProfileSelector(Preferences globalPrefs) {
        this.globalPrefs = globalPrefs;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Profiles"));

        radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.Y_AXIS));
        profileGroup = new ButtonGroup();

        btnNew = new JButton("New");
        btnRename = new JButton("Rename");
        btnDelete = new JButton("Delete");


        JPanel crudPanel = new JPanel();
        crudPanel.setLayout(new BoxLayout(crudPanel, BoxLayout.Y_AXIS));
        crudPanel.add(btnNew);
        crudPanel.add(btnRename);
        crudPanel.add(btnDelete);

        // Combine radioPanel and crudPanel side by side
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(radioPanel, BorderLayout.WEST);
        mainPanel.add(crudPanel, BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);


        /*
        JPanel crudPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        crudPanel.add(btnNew);
        crudPanel.add(btnRename);
        crudPanel.add(btnDelete);

        add(radioPanel, BorderLayout.CENTER);
        add(crudPanel, BorderLayout.SOUTH);
         */

        btnNew.addActionListener(e -> createProfile());
        btnRename.addActionListener(e -> renameProfile());
        btnDelete.addActionListener(e -> deleteProfile());

        // Load the last selected profile or create default
        selectedProfile = globalPrefs.get(CURRENT_PROFILE_KEY, null);
        if (selectedProfile == null) {
            createDefaultProfile();
        }

        loadProfiles();
    }

    /**
     *  Renames the Currently selected Profile
     */
    private void renameProfile() {
        if (selectedProfile == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "No profile selected.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        String newName = JOptionPane.showInputDialog(
                this,
                "Enter new name for profile '" + selectedProfile + "':",
                "Rename Profile",
                JOptionPane.QUESTION_MESSAGE
        );

        if (newName == null || newName.trim().isEmpty()) {
            return; // Cancelled or empty
        }

        newName = newName.trim();

        if (newName.equals(selectedProfile)) {
            return; // No change
        }

        try {
            Preferences profileRoot = globalPrefs.node(PROFILE_ROOT);

            if (profileRoot.nodeExists(newName)) {
                JOptionPane.showMessageDialog(
                        this,
                        "A profile with that name already exists.",
                        "Error",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            // Copy preferences from old to new
            Preferences oldNode = profileRoot.node(selectedProfile);
            Preferences newNode = profileRoot.node(newName);

            for (String key : oldNode.keys()) {
                newNode.put(key, oldNode.get(key, null));
            }

            newNode.flush();
            oldNode.removeNode();
            profileRoot.flush();

            selectedProfile = newName;
            globalPrefs.put(CURRENT_PROFILE_KEY, newName);
            globalPrefs.flush();

            loadProfiles();

            if (changeListener != null) {
                changeListener.onProfileDeleted(selectedProfile); // old name
                changeListener.onProfileChanged(newName);
            }

        } catch (BackingStoreException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Error renaming profile: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Sets the listener for profile change events
     */
    public void setProfileChangeListener(ProfileChangeListener listener) {
        this.changeListener = listener;
    }

    /**
     * Gets the currently selected profile name
     */
    public String getSelectedProfile() {
        return selectedProfile;
    }

    /**
     * Gets the Preferences node for the currently selected profile
     */
    public Preferences getCurrentProfilePrefs() {
        if (selectedProfile == null) {
            return null;
        }
        return globalPrefs.node(PROFILE_ROOT).node(selectedProfile);
    }

    /**
     * Creates the default profile if it doesn't exist
     */
    private void createDefaultProfile() {
        try {
            Preferences profileRoot = globalPrefs.node(PROFILE_ROOT);
            if (!profileRoot.nodeExists(DEFAULT_PROFILE)) {
                profileRoot.node(DEFAULT_PROFILE);
                profileRoot.flush();
            }
            selectedProfile = DEFAULT_PROFILE;
            globalPrefs.put(CURRENT_PROFILE_KEY, DEFAULT_PROFILE);
            globalPrefs.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads and displays all available profiles
     */
    private void loadProfiles() {
        radioPanel.removeAll();
        profileGroup = new ButtonGroup();

        try {
            Preferences profileRoot = globalPrefs.node(PROFILE_ROOT);
            String[] profiles = profileRoot.childrenNames();

            // If no profiles exist, create default
            if (profiles.length == 0) {
                createDefaultProfile();
                profiles = new String[] { DEFAULT_PROFILE };
            }

            for (String profile : profiles) {
                JRadioButton rb = new JRadioButton(profile);
                rb.addActionListener(e -> selectProfile(profile));
                profileGroup.add(rb);
                radioPanel.add(rb);

                // Select the current profile
                if (profile.equals(selectedProfile)) {
                    rb.setSelected(true);
                }
            }
        } catch (BackingStoreException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error loading profiles: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }

        radioPanel.revalidate();
        radioPanel.repaint();
    }

    /**
     * Switches to the specified profile
     */
    private void selectProfile(String profile) {
        if (profile.equals(selectedProfile)) {
            return; // Already selected
        }

        selectedProfile = profile;

        try {
            globalPrefs.put(CURRENT_PROFILE_KEY, profile);
            globalPrefs.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }

        // Notify listener
        if (changeListener != null) {
            changeListener.onProfileChanged(profile);
        }
    }

    /**
     * Creates a new profile with the given name
     */
    private void createProfile() {
        String name = JOptionPane.showInputDialog(
                this,
                "Enter new profile name:",
                "New Profile",
                JOptionPane.QUESTION_MESSAGE
        );

        if (name == null || name.trim().isEmpty()) {
            return; // User cancelled or entered empty name
        }

        name = name.trim();

        try {
            Preferences profileRoot = globalPrefs.node(PROFILE_ROOT);

            // Check if profile already exists
            if (profileRoot.nodeExists(name)) {
                JOptionPane.showMessageDialog(
                        this,
                        "A profile with that name already exists.",
                        "Profile Exists",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            // Create new profile node
            Preferences newProfile = profileRoot.node(name);
            newProfile.flush();

            // Switch to new profile
            selectedProfile = name;
            globalPrefs.put(CURRENT_PROFILE_KEY, name);
            globalPrefs.flush();

            loadProfiles();

            // Notify listener
            if (changeListener != null) {
                changeListener.onProfileChanged(name);
            }

        } catch (BackingStoreException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Error creating profile: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Deletes the currently selected profile
     */
    private void deleteProfile() {
        if (selectedProfile == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "No profile selected.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Don't allow deleting the last profile
        try {
            String[] profiles = globalPrefs.node(PROFILE_ROOT).childrenNames();
            if (profiles.length <= 1) {
                JOptionPane.showMessageDialog(
                        this,
                        "Cannot delete the last profile.",
                        "Error",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete profile '" + selectedProfile + "'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        String deletedProfile = selectedProfile;

        try {
            Preferences profileRoot = globalPrefs.node(PROFILE_ROOT);
            profileRoot.node(selectedProfile).removeNode();
            profileRoot.flush();

            // Switch to first available profile
            String[] remainingProfiles = profileRoot.childrenNames();
            if (remainingProfiles.length > 0) {
                selectedProfile = remainingProfiles[0];
                globalPrefs.put(CURRENT_PROFILE_KEY, selectedProfile);
                globalPrefs.flush();
            } else {
                // This shouldn't happen, but create default if it does
                createDefaultProfile();
            }

            loadProfiles();

            // Notify listener
            if (changeListener != null) {
                changeListener.onProfileDeleted(deletedProfile);
                changeListener.onProfileChanged(selectedProfile);
            }

        } catch (BackingStoreException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Error deleting profile: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * For standalone testing
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Profile Selector Test");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            Preferences testPrefs = Preferences.userRoot().node("testApp");
            RadioButtonProfileSelector selector = new RadioButtonProfileSelector(testPrefs);

            selector.setProfileChangeListener(new ProfileChangeListener() {
                @Override
                public void onProfileChanged(String profileName) {
                    System.out.println("Profile changed to: " + profileName);
                }

                @Override
                public void onProfileDeleted(String profileName) {
                    System.out.println("Profile deleted: " + profileName);
                }

            });

            frame.add(selector);
            frame.setSize(300, 400);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}