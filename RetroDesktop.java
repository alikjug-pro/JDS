import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

public class RetroDesktop extends JFrame implements ActionListener {
    private JDesktopPane desktopPane;
    private JMenuBar menuBar;
    private JMenu appsMenu, systemMenu;
    private JMenuItem notepadItem, calcItem, browserItem, viewerItem, playerItem, backgroundImageItem, exitItem; // Added viewerItem

    private Image backgroundImage = null;
    private final Preferences prefs = Preferences.userNodeForPackage(RetroDesktop.class);

    private JPanel statusBar;
    private JLabel statusClockLabel;
    private JLabel statusMemoryLabel;
    private JLabel statusWindowsLabel;
    private Timer sysMonitorTimer;

    public RetroDesktop() {
        setTitle("Java retroOS Virtual Desktop");
        setSize(1376, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

//        setTitle("Java retroOS Virtual Desktop");
//        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 1. Remove standard operating system window borders/titlebars
//        setUndecorated(true);

        // 2. Query system graphics hardware profiles
//        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
//        GraphicsDevice gd = ge.getDefaultScreenDevice();

        // 3. Attempt Exclusive Full-Screen Mode, fallback to extended bounds if denied
//        if (gd.isFullScreenSupported()) {
//            gd.setFullScreenWindow(this);
//        } else {
            // Alternative borderless maximized fallback window
//            setExtendedState(JFrame.MAXIMIZED_BOTH);
//            setSize(Toolkit.getDefaultToolkit().getScreenSize());
//        }


        loadSavedWallpaper();

        desktopPane = new JDesktopPane() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                }
            }
        };

        desktopPane.setBackground(Config.RETRO_TEAL);
        add(desktopPane, BorderLayout.CENTER);

        try {
            java.net.URL dukeUrl = new java.net.URL(Config.DUKE_URL);
            ImageIcon dukeIcon = new ImageIcon(dukeUrl);
            JLabel dukeLabel = new JLabel(dukeIcon);
            dukeLabel.setBounds(200, 200, 750, 400);
            desktopPane.add(dukeLabel, JDesktopPane.DEFAULT_LAYER);
        } catch (Exception ex) {
            System.err.println("Could not load Duke animation: " + ex.getMessage());
        }

        buildStatusBar();

        desktopPane.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) { updateActiveWindowsCount(); }
            @Override
            public void componentRemoved(ContainerEvent e) { updateActiveWindowsCount(); }
        });

        // Detect OS type to apply native feeling system hotkey triggers
        boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
        int modifier = isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK;

        // Register the "Run Dialog" trigger on your main workspace panel canvas
        desktopPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                   .put(KeyStroke.getKeyStroke(KeyEvent.VK_R, modifier), "openRunDialog");

        desktopPane.getActionMap().put("openRunDialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showRunCommandDialog(desktopPane);
            }
        });

        buildMenus();
        RetroNotepad.launch(desktopPane, "Untitled.txt");
    }

    private void buildStatusBar() {
        statusBar = new JPanel(new GridBagLayout());
        statusBar.setPreferredSize(new Dimension(1024, 26));
        statusBar.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, Color.LIGHT_GRAY));
        statusBar.setBackground(UIManager.getColor("Panel.background"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        statusWindowsLabel = new JLabel(" Tasks Active: 0", SwingConstants.LEFT);
        statusWindowsLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        statusWindowsLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        gbc.weightx = 0.5;
        gbc.gridx = 0;
        statusBar.add(statusWindowsLabel, gbc);

        statusMemoryLabel = new JLabel(" RAM: 0 MB / 0 MB ", SwingConstants.CENTER);
        statusMemoryLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        statusMemoryLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        gbc.weightx = 0.25;
        gbc.gridx = 1;
        statusBar.add(statusMemoryLabel, gbc);

        statusClockLabel = new JLabel(" 00:00:00 AM ", SwingConstants.CENTER);
        statusClockLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        statusClockLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        gbc.weightx = 0.25;
        gbc.gridx = 2;
        statusBar.add(statusClockLabel, gbc);

        add(statusBar, BorderLayout.SOUTH);

        sysMonitorTimer = new Timer(1000, e -> {
            updateClock();
            updateMemoryStats();
        });
        sysMonitorTimer.start();

        updateClock();
        updateMemoryStats();
    }

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a");
        statusClockLabel.setText(" " + sdf.format(new Date()) + " ");
    }

    private void updateMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long allocatedMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = allocatedMemory - freeMemory;

        statusMemoryLabel.setText(" Java Heap Used: " + usedMemory + "MB / " + maxMemory + "MB ");
    }

    private void updateActiveWindowsCount() {
        int count = 0;
        for (JInternalFrame frame : desktopPane.getAllFrames()) {
            if (frame.getTitle() != null) count++;
        }
        statusWindowsLabel.setText(" Tasks Active: " + count);
    }

    private void buildMenus() {
        menuBar = new JMenuBar();

        // --- SYSTEM MENU ---
        systemMenu = new JMenu("System");
        backgroundImageItem = new JMenuItem("Background"); // Moved item reference block
        exitItem = new JMenuItem("Shutdown OS");

        backgroundImageItem.addActionListener(this);
        exitItem.addActionListener(e -> {
            sysMonitorTimer.stop();
            System.exit(0);
        });

        systemMenu.add(backgroundImageItem); // Added to system branch structure hierarchies
        systemMenu.addSeparator();
        systemMenu.add(exitItem);

        // --- APPS MENU ---
        appsMenu = new JMenu("Apps");
        notepadItem = new JMenuItem("Text Editor");
        calcItem = new JMenuItem("Calculator");
        browserItem = new JMenuItem("File Explorer");
        viewerItem = new JMenuItem("Image Viewer"); // New image viewer item assignment
        playerItem = new JMenuItem("Music Player");

        notepadItem.addActionListener(this);
        calcItem.addActionListener(this);
        browserItem.addActionListener(this);
        viewerItem.addActionListener(this); // Coupled action listener routing
        playerItem.addActionListener(this);

        appsMenu.add(notepadItem);
        appsMenu.add(calcItem);
        appsMenu.add(browserItem);
        appsMenu.add(viewerItem); // Added image viewer app entry option layer
        appsMenu.add(playerItem);

        menuBar.add(systemMenu);
        menuBar.add(appsMenu);
        setJMenuBar(menuBar);
    }

    private void loadSavedWallpaper() {
        String savedPath = prefs.get(Config.PREF_BG_KEY, null);
        if (savedPath != null) {
            File wallpaperFile = new File(savedPath);
            if (wallpaperFile.exists()) {
                try {
                    this.backgroundImage = ImageIO.read(wallpaperFile);
                } catch (IOException e) {
                    System.err.println("Could not auto-load saved background path: " + savedPath);
                }
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        switch (command) {
            case "Text Editor":
                RetroNotepad.launch(desktopPane, "Untitled.txt");
                break;
            case "Calculator":
                RetroCalculator.launch(desktopPane);
                break;
            case "File Explorer":
                RetroBrowser.launch(desktopPane);
                break;
            case "Image Viewer":
                RetroImageViewer.launch(desktopPane, null); // Launches blank image canvas
                break;
            case "Music Player":
                RetroMusicPlayer.launch(desktopPane, null);
                break;
            case "Background":
                openBackgroundImagePicker(); // Handles moved trigger call pipelines
                break;
        }
    }

    private static void showRunCommandDialog(JDesktopPane desktopPane) {
        // Prevent opening duplicate dialog windows on screen workspace
        for (JInternalFrame frame : desktopPane.getAllFrames()) {
            if ("Run".equals(frame.getTitle())) {
                try { frame.setSelected(true); } catch (Exception ex) {}
                return;
            }
        }

        JInternalFrame runFrame = new JInternalFrame("Run", false, true, false, false);
        runFrame.setSize(380, 140);
        runFrame.setLocation(30, desktopPane.getHeight() - 190); // Position neatly near bottom left
        runFrame.setLayout(new BorderLayout(10, 10));
        ((JPanel)runFrame.getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Prompt Information row layout
        JPanel centerPanel = new JPanel(new BorderLayout(10, 5));
        JLabel iconPlaceholder = new JLabel("⚙");
        iconPlaceholder.setFont(new Font("Dialog", Font.PLAIN, 28));

        JPanel inputPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        JLabel promptLabel = new JLabel("Type the name of a program, folder, or document to open:");
        promptLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        JTextField cmdField = new JTextField();

        inputPanel.add(promptLabel);
        inputPanel.add(cmdField);
        centerPanel.add(iconPlaceholder, BorderLayout.WEST);
        centerPanel.add(inputPanel, BorderLayout.CENTER);
        runFrame.add(centerPanel, BorderLayout.CENTER);

        // Action controls tray tray layout buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        runFrame.add(buttonPanel, BorderLayout.SOUTH);

        // Core Trigger Logic
        Runnable launchAction = () -> {
            String command = cmdField.getText().trim();
            if (!command.isEmpty()) {
                executeTerminalCommandString(desktopPane, command);
                runFrame.dispose();
            }
        };

        okBtn.addActionListener(e -> launchAction.run());
        cmdField.addActionListener(e -> launchAction.run()); // Triggers when pressing Enter in textfield
        cancelBtn.addActionListener(e -> runFrame.dispose());

        desktopPane.add(runFrame);
        runFrame.setVisible(true);
        try { runFrame.setSelected(true); cmdField.requestFocusInWindow(); } catch (Exception ex) {}
    }

    private static void executeTerminalCommandString(JDesktopPane desktopPane, String rawCommand) {
        new Thread(() -> {
            try {
                ProcessBuilder pb;
                boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");

                // Route through the system shell framework so built-in terminal calls work
                if (isMac) {
                    pb = new ProcessBuilder("bash", "-c", rawCommand);
                } else {
                    pb = new ProcessBuilder("cmd.exe", "/c", rawCommand);
                }

                pb.redirectErrorStream(true);
                Process process = pb.start();

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.out.println("Command executed with return diagnostic trace flag code: " + exitCode);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(desktopPane,
                        "System environment failed to resolve command: " + rawCommand,
                        "Run Error", JOptionPane.ERROR_MESSAGE)
                );
            }
        }).start();
    }

    private void openBackgroundImagePicker() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Custom Background Wallpaper");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Images (JPG, PNG, GIF)", "jpg", "jpeg", "png", "gif");
        fileChooser.setFileFilter(filter);

        int choice = fileChooser.showOpenDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                backgroundImage = ImageIO.read(file);
                prefs.put(Config.PREF_BG_KEY, file.getAbsolutePath());
                desktopPane.repaint();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to load selected background.", "Disk Read Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
