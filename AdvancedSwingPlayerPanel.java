import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;

// Changed from JFrame to JPanel so it can be embedded anywhere!
public class AdvancedSwingPlayerPanel extends JPanel {
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

    public AdvancedSwingPlayerPanel() {
        // Use BorderLayout for the panel itself
        setLayout(new BorderLayout(10, 10));

        // 1. Playlist Panel (Left Side)
        JPanel playlistPanel = new JPanel(new BorderLayout(5, 5));
        playlistPanel.setBorder(BorderFactory.createTitledBorder("Playlist"));
        listModel = new DefaultListModel<>();
        playlistDisplay = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(playlistDisplay);
        scrollPane.setPreferredSize(new Dimension(160, 180));
        playlistPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel playlistButtons = new JPanel(new GridLayout(1, 2, 5, 5));
        addBtn = new JButton("Add");
        removeBtn = new JButton("Remove");
        playlistButtons.add(addBtn);
        playlistButtons.add(removeBtn);
        playlistPanel.add(playlistButtons, BorderLayout.SOUTH);

        // 2. Controls Panel (Right Side)
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Controls"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel transportButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        playBtn = new JButton("Play");
        pauseBtn = new JButton("Pause");
        stopBtn = new JButton("Stop");
        transportButtons.add(playBtn);
        transportButtons.add(pauseBtn);
        transportButtons.add(stopBtn);

        gbc.gridx = 0; gbc.gridy = 0;
        controlPanel.add(transportButtons, gbc);

        JPanel volumePanel = new JPanel(new BorderLayout(5, 5));
        volumePanel.add(new JLabel("Vol:"), BorderLayout.WEST);
        volumeSlider = new JSlider(0, 100, 80);
        volumePanel.add(volumeSlider, BorderLayout.CENTER);

        gbc.gridy = 1;
        controlPanel.add(volumePanel, gbc);

        statusLabel = new JLabel("Load WAV files.", SwingConstants.CENTER);
        gbc.gridy = 2;
        controlPanel.add(statusLabel, gbc);

        // Add both sections to the main Panel layout
        add(playlistPanel, BorderLayout.WEST);
        add(controlPanel, BorderLayout.CENTER);

        // Wiring standard actions
        addBtn.addActionListener(e -> addSongs());
        removeBtn.addActionListener(e -> removeSong());
        playBtn.addActionListener(e -> handlePlay());
        pauseBtn.addActionListener(e -> handlePause());
        stopBtn.addActionListener(e -> handleStop());
        volumeSlider.addChangeListener(e -> adjustVolume());
    }

    private void addSongs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            for (File file : chooser.getSelectedFiles()) {
                if (file.getName().toLowerCase().endsWith(".wav")) {
                    playlistFiles.add(file);
                    listModel.addElement(file.getName());
                } else {
                    JOptionPane.showMessageDialog(this, "WAV files only for this native template.");
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
    }

    private void handleNextTrack() {
        handleStop();
        if (currentTrackIndex + 1 < playlistFiles.size()) {
            playlistDisplay.setSelectedIndex(currentTrackIndex + 1);
            handlePlay();
        } else {
            statusLabel.setText("Playlist finished.");
        }
    }

    private void adjustVolume() {
        if (wavClip != null && wavClip.isOpen()) {
            try {
                FloatControl volumeControl = (FloatControl) wavClip.getControl(FloatControl.Type.MASTER_GAIN);
                float sliderVal = volumeSlider.getValue() / 100f;
                float dB = (float) (Math.log(sliderVal == 0 ? 0.0001 : sliderVal) / Math.log(10.0) * 20.0);
                volumeControl.setValue(dB);
            } catch (IllegalArgumentException e) {
                // Volume controls unmapped on host device
            }
        }
    }
}
