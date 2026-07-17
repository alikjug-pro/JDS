import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;

public class RetroBrowser {
    private static File currentDirectory;
    private static JTable fileTable;
    private static JLabel pathLabel;
    private static DefaultTableModel tableModel;

    public static void launch(JDesktopPane desktopPane) {
        JInternalFrame frame = new JInternalFrame("File Explorer", true, true, true, true);
        frame.setSize(600, 400);
        frame.setLocation(80, 80);
        frame.setLayout(new BorderLayout());

        JPanel navPanel = new JPanel(new BorderLayout(5, 5));
        navPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Create a layout tray for your navigation tools
        JPanel navigationButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton upButton = new JButton("▲ Up");
        JButton runButton = new JButton("🚀 Run App"); // NEW: Dynamic launcher button
        navigationButtons.add(upButton);
        navigationButtons.add(runButton);

        pathLabel = new JLabel();
        pathLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        pathLabel.setFont(new Font("Dialog", Font.PLAIN, 12));

        // Add them to your navigation panel configuration
        navPanel.add(navigationButtons, BorderLayout.WEST);
        navPanel.add(pathLabel, BorderLayout.CENTER);
        frame.add(navPanel, BorderLayout.NORTH);

        String[] columns = {"Name", "Size", "Type", "Modified"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        fileTable = new JTable(tableModel);
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setShowGrid(false);
        fileTable.setIntercellSpacing(new Dimension(0, 0));
        fileTable.setFont(new Font("Dialog", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(fileTable);
        scrollPane.getViewport().setBackground(Color.WHITE);
        frame.add(scrollPane, BorderLayout.CENTER);

        currentDirectory = new File(System.getProperty("user.home"));
        refreshDirectoryListing();

        // Action to go up a folder level
        upButton.addActionListener(e -> {
            File parent = currentDirectory.getParentFile();
            if (parent != null && parent.exists()) {
                currentDirectory = parent;
                refreshDirectoryListing();
            }
        });

        // NEW: Action to execute whatever file path or application is in focus
        runButton.addActionListener(e -> {
            int selectedRow = fileTable.getSelectedRow();
            if (selectedRow != -1) {
                // Execute using the row item highlighted in the table view grid
                executeExternalProcessFromFile(desktopPane, selectedRow);
            } else {
                JOptionPane.showMessageDialog(desktopPane,
                    "Please highlight a file or executable binary from the table list first.",
                    "Run App Context Alert", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    int selectedRow = fileTable.getSelectedRow();
                    if (selectedRow != -1) {
                        executeFileActivation(desktopPane, selectedRow);
                    }
                }
            }
        });

        desktopPane.add(frame);
        frame.setVisible(true);
    }

    private static String getCleanNameFromRow(int row) {
        String rawName = (String) tableModel.getValueAt(row, 0);
        if (rawName.endsWith(" /")) {
            return rawName.substring(0, rawName.length() - 2);
        } else if (rawName.endsWith(" [TXT]")) {
            return rawName.substring(0, rawName.length() - 6);
        } else if (rawName.endsWith(" [JAVA]")) {
            return rawName.substring(0, rawName.length() - 7);
        } else if (rawName.endsWith(" [CLASS]")) {
            return rawName.substring(0, rawName.length() - 8); // [CLASS] is 8 characters
        }
        return rawName;
    }

    private static void executeFileActivation(JDesktopPane desktopPane, int row) {
        String cleanName = getCleanNameFromRow(row);
        File targetFile = new File(currentDirectory, cleanName);

        if (!targetFile.exists()) {
            JOptionPane.showMessageDialog(desktopPane, "File no longer exists: " + cleanName, "File Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (targetFile.isDirectory()) {
            currentDirectory = targetFile;
            refreshDirectoryListing();
        } else if (targetFile.isFile()) {
            String nameLower = targetFile.getName().toLowerCase();

            if (nameLower.endsWith(".txt")) {
              openTextFileInNotepad(desktopPane, targetFile);
            } else if (nameLower.endsWith(".java")) {
              // Compile the file instantly, then automatically try to run it
              compileAndRunJavaSource(desktopPane, targetFile);
            } else if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".gif")) {
                RetroImageViewer.launch(desktopPane, targetFile);
            } else if (nameLower.endsWith(".class")) {
                loadAndRunClassFile(desktopPane, targetFile); // NEW Reflection Launcher Hook
            }
        }
    }

    private static void executeExternalProcessFromFile(JDesktopPane desktopPane, int row) {
        String cleanName = getCleanNameFromRow(row);
        File targetFile = new File(currentDirectory, cleanName);

        if (!targetFile.exists() || targetFile.isDirectory()) {
            JOptionPane.showMessageDialog(desktopPane, "Target item is a folder or missing binary.", "Launch Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Spawn a background thread to prevent the browser window layout from locking up
        new Thread(() -> {
            try {
                ProcessBuilder pb;
                String filename = targetFile.getName().toLowerCase();

                if (filename.endsWith(".class")) {
                    // Extract class name framework boundaries for compiling structures
                    String className = targetFile.getName().substring(0, targetFile.getName().length() - 6);
                    pb = new ProcessBuilder("java", "-cp", ".", className);
                    pb.directory(targetFile.getParentFile());
                } else if (filename.endsWith(".jar")) {
                    // Launch an encapsulated standalone jar application archive packages
                    pb = new ProcessBuilder("java", "-jar", targetFile.getAbsolutePath());
                } else if (System.getProperty("os.name").toLowerCase().contains("mac") && filename.endsWith(".app")) {
                    // Native execution support for system application packages on macOS environment
                    pb = new ProcessBuilder("open", "-a", targetFile.getAbsolutePath());
                } else {
                    // Default system context fallback execution route layout engine setups
                    pb = new ProcessBuilder(targetFile.getAbsolutePath());
                }

                // Redirect error tracking data streams right out to host debugging configurations
                pb.redirectErrorStream(true);
                Process process = pb.start();

                // Wait asynchronously for system lifecycle validation indicators
                int exitStatus = process.waitFor();
                System.out.println("Process execution sequence finished with status code: " + exitStatus);

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(desktopPane,
                        "Failed to execute external system application process:\n" + ex.getMessage(),
                        "OS Environment Engine Exception", JOptionPane.ERROR_MESSAGE)
                );
            }
        }).start();
    }    

    private static void refreshDirectoryListing() {
        pathLabel.setText(" Address: " + currentDirectory.getAbsolutePath());
        tableModel.setRowCount(0);

        File[] list = currentDirectory.listFiles();
        if (list == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm a");

        // Folders
        for (File file : list) {
            if (file.isDirectory() && !file.isHidden()) {
                tableModel.addRow(new Object[]{
                    file.getName() + " /",
                    "<DIR>",
                    "Folder",
                    sdf.format(new Date(file.lastModified()))
                });
            }
        }

        // Files
        for (File file : list) {
            if (file.isFile() && !file.isHidden()) {
                String type = "File";
                String displayName = file.getName();
                String nameLower = displayName.toLowerCase();

                if (nameLower.endsWith(".txt")) {
                    type = "Text Document";
                    displayName += " [TXT]";
                } else if (nameLower.endsWith(".java")) {
                    type = "Java Source File";
                    displayName += " [JAVA]";
                } else if (nameLower.endsWith(".class")) {
                    type = "Compiled Java Class";
                    displayName += " [CLASS]";
                }

                long kbSize = (file.length() / 1024) + 1;
                tableModel.addRow(new Object[]{
                    displayName,
                    kbSize + " KB",
                    type,
                    sdf.format(new Date(file.lastModified()))
                });
            }
        }
    }

    private static void openTextFileInNotepad(JDesktopPane desktopPane, File file) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (!firstLine) sb.append("\n");
                sb.append(line);
                firstLine = false;
            }
            RetroNotepad.launch(desktopPane, sb.toString());

            for (JInternalFrame f : desktopPane.getAllFrames()) {
                if (f.getTitle().equals("Notepad v1.0 - Untitled")) {
                    f.setTitle("Notepad v1.0 - " + file.getName());
                    break;
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(desktopPane, "Failed to read file.", "Access Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void compileAndRunJavaSource(JDesktopPane desktopPane, File sourceFile) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        if (compiler == null) {
            JOptionPane.showMessageDialog(desktopPane,
                "No Java Development Kit (JDK) compiler found on this machine.\n" +
                "Are you running the app using a standard JRE instead of a full JDK?",
                "Compilation Error", JOptionPane.ERROR_MESSAGE);
            openTextFileInNotepad(desktopPane, sourceFile);
            return;
        }

        new Thread(() -> {
            try {
                String sourcePath = sourceFile.getAbsolutePath();

                // Create output streams to catch standard compiler diagnostics and errors
                ByteArrayOutputStream errStream = new ByteArrayOutputStream();
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();

                // Run the compiler tool
                // Arguments mapping: run(InputStream in, OutputStream out, OutputStream err, String... arguments)
                int compilationResult = compiler.run(null, outStream, errStream, sourcePath);

                if (compilationResult == 0) {
                    // Success! Calculate the path to the newly created .class file
                    String classPathName = sourcePath.substring(0, sourcePath.length() - 5) + ".class";
                    File compiledClassFile = new File(classPathName);

                    SwingUtilities.invokeLater(() -> {
                        refreshDirectoryListing();
                        // Hand off the fresh binary file directly to your Reflection Launcher
                        loadAndRunClassFile(desktopPane, compiledClassFile);
                    });
                } else {
                    // Compilation Failed! Combine standard out and error streams to extract details
                    String errorLog = errStream.toString() + "\n" + outStream.toString();

                    // Display details inside a custom terminal log window frame
                    SwingUtilities.invokeLater(() ->
                        showCompilerErrorFrame(desktopPane, sourceFile.getName(), errorLog)
                    );
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private static void showCompilerErrorFrame(JDesktopPane desktopPane, String filename, String errorText) {
        JInternalFrame errorFrame = new JInternalFrame("Compiler Build Log - " + filename, true, true, true, true);
        errorFrame.setSize(550, 300);
        errorFrame.setLocation(100, 100);
        errorFrame.setLayout(new BorderLayout());

        // Header warning stripe panel
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBackground(new Color(180, 50, 50));
        JLabel warningLabel = new JLabel("✖ Build Failed: Syntax or structural errors detected.");
        warningLabel.setForeground(Color.WHITE);
        warningLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        topPanel.add(warningLabel);
        errorFrame.add(topPanel, BorderLayout.NORTH);

        // Terminal text area style configurations
        JTextArea consoleOut = new JTextArea();
        consoleOut.setEditable(false);
        consoleOut.setBackground(Color.BLACK);
        consoleOut.setForeground(new Color(220, 220, 220)); // Subtle light-grey retro text
        consoleOut.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleOut.setText(errorText.trim());
        consoleOut.setCaretPosition(0); // Scroll to top by default

        JScrollPane scrollPane = new JScrollPane(consoleOut);
        errorFrame.add(scrollPane, BorderLayout.CENTER);

        // Close action button tray layout panel
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close Log");
        closeBtn.addActionListener(e -> errorFrame.dispose());
        bottomPanel.add(closeBtn);
        errorFrame.add(bottomPanel, BorderLayout.SOUTH);

        // Render target system workspace layer
        desktopPane.add(errorFrame);
        errorFrame.setVisible(true);
        try {
            errorFrame.setSelected(true);
        } catch (java.beans.PropertyVetoException e) {
            e.printStackTrace();
        }
    }

    // Dynamic Reflection Engine to execute compiled code at runtime
    private static void loadAndRunClassFile(JDesktopPane desktopPane, File file) {
        try {
            // Strip the ".class" extension to get the pure class name
            String className = file.getName().substring(0, file.getName().length() - 6);
            File directoryPath = file.getParentFile();

            // Setup a dynamic ClassLoader pointing to the file's current directory folder
            URL[] urls = new URL[]{ directoryPath.toURI().toURL() };

            // Using a try-with-resources statement prevents file-locking on your .class files
            try (URLClassLoader classLoader = new URLClassLoader(urls, RetroBrowser.class.getClassLoader())) {

                // Load the class into memory dynamically
                Class<?> loadedClass = classLoader.loadClass(className);

                // APPROACH A: If it's a reusable UI panel (like AdvancedSwingPlayerPanel)
                if (JPanel.class.isAssignableFrom(loadedClass)) {
                    // Create an instance of the panel dynamically using its default constructor
                    JPanel panelInstance = (JPanel) loadedClass.getDeclaredConstructor().newInstance();

                    // Wrap it in a shiny new internal OS window overlay
                    JInternalFrame innerWindow = new JInternalFrame(className, true, true, true, true);
                    innerWindow.setSize(500, 400);
                    innerWindow.setLocation(120, 120);
                    innerWindow.setLayout(new BorderLayout());
                    innerWindow.add(panelInstance, BorderLayout.CENTER);

                    // Add it directly to your existing workspace
                    desktopPane.add(innerWindow);
                    innerWindow.setVisible(true);
                    innerWindow.setSelected(true);
                    return;
                }

                // APPROACH B: If it's a full separate application with a main method
                try {
                    // Look for the standard static main method string array footprint
                    Method mainMethod = loadedClass.getMethod("main", String[].class);

                    // Run it on a background thread so your File Explorer panel doesn't hitch or lag
                    new Thread(() -> {
                        try {
                            // Invoke the main method with empty arguments
                            String[] mainArgs = new String[0];
                            mainMethod.invoke(null, (Object) mainArgs);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }).start();

                } catch (NoSuchMethodException e) {
                    // Class lacks a main framework entry point and isn't a JPanel
                    JOptionPane.showMessageDialog(desktopPane,
                        "Class loaded successfully, but it does not contain an executable main() method or an embeddable JPanel.",
                        "Execution Error", JOptionPane.WARNING_MESSAGE);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(desktopPane,
                "Failed to dynamically invoke the class file:\n" + ex.getMessage(),
                "Reflection Engine Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
