import javax.swing.*;
import java.awt.*;

public class RetroCalculator {
    // REMOVED: TaskbarManager parameter from launch method
    public static void launch(JDesktopPane desktopPane) {
        JInternalFrame frame = new JInternalFrame("Calculator", true, true, true, true);
        frame.setSize(240, 300);
        frame.setLocation(50, 400);
        frame.setLayout(new BorderLayout(5, 5));

        final JTextField display = new JTextField("0");
        display.setFont(new Font("Dialog", Font.BOLD, 16));
        display.setHorizontalAlignment(JTextField.RIGHT);
        display.setEditable(false);
        display.setBackground(Color.WHITE);
        frame.add(display, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(4, 4, 4, 4));
        String[] buttons = {
            "7", "8", "9", "/",
            "4", "5", "6", "*",
            "1", "2", "3", "-",
            "0", "C", "=", "+"
        };

        final double[] firstOperand = {0};
        final String[] operator = {""};
        final boolean[] startNewNumber = {true};

        for (String text : buttons) {
            JButton btn = new JButton(text);
            btn.setFont(new Font("Dialog", Font.BOLD, 12));
            btn.addActionListener(e -> {
                String cmd = e.getActionCommand();
                if ("0123456789".contains(cmd)) {
                    if (startNewNumber[0]) {
                        display.setText(cmd);
                        startNewNumber[0] = false;
                    } else {
                        display.setText(display.getText() + cmd);
                    }
                } else if (cmd.equals("C")) {
                    display.setText("0");
                    firstOperand[0] = 0;
                    operator[0] = "";
                    startNewNumber[0] = true;
                } else if (cmd.equals("=")) {
                    double secondOperand = Double.parseDouble(display.getText());
                    double result = firstOperand[0];
                    switch (operator[0]) {
                        case "+": result += secondOperand; break;
                        case "-": result -= secondOperand; break;
                        case "*": result *= secondOperand; break;
                        case "/": result = secondOperand != 0 ? result / secondOperand : 0; break;
                    }
                    display.setText(String.valueOf(result).replaceAll("\\.0$", ""));
                    startNewNumber[0] = true;
                } else {
                    firstOperand[0] = Double.parseDouble(display.getText());
                    operator[0] = cmd;
                    startNewNumber[0] = true;
                }
            });
            buttonPanel.add(btn);
        }

        frame.add(buttonPanel, BorderLayout.CENTER);
        desktopPane.add(frame);
        frame.setVisible(true);
    }
}
