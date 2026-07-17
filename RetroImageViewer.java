import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import javax.imageio.ImageIO;

public class RetroImageViewer {
    public static void launch(JDesktopPane desktopPane, File initialFile) {
        JInternalFrame frame = new JInternalFrame("Image Viewer", true, true, true, true);
        frame.setSize(500, 400);
        frame.setLocation(120, 120);
        frame.setLayout(new BorderLayout());

        // Custom image canvas component
        final Image[] activeImage = { null };
        JPanel canvasPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (activeImage[0] != null) {
                    // Smooth scaling aspect ratio calculations
                    int imgW = activeImage[0].getWidth(this);
                    int imgH = activeImage[0].getHeight(this);
                    double currentViewRatio = (double) getWidth() / getHeight();
                    double imageActualRatio = (double) imgW / imgH;

                    int renderW = getWidth();
                    int renderH = getHeight();
                    if (currentViewRatio > imageActualRatio) {
                        renderW = (int) (getHeight() * imageActualRatio);
                    } else {
                        renderH = (int) (getWidth() / imageActualRatio);
                    }
                    int renderX = (getWidth() - renderW) / 2;
                    int renderY = (getHeight() - renderH) / 2;
                    g.drawImage(activeImage[0], renderX, renderY, renderW, renderH, this);
                }
            }
        };
        canvasPanel.setBackground(Color.DARK_GRAY);
        frame.add(canvasPanel, BorderLayout.CENTER);

        // Control Panel
        JPanel controlPanel = new JPanel(new BorderLayout(5, 5));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JButton openButton = new JButton("Open Image File...");
        JLabel infoLabel = new JLabel(" No image loaded ", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        infoLabel.setBorder(BorderFactory.createLoweredBevelBorder());

        controlPanel.add(openButton, BorderLayout.WEST);
        controlPanel.add(infoLabel, BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.SOUTH);

        // Functional loading block wrapper
        Runnable loaderWorker = () -> {
            if (initialFile != null && initialFile.exists()) {
                try {
                    activeImage[0] = ImageIO.read(initialFile);
                    frame.setTitle("Image Viewer - " + initialFile.getName());
                    infoLabel.setText(String.format(" Path: %s (%dx%d)", initialFile.getName(), activeImage[0].getWidth(null), activeImage[0].getHeight(null)));
                    canvasPanel.repaint();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Failed to decode target image matrix content.", "Format Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        // Open Dialog Event Listener
        openButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Open System Image File");
            chooser.setFileFilter(new FileNameExtensionFilter("Images (JPG, PNG, GIF)", "jpg", "jpeg", "png", "gif"));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                // Point active pointers to the chosen file directory object references
                File selected = chooser.getSelectedFile();
                try {
                    activeImage[0] = ImageIO.read(selected);
                    frame.setTitle("Image Viewer - " + selected.getName());
                    infoLabel.setText(String.format(" Path: %s (%dx%d)", selected.getName(), activeImage[0].getWidth(null), activeImage[0].getHeight(null)));
                    canvasPanel.repaint();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error loading image file structure.", "File Access Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Initialize target file pointers if supplied externally
        if (initialFile != null) {
            loaderWorker.run();
        }

        desktopPane.add(frame);
        frame.setVisible(true);
    }
}
