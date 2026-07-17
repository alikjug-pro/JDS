import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

public class Main {
    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "false");
        System.setProperty("swing.defaultlaf", "javax.swing.plaf.metal.MetalLookAndFeel");

        try {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (Exception e) {
            System.err.println("Could not enforce Metal Look and Feel.");
        }

        SwingUtilities.invokeLater(() -> new RetroDesktop().setVisible(true));
    }
}
