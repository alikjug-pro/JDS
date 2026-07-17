import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class RetroMusicPlayer extends JPanel {
    private JButton playBtn, pauseBtn, stopBtn, addBtn, removeBtn;
    private JList<String> playlistDisplay;
    private DefaultListModel<String> listModel;
    private JSlider volumeSlider;
    private JLabel statusLabel;

    private ArrayList<File> playlistFiles = new ArrayList<>();
    private int currentTrackIndex = -1;
    private boolean isPlaying = false;
    private boolean isPaused = false;

    private Thread audioThread;
    private Clip wavClip;
    private long wavPausedMicrosecond = 0;

    private JProgressBar progressBar;
    private JLabel timeLabel;
    private boolean isProgressRunning = false;

    public static void launch(JDesktopPane desktopPane, File initialFile) {
        for (JInternalFrame frame : desktopPane.getAllFrames()) {
            if ("Media Player".equals(frame.getTitle())) {
                try {
                    frame.setSelected(true);
                    frame.setIcon(false);
                } catch (Exception ex) {}
                return;
            }
        }

        JInternalFrame playerFrame = new JInternalFrame("Media Player", true, true, true, true);
        RetroMusicPlayer playerPanel = new RetroMusicPlayer();

        playerFrame.setContentPane(playerPanel);
        playerFrame.setSize(900, 250);
        playerFrame.setLocation(100, 100);

        desktopPane.add(playerFrame);
        playerFrame.setVisible(true);

        try {
            playerFrame.setSelected(true);
        } catch (java.beans.PropertyVetoException ex) {
            ex.printStackTrace();
        }

        if (initialFile != null && initialFile.getName().toLowerCase().endsWith(".wav")) {
            playerPanel.addSingleFile(initialFile);
            playerPanel.playlistDisplay.setSelectedIndex(0);
            playerPanel.handlePlay();
        }
    }

    public RetroMusicPlayer() {
        setLayout(new BorderLayout(10, 10));

        // Playlist Layout
        JPanel playlistPanel = new JPanel(new BorderLayout(5, 5));
        playlistPanel.setBorder(BorderFactory.createTitledBorder("Playlist"));
        listModel = new DefaultListModel<>();
        playlistDisplay = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(playlistDisplay);
        scrollPane.setPreferredSize(new Dimension(400, 150));
        playlistPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel playlistButtons = new JPanel(new GridLayout(1, 2, 5, 5));
        addBtn = new JButton("Add");
        removeBtn = new JButton("Remove");
        playlistButtons.add(addBtn);
        playlistButtons.add(removeBtn);
        playlistPanel.add(playlistButtons, BorderLayout.SOUTH);

        // Control Layout
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Controls"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel transportButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        playBtn = new JButton("Play");
        pauseBtn = new JButton("Pause");
        stopBtn = new JButton("Stop");
        transportButtons.add(playBtn);
        transportButtons.add(pauseBtn);
        transportButtons.add(stopBtn);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        controlPanel.add(transportButtons, gbc);

        // NEW: Progress Bar Layout Zone
        JPanel progressTrackPanel = new JPanel(new BorderLayout(5, 0));
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        timeLabel = new JLabel("00:00 / 00:00");
        timeLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        progressTrackPanel.add(progressBar, BorderLayout.CENTER);
        progressTrackPanel.add(timeLabel, BorderLayout.EAST);

        gbc.gridy = 1;
        controlPanel.add(progressTrackPanel, gbc);

        JPanel volumePanel = new JPanel(new BorderLayout(5, 5));
        volumePanel.add(new JLabel("Volume:"), BorderLayout.WEST);
        volumeSlider = new JSlider(0, 100, 80);
        volumePanel.add(volumeSlider, BorderLayout.CENTER);

        gbc.gridy = 2;
        controlPanel.add(volumePanel, gbc);

        statusLabel = new JLabel("Add or Drag WAV files here.", SwingConstants.CENTER);
        gbc.gridy = 3;
        controlPanel.add(statusLabel, gbc);

        add(playlistPanel, BorderLayout.WEST);
        add(controlPanel, BorderLayout.CENTER);

        // Action Hookups
        addBtn.addActionListener(e -> addSongs());
        removeBtn.addActionListener(e -> removeSong());
        playBtn.addActionListener(e -> handlePlay());
        pauseBtn.addActionListener(e -> handlePause());
        stopBtn.addActionListener(e -> handleStop());
        volumeSlider.addChangeListener(e -> adjustVolume());

        // FEATURE 1: Double-Click Support
        playlistDisplay.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = playlistDisplay.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        playlistDisplay.setSelectedIndex(index);
                        handlePlay();
                    }
                }
            }
        });

        // FEATURE 2: Drag and Drop Support
        setupDragAndDrop();
    }

    private void setupDragAndDrop() {
        this.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    boolean addedAny = false;
                    for (File file : files) {
                        if (file.getName().toLowerCase().endsWith(".wav")) {
                            addSingleFile(file);
                            addedAny = true;
                        }
                    }
                    if (!addedAny) {
                        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(RetroMusicPlayer.this),
                            "No valid WAV files found in dropped items.", "Format Warning", JOptionPane.WARNING_MESSAGE);
                    }
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return false;
            }
        });
    }

    private void addSingleFile(File file) {
        playlistFiles.add(file);
        listModel.addElement(file.getName());
    }

    private void addSongs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        int result = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this));
        if (result == JFileChooser.APPROVE_OPTION) {
            for (File file : chooser.getSelectedFiles()) {
                if (file.getName().toLowerCase().endsWith(".wav")) {
                    addSingleFile(file);
                } else {
                    JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                        "Native Java only supports WAV format in this mode.");
                }
            }
        }
    }

    private void removeSong() {
        int selected = playlistDisplay.getSelectedIndex();
        if (selected != -1) {
            if (selected == currentTrackIndex) handleStop();
            playlistFiles.remove(selected);
            listModel.remove(selected);
        }
    }

    private void handlePlay() {
        int selected = playlistDisplay.getSelectedIndex();
        if (selected == -1 && !listModel.isEmpty()) {
            selected = 0;
            playlistDisplay.setSelectedIndex(0);
        }
        if (selected == -1) return;

        if (isPaused && selected == currentTrackIndex) {
            isPaused = false;
            isPlaying = true;
            statusLabel.setText("Playing: " + playlistFiles.get(currentTrackIndex).getName());
            if (wavClip != null) {
                wavClip.setMicrosecondPosition(wavPausedMicrosecond);
                wavClip.start();
                startProgressThread();
            }
            return;
        }

        handleStop();
        currentTrackIndex = selected;
        isPlaying = true;
        isPaused = false;
        statusLabel.setText("Playing: " + playlistFiles.get(currentTrackIndex).getName());

        audioThread = new Thread(() -> {
            try {
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(playlistFiles.get(currentTrackIndex))) {
                    wavClip = AudioSystem.getClip();
                    wavClip.open(ais);
                    adjustVolume();
                    wavClip.start();
                    startProgressThread();

                    while (isPlaying && !isPaused && wavClip.isRunning()) {
                        Thread.sleep(100);
                    }

                    if (isPlaying && !isPaused && !wavClip.isRunning()) {
                        SwingUtilities.invokeLater(this::handleNextTrack);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        audioThread.start();
    }

    private void startProgressThread() {
    if (isProgressRunning) return; // Prevent spawning multiple tracking loops
    isProgressRunning = true;

    new Thread(() -> {
        while (isPlaying && !isPaused && wavClip != null && wavClip.isOpen()) {
            long currentMicro = wavClip.getMicrosecondPosition();
            long totalMicro = wavClip.getMicrosecondLength();

            if (totalMicro > 0) {
                int progressPercent = (int) ((currentMicro * 100) / totalMicro);

                // Format times cleanly into mm:ss
                String currentStr = formatTimeStrings(currentMicro / 1000000);
                String totalStr = formatTimeStrings(totalMicro / 1000000);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(progressPercent);
                    timeLabel.setText(currentStr + " / " + totalStr);
                });
            }

            try {
                Thread.sleep(200); // UI updates 5 times per second for smooth rendering
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        isProgressRunning = false;
    }).start();
}

private String formatTimeStrings(long seconds) {
    long m = seconds / 60;
    long s = seconds % 60;
    return String.format("%02d:%02d", m, s);
}

    private void handlePause() {
        if (!isPlaying || isPaused) return;
        isPaused = true;
        isPlaying = false;
        statusLabel.setText("Paused.");
        if (wavClip != null) {
            wavPausedMicrosecond = wavClip.getMicrosecondPosition();
            wavClip.stop();
        }
    }

    private void handleStop() {
        isPlaying = false;
        isPaused = false;
        wavPausedMicrosecond = 0;
        if (wavClip != null) {
            wavClip.stop();
            wavClip.close();
        }
        statusLabel.setText("Stopped.");

        // Reset progress track layout elements
        progressBar.setValue(0);
        timeLabel.setText("00:00 / 00:00");
    }

        private void handleNextTrack() {
          handleStop();
          if (currentTrackIndex + 1 < playlistFiles.size()) {
            playlistDisplay.setSelectedIndex(currentTrackIndex + 1);
            handlePlay();
          } else {
            statusLabel.setText("Finished entire playlist.");
          }
        }

        private void adjustVolume() {
          if (wavClip != null && wavClip.isOpen()) {
            try {
              FloatControl volumeControl = (FloatControl)
              wavClip.getControl(FloatControl.Type.MASTER_GAIN);
              float sliderVal = volumeSlider.getValue() / 100f;
              float dB = (float) (Math.log(sliderVal == 0 ? 0.0001 : sliderVal) / Math.log(10.0) * 20.0);
              volumeControl.setValue(dB);
            } catch (IllegalArgumentException e) {
              // Volume control configuration unmapped on host device hardware audio profiles
            }
          }
        }
      }
