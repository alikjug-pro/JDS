import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.URL;

public class RetroNotepad {
    public static void launch(JDesktopPane desktopPane, String initialText) {
        final JInternalFrame frame = new JInternalFrame("Notepad v1.0 - Untitled", true, true, true, true);
        frame.setSize(650, 480); // Adjusted size slightly to accommodate the toolbar neatly
        frame.setLocation(350, 60);
        frame.setLayout(new BorderLayout());

        final JTextArea textArea = new JTextArea(initialText);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        textArea.setBackground(new Color(24, 24, 24));
        textArea.setForeground(new Color(51, 255, 51));
        textArea.setCaretColor(new Color(51, 255, 51));

        final File[] currentFileTracker = { null };

        // Use standard cross-platform modifier mask (Command on Mac, Control on Windows)
        @SuppressWarnings("deprecation")
        final int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        // Undo History Manager Engine
        final UndoManager undoManager = new UndoManager();
        textArea.getDocument().addUndoableEditListener(e -> undoManager.addEdit(e.getEdit()));

        // --- 1. ACTION LOGIC ENGINE WRAPPERS (Shared by Toolbar and Menu) ---

        Runnable newAction = () -> {
            textArea.setText("");
            currentFileTracker[0] = null;
            frame.setTitle("Notepad v1.0 - Untitled");
            undoManager.discardAllEdits();
        };

        Runnable openAction = () -> {
            JFileChooser openChooser = new JFileChooser();
            openChooser.setDialogTitle("Open Text File");
            int choice = openChooser.showOpenDialog(frame);
            if (choice == JFileChooser.APPROVE_OPTION) {
                File targetFile = openChooser.getSelectedFile();
                try (BufferedReader reader = new BufferedReader(new FileReader(targetFile))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    textArea.setText(sb.toString());
                    currentFileTracker[0] = targetFile;
                    frame.setTitle("Notepad v1.0 - " + targetFile.getName());
                    undoManager.discardAllEdits();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Error reading file.", "Read Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        Runnable saveAsAction = () -> {
            JFileChooser saveChooser = new JFileChooser();
            saveChooser.setDialogTitle("Save As...");
            int choice = saveChooser.showSaveDialog(frame);
            if (choice == JFileChooser.APPROVE_OPTION) {
                File targetFile = saveChooser.getSelectedFile();
                try (PrintWriter writer = new PrintWriter(new FileWriter(targetFile))) {
                    writer.print(textArea.getText());
                    currentFileTracker[0] = targetFile;
                    frame.setTitle("Notepad v1.0 - " + targetFile.getName());
                    JOptionPane.showMessageDialog(frame, "Document saved successfully!");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Error writing file.", "Write Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        Runnable saveAction = () -> {
            if (currentFileTracker[0] == null) {
                saveAsAction.run();
            } else {
                try (PrintWriter writer = new PrintWriter(new FileWriter(currentFileTracker[0]))) {
                    writer.print(textArea.getText());
                    JOptionPane.showMessageDialog(frame, "Document saved successfully!");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Error writing file.", "Write Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        Runnable printAction = () -> {
            try {
                textArea.print();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Error sending document to printer.", "Print Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        Runnable undoAction = () -> {
            if (undoManager.canUndo()) undoManager.undo();
        };

        Runnable redoAction = () -> {
            if (undoManager.canRedo()) undoManager.redo();
        };

        Runnable findAction = () -> {
            String searchTerm = JOptionPane.showInputDialog(frame, "Enter text to find:", "Find", JOptionPane.QUESTION_MESSAGE);
            if (searchTerm != null && !searchTerm.isEmpty()) {
                String text = textArea.getText();
                int index = text.indexOf(searchTerm, textArea.getCaretPosition());
                if (index < 0) index = text.indexOf(searchTerm, 0); // Wrap search to beginning if not found from caret

                if (index >= 0) {
                    textArea.requestFocusInWindow();
                    textArea.setSelectionStart(index);
                    textArea.setSelectionEnd(index + searchTerm.length());
                } else {
                    JOptionPane.showMessageDialog(frame, "Text not found.", "Find", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        };

        Runnable replaceAction = () -> {
            JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
            JTextField findField = new JTextField();
            JTextField replaceField = new JTextField();
            panel.add(new JLabel("Find:"));
            panel.add(findField);
            panel.add(new JLabel("Replace with:"));
            panel.add(replaceField);

            int result = JOptionPane.showConfirmDialog(frame, panel, "Replace Text", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                String target = findField.getText();
                String replacement = replaceField.getText();
                if (!target.isEmpty()) {
                    String currentText = textArea.getText();
                    if (currentText.contains(target)) {
                        String updatedText = currentText.replace(target, replacement);
                        textArea.setText(updatedText);
                    } else {
                        JOptionPane.showMessageDialog(frame, "Target text not found.", "Replace Error", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        };

        // --- 2. MENU BAR BUILD ENGINE ---
        JMenuBar notepadMenu = new JMenuBar();

        // File Menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem newItem = new JMenuItem("New");
        JMenuItem openItem = new JMenuItem("Open...");
        JMenuItem saveItem = new JMenuItem("Save");
        JMenuItem saveAsItem = new JMenuItem("Save As...");
        JMenuItem printItem = new JMenuItem("Print...");
        JMenuItem closeItem = new JMenuItem("Close");

        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, mask));
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, mask));
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, mask));
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, mask | KeyEvent.SHIFT_DOWN_MASK));
        printItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, mask));
        closeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, mask));

        newItem.addActionListener(e -> newAction.run());
        openItem.addActionListener(e -> openAction.run());
        saveItem.addActionListener(e -> saveAction.run());
        saveAsItem.addActionListener(e -> saveAsAction.run());
        printItem.addActionListener(e -> printAction.run());
        closeItem.addActionListener(e -> frame.dispose());

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(printItem);
        fileMenu.addSeparator();
        fileMenu.add(closeItem);

        // Edit Menu
        JMenu editMenu = new JMenu("Edit");
        JMenuItem undoItem = new JMenuItem("Undo");
        JMenuItem redoItem = new JMenuItem("Redo");
        JMenuItem cutItem = new JMenuItem("Cut");
        JMenuItem copyItem = new JMenuItem("Copy");
        JMenuItem pasteItem = new JMenuItem("Paste");
        JMenuItem selectAllItem = new JMenuItem("Select All");

        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask));
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, mask));
        cutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, mask));
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, mask));
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, mask));
        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, mask));

        undoItem.addActionListener(e -> undoAction.run());
        redoItem.addActionListener(e -> redoAction.run());
        cutItem.addActionListener(e -> textArea.cut());
        copyItem.addActionListener(e -> textArea.copy());
        pasteItem.addActionListener(e -> textArea.paste());
        selectAllItem.addActionListener(e -> {
            textArea.requestFocusInWindow();
            textArea.selectAll();
        });

        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.addSeparator();
        editMenu.add(selectAllItem);

        // Search Menu
        JMenu searchMenu = new JMenu("Search");
        JMenuItem findItem = new JMenuItem("Find...");
        JMenuItem replaceItem = new JMenuItem("Replace...");

        findItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, mask));
        replaceItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, mask));

        findItem.addActionListener(e -> findAction.run());
        replaceItem.addActionListener(e -> replaceAction.run());

        searchMenu.add(findItem);
        searchMenu.add(replaceItem);

        notepadMenu.add(fileMenu);
        notepadMenu.add(editMenu);
        notepadMenu.add(searchMenu);
        frame.setJMenuBar(notepadMenu);

        // --- 3. RETRO TOOLBAR BUILD ENGINE ---
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false); // Fixes the position relative to the main layout border

        // Instantiating matching assets directly out of jlfgr-1_0 jar archives [1]
        JButton newBtn   = createToolbarButton("lib/New24.gif", "New File");
        JButton openBtn  = createToolbarButton("lib/Open24.gif", "Open File");
        JButton saveBtn  = createToolbarButton("lib/Save24.gif", "Save File");
        JButton printBtn = createToolbarButton("lib/Print24.gif", "Print Document");
        JButton cutBtn   = createToolbarButton("lib/Cut24.gif", "Cut Selected");
        JButton copyBtn  = createToolbarButton("lib/Copy24.gif", "Copy Selected");
        JButton pasteBtn = createToolbarButton("lib/Paste24.gif", "Paste Clipboard");
        JButton undoBtn  = createToolbarButton("lib/Undo24.gif", "Undo Action");
        JButton redoBtn  = createToolbarButton("lib/Redo24.gif", "Redo Action");
        JButton findBtn  = createToolbarButton("lib/Find24.gif", "Find Text");
        JButton dukeBtn  = createToolbarButton("lib/wave.png", "Duke Mascot");

        // Action routing hookups for toolbar interactions
        newBtn.addActionListener(e -> newAction.run());
        openBtn.addActionListener(e -> openAction.run());
        saveBtn.addActionListener(e -> saveAction.run());
        printBtn.addActionListener(e -> printAction.run());
        cutBtn.addActionListener(e -> textArea.cut());
        copyBtn.addActionListener(e -> textArea.copy());
        pasteBtn.addActionListener(e -> textArea.paste());
        undoBtn.addActionListener(e -> undoAction.run());
        redoBtn.addActionListener(e -> redoAction.run());
        findBtn.addActionListener(e -> findAction.run());

        // Assembly order mapping
        toolBar.add(newBtn);
        toolBar.add(openBtn);
        toolBar.add(saveBtn);
        toolBar.add(printBtn);
        toolBar.addSeparator();
        toolBar.add(cutBtn);
        toolBar.add(copyBtn);
        toolBar.add(pasteBtn);
        toolBar.addSeparator();
        toolBar.add(undoBtn);
        toolBar.add(redoBtn);
        toolBar.addSeparator();
        toolBar.add(findBtn);
        toolBar.add(javax.swing.Box.createHorizontalGlue());
        toolBar.add(dukeBtn);

        frame.add(toolBar, BorderLayout.NORTH);
        frame.add(new JScrollPane(textArea), BorderLayout.CENTER);

        desktopPane.add(frame);
        frame.setVisible(true);
      }
      // Safely reads image streaming buffers out of target classpath packages
      private static JButton createToolbarButton(String resourcePath, String tooltipText) {
        JButton button = new JButton();
        button.setToolTipText(tooltipText);
        try {
          java.net.URL imgUrl = RetroNotepad.class.getResource(resourcePath);
          if (imgUrl != null) {
            button.setIcon(new ImageIcon(imgUrl));
          } else {
            button.setText(tooltipText.substring(0, 1)); // Fallback single character label representation
          }
        } catch (Exception e) {
          button.setText(tooltipText.substring(0, 1));
        }
        button.setMargin(new Insets(2, 2, 2, 2));
        button.setFocusable(false);
        return button;
      }
    }
