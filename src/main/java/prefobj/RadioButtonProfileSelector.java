package main.java.prefobj;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.*;

public class RadioButtonProfileSelector extends JPanel {
    private ButtonGroup profileGroup;
    private JPanel radioPanel;
    private JButton btnNew, btnRename, btnDelete;
    private Preferences prefs = Preferences.userNodeForPackage(getClass());
    private final String PROFILE_ROOT = "profiles";
    private String selectedProfile = null;

    public RadioButtonProfileSelector() {
        setLayout(new BorderLayout());
        radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.Y_AXIS));
        profileGroup = new ButtonGroup();

        btnNew = new JButton("New");
        //btnRename = new JButton("Rename");
        btnDelete = new JButton("Delete");

        JPanel crudPanel = new JPanel(new FlowLayout());
        crudPanel.add(btnNew);
        //crudPanel.add(btnRename);
        crudPanel.add(btnDelete);

        add(new JLabel("Profiles:"), BorderLayout.NORTH);
        add(radioPanel, BorderLayout.CENTER);
        add(crudPanel, BorderLayout.SOUTH);

        loadProfiles();

        btnNew.addActionListener(e -> createProfile());
        //btnRename.addActionListener(e -> renameProfile());
        btnDelete.addActionListener(e -> deleteProfile());
    }

    private void loadProfiles() {
        radioPanel.removeAll();
        profileGroup = new ButtonGroup();
        try {
            String[] profiles = prefs.node(PROFILE_ROOT).childrenNames();
            for (String profile : profiles) {
                JRadioButton rb = new JRadioButton(profile);
                rb.addActionListener(e -> selectProfile(profile));
                profileGroup.add(rb);
                radioPanel.add(rb);

                // Select first profile by default
                if (selectedProfile == null || selectedProfile.equals(profile)) {
                    rb.setSelected(true);
                    selectedProfile = profile;
                }
            }
        } catch (Exception ignored) {}
        radioPanel.revalidate();
        radioPanel.repaint();
    }

    private void selectProfile(String profile) {
        selectedProfile = profile;
        // TODO: Load settings for selectedProfile
        JOptionPane.showMessageDialog(this, "Loaded profile: " + profile);
    }

    private void createProfile() {
        String name = JOptionPane.showInputDialog(this, "Enter new profile name:");
        if (name != null && !name.isEmpty()) {
            try {
                prefs.node(PROFILE_ROOT).node(name);
                selectedProfile = name;
                loadProfiles();
            } catch (Exception ignored) {}
        }
    }

    private void renameProfile() {
        if (selectedProfile != null) {
            String newName = JOptionPane.showInputDialog(this, "Rename profile:", selectedProfile);
            if (newName != null && !newName.equals(selectedProfile)) {
                try {
                    Preferences root = prefs.node(PROFILE_ROOT);
                    Preferences oldNode = root.node(selectedProfile);
                    Preferences newNode = root.node(newName);
                    for (String key : oldNode.keys()) {
                        newNode.put(key, oldNode.get(key, null));
                    }
                    oldNode.removeNode();
                    selectedProfile = newName;
                    loadProfiles();
                } catch (Exception ignored) {}
            }
        }
    }

    private void deleteProfile() {
        if (selectedProfile != null) {
            int confirm = JOptionPane.showConfirmDialog(this, "Delete profile: " + selectedProfile + "?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    prefs.node(PROFILE_ROOT).node(selectedProfile).removeNode();
                    selectedProfile = null;
                    loadProfiles();
                } catch (Exception ignored) {}
            }
        }
    }

    // For standalone testing
    public static void main(String[] args) {
        JFrame frame = new JFrame("RadioButton Profile Selector Example");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new RadioButtonProfileSelector());
        frame.setSize(400, 300);
        frame.setVisible(true);
    }
}