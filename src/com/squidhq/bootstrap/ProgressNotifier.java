package com.squidhq.bootstrap;

import javax.swing.*;
import java.awt.*;

public class ProgressNotifier {

    public JFrame frame;
    public JLabel progressLabel;
    public JProgressBar progressBar;

    public ProgressNotifier(String title) {
        this.frame = new JFrame(title);
        JPanel jPanel = new JPanel();
        this.progressLabel = new JLabel("Starting...");
        this.progressBar = new JProgressBar();
        this.progressBar.setSize(380, 40);
        this.progressBar.setValue(5);
        jPanel.setLayout(new GridLayout(2, 1));
        jPanel.add(BorderLayout.NORTH, this.progressLabel);
        jPanel.add(BorderLayout.SOUTH, this.progressBar);
        jPanel.setSize(400, 100);
        jPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.frame.add(jPanel);
        this.frame.setSize(jPanel.getSize());
        this.frame.setLocationRelativeTo(null);
        this.frame.setResizable(false);
        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public void show() {
        this.frame.setVisible(true);
    }

    public void set(String label, int percent) {
        this.progressLabel.setText(label);
        this.progressBar.setValue(percent);
    }

    public void dispose() {
        this.frame.dispose();
    }

    public void disposeLater(final long ms) {
        new Thread() {
            public void run() {
                try {
                    Thread.sleep(ms);
                } catch(InterruptedException exception) {
                    // ignore
                }
                ProgressNotifier.this.dispose();
            }
        }.start();
    }

}
