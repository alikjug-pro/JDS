import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;

public class AdvancedSwingPlayer extends JFrame {
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

    public AdvancedSwingPlayer() {
        setTitle("Native Java Swing Music Player (WAV Only)");
        setSize(500, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Playlist Layout
        JPanel playlistPanel = new JPanel(new BorderLayout(5, 5));
        playlistPanel.setBorder(BorderFactory.createTitledBorder("Playlist"));
        listModel = new DefaultListModel<>();
        playlistDisplay = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(playlistDisplay);
        scrollPane.setPreferredSize(new Dimension(180, 200));
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
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel transportButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        playBtn = new JButton("Play");
        pauseBtn = new JButton("Pause");
        stopBtn = new JButton("Stop");
        transportButtons.add(playBtn);
        transportButtons.add(pauseBtn);
        transportButtons.add(stopBtn);

        gbc.gridx = 0; gbc.gridy = 0;
        controlPanel.add(transportButtons, gbc);

        JPanel volumePanel = new JPanel(new BorderLayout(5, 5));
        volumePanel.add(new JLabel("Volume:"), BorderLayout.WEST);
        volumeSlider = new JSlider(0, 100, 80);
        volumePanel.add(volumeSlider, BorderLayout.CENTER);

        gbc.gridy = 1;
        controlPanel.add(volumePanel, gbc);

        statusLabel = new JLabel("Add WAV files to get started.", SwingConstants.CENTER);
        gbc.gridy = 2;
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
                    JOptionPane.showMessageDialog(this, "Native Java only supports WAV format in this mode.");
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
        // Keeps the UI processing while file stream plays
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
            statusLabel.setText("Finished entire playlist.");
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
                // Volume control configuration unmapped on host device hardware audio profiles
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AdvancedSwingPlayer().setVisible(true));
    }
}
