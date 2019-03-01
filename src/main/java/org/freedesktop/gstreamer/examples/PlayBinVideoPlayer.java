/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2019 Neil C Smith.
 *
 * Copying and distribution of this file, with or without modification,
 * are permitted in any medium without royalty provided the copyright
 * notice and this notice are preserved.  This file is offered as-is,
 * without any warranty.
 *
 */
package org.freedesktop.gstreamer.examples;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.Timer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.message.Message;
import org.freedesktop.gstreamer.message.MessageType;

/**
 *
 * A simple video player using PlayBin and Swing.
 *
 * @author Neil C Smith (http://neilcsmith.net)
 */
public class PlayBinVideoPlayer {

    public static void main(String[] args) {

        Gst.init();
        EventQueue.invokeLater(() -> {

            // Create a Swing video display component
            SimpleVideoComponent vc = new SimpleVideoComponent();

            // Create a PlayBin element and set the AppSink from the Swing component
            // as the video sink.
            PlayBin playbin = new PlayBin("playbin");
            playbin.setVideoSink(vc.getElement());

            // Create a level component and set it as the audio-filter property
            // on the playbin - this will post audio level messages to the bus -
            // see below how to display them.
            Element level = ElementFactory.make("level", "level");
            playbin.set("audio-filter", level);

            // Create a GUI
            JFrame window = new JFrame("Video Player");
            window.add(vc);
            vc.setPreferredSize(new Dimension(800, 600));
            Box buttons = Box.createHorizontalBox();

            JButton fileButton = new JButton("File...");
            fileButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                int returnValue = fileChooser.showOpenDialog(window);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    playbin.stop();
                    playbin.setURI(fileChooser.getSelectedFile().toURI());
                    playbin.play();
                }
            });

            JButton playButton = new JButton("Play");
            playButton.addActionListener(e -> playbin.play());
            JButton pauseButton = new JButton("Pause");
            pauseButton.addActionListener(e -> playbin.pause());
            
            // position slider
            JSlider position = new JSlider(0, 1000);
            position.addChangeListener(e -> {
                if (position.getValueIsAdjusting()) {
                    long dur = playbin.queryDuration(TimeUnit.NANOSECONDS);
                    long pos = playbin.queryPosition(TimeUnit.NANOSECONDS);
                    if (dur > 0) {
                        double relPos = position.getValue() / 1000.0;
                        playbin.seek((long) (relPos * dur), TimeUnit.NANOSECONDS);
                    } 
                }
            });
            // sync slider position to video when not dragging
            new Timer(50, e -> {
                if (!position.getValueIsAdjusting()) {
                    long dur = playbin.queryDuration(TimeUnit.NANOSECONDS);
                    long pos = playbin.queryPosition(TimeUnit.NANOSECONDS);
                    if (dur > 0) {
                        double relPos = (double) pos / dur;
                        position.setValue((int) (relPos * 1000));
                    }
                }
            }).start();

            // quick and dirty level display using JProgressBar
            Box levels = Box.createVerticalBox();
            JProgressBar leftLevel = new JProgressBar();
            leftLevel.setMaximumSize(new Dimension(200, 20));
            JProgressBar rightLevel = new JProgressBar();
            rightLevel.setMaximumSize(new Dimension(200, 20));
            levels.add(leftLevel);
            levels.add(rightLevel);

            buttons.add(fileButton);
            buttons.add(playButton);
            buttons.add(pauseButton);
            buttons.add(position);
            buttons.add(levels);
            window.add(buttons, BorderLayout.SOUTH);

            window.pack();
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // listen for level messages on the bus
            playbin.getBus().connect("element", new Bus.MESSAGE() {

                @Override
                public void busMessage(Bus arg0, Message message) {
                    Structure struct = message.getStructure();
                    if (message.getType() == MessageType.ELEMENT
                            && message.getSource().getName().startsWith("level")) {
                        // We can get either rms or peak
                        double[] levels = struct.getDoubles("peak");
                        // Calculate the time offset required to get the level
                        // information in sync with the video display
                        long timeDelay = getTimeOffset(struct);
                        Gst.getExecutor().schedule(
                                () -> EventQueue.invokeLater(() -> updateLevelDisplay(levels)),
                                timeDelay, TimeUnit.NANOSECONDS);
                    }
                }

                private long getTimeOffset(Structure struct) {
                    long actualTime = playbin.getClock().getTime()
                            - playbin.getBaseTime();
                    long runningTime = (long) struct.getValue("running-time");
                    long duration = (long) struct.getValue("duration");
                    long messageTime = runningTime + (duration / 2);
                    return messageTime - actualTime;
                }

                private void updateLevelDisplay(double[] levels) {
                    if (playbin.isPlaying() && levels.length > 0) {
                        // convert levels for display
                        for (int i = 0; i < levels.length; i++) {
                            levels[i] = Math.pow(10, levels[i] / 20);
                        }
                        if (levels.length >= 2) {
                            leftLevel.setValue((int) Math.max(0, Math.min(levels[0] * 100, 100)));
                            rightLevel.setValue((int) Math.max(0, Math.min(levels[1] * 100, 100)));
                        } else {
                            leftLevel.setValue((int) Math.max(0, Math.min(levels[0] * 100, 100)));
                            rightLevel.setValue((int) Math.max(0, Math.min(levels[0] * 100, 100)));
                        }
                    } else {
                        leftLevel.setValue(0);
                        rightLevel.setValue(0);
                    }
                }
            });

            window.setVisible(true);

        });
    }

}
