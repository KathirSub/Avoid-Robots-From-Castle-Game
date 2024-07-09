package edu.curtin.saed.assignment1;

import java.awt.*;
import javax.swing.*;

public class App {

    public static void main(String[] args) {
        // Note: SwingUtilities.invokeLater() is equivalent to JavaFX's
        // Platform.runLater().
        JFrame window = new JFrame("Example App (Swing)");

        JToolBar toolbar = new JToolBar();

        JLabel label = new JLabel("Score: 0 ");
        JLabel wallLabel = new JLabel(" Queue: 0");

        toolbar.add(label);
        toolbar.add(wallLabel);

        JTextArea logger = new JTextArea();
        JScrollPane loggerArea = new JScrollPane(logger);
        loggerArea.setBorder(BorderFactory.createEtchedBorder());
        logger.append("Welcome, Try to stop the Robots!\n");
        

        SwingArena arena = new SwingArena(logger, label, wallLabel);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, arena, logger);

        Container contentPane = window.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(toolbar, BorderLayout.NORTH);
        contentPane.add(splitPane, BorderLayout.CENTER);

        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setPreferredSize(new Dimension(800, 800));
        window.pack();
        window.setVisible(true);

        splitPane.setDividerLocation(0.75);
    };
}