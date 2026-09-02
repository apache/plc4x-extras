package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class ExportDialog extends JDialog{



    private final Project[] projects;
    private JList<Project> projectList;
    private JTextField txtDirectory;
    private JTextField txtFileName;

    public ExportDialog(Project[] projects) {
        this.projects = projects;


        setTitle("Export Physical Model");
        setModal(true);
        setSize(1000, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));


        mainPanel.add(createTopPanel(), BorderLayout.NORTH);
        mainPanel.add(createMiddlePanel(), BorderLayout.CENTER);
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        add(mainPanel);


        if (projects.length > 0) {
            projectList.setSelectedIndex(0);
        }
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("Export to:"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.Y_AXIS));
        JRadioButton rbJson = new JRadioButton("JSON Files ??");
        rbJson.setEnabled(false);
        JRadioButton rbB2mml = new JRadioButton("XML Files (B2MML)");
        rbB2mml.setEnabled(false);
        JRadioButton rbXml = new JRadioButton("AXML Files (Rockwell)", true);

        ButtonGroup group = new ButtonGroup();
        group.add(rbJson);
        group.add(rbB2mml);
        group.add(rbXml);

        radioPanel.add(rbJson);
        radioPanel.add(rbB2mml);
        radioPanel.add(rbXml);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 3;
        gbc.weightx = 0.0;
        topPanel.add(radioPanel, gbc);


        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0;
        topPanel.add(new JLabel("Target Directory:"), gbc);

        gbc.gridx = 2; gbc.weightx = 1.0;

        String defaultDir = System.getProperty("user.home");

        txtDirectory = new JTextField(defaultDir);
        topPanel.add(txtDirectory, gbc);

        gbc.gridx = 3; gbc.weightx = 0;
        JButton btnBrowse = new JButton("...");


        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(txtDirectory.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                txtDirectory.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        topPanel.add(btnBrowse, gbc);

        return topPanel;
    }

    private JPanel createMiddlePanel() {
        JPanel middlePanel = new JPanel(new BorderLayout(10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.add(new JLabel("Select a model"), BorderLayout.NORTH);

        projectList = new JList<>(projects);
        projectList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Project) {
                    setText(((Project) value).getProjectDirectory().getName());
                }
                return this;
            }
        });
        projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        projectList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && projectList.getSelectedValue() != null) {
                Project p = projectList.getSelectedValue();
                txtFileName.setText(p.getProjectDirectory().getName() + ".axml");
            }
        });

        JScrollPane listScrollPane = new JScrollPane(projectList);
        listScrollPane.setPreferredSize(new Dimension(250, 0));
        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        middlePanel.add(leftPanel, BorderLayout.WEST);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Model Details", createDetailsTab());
        middlePanel.add(tabbedPane, BorderLayout.CENTER);

        return middlePanel;
    }

    private JScrollPane createDetailsTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // metadata from ISA-88 project here
        addFormRow(panel, gbc, row++, "Version Number", createUneditableField("1.0"));
        addFormRow(panel, gbc, row++, "Model Type", createUneditableField("MALBEC"));


        txtFileName = createUneditableField("");
        addFormRow(panel, gbc, row++, "Output File Name", txtFileName);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(labelText + " ", SwingConstants.RIGHT), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(field, gbc);
    }

    private JTextField createUneditableField(String text) {
        JTextField tf = new JTextField(text);
        tf.setEditable(false);
        tf.setBackground(new Color(240, 240, 240));
        return tf;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        JButton btnExport = new JButton("Export");
        btnExport.setPreferredSize(new Dimension(100, 26));


        btnExport.addActionListener(this::actionPerformed);

        JButton btnCancel = new JButton("Cancel");
        btnCancel.setPreferredSize(new Dimension(100, 26));
        btnCancel.addActionListener(e -> dispose());

        bottomPanel.add(btnExport);
        bottomPanel.add(btnCancel);

        return bottomPanel;
    }

    private void actionPerformed(ActionEvent e) {
        Project selected = projectList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a project to export.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String exportDir = txtDirectory.getText();


        try {
            File destFile = new File(exportDir, selected.getProjectDirectory().getName() + ".axml");

            if (destFile.exists()) {
                int response = JOptionPane.showConfirmDialog(
                        this,
                        "File '" + destFile.getName() + "' already exist in this location.\nDo you want to overwrite it?",
                        "Confirm overwritting",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (response != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            Plc4xPlantModel pmodel = selected.getLookup().lookup(Plc4xPlantModel.class);
            if (pmodel != null) {
                pmodel.export(destFile.getAbsolutePath());
                JOptionPane.showMessageDialog(this, "Export successful!\nFile: " + destFile.getName(), "Success", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Plc4xPlantModel not found in the selected project.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
}
