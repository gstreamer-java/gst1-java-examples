/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 Neil C Smith.
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
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.PlayBin;

/**
 *
 * An example of playing back a video in multiple sinks - one full size in a native
 * window, one scaled in a Swing window. Use the File... button to choose a local video file.
 * 
 * @author Neil C Smith (http://neilcsmith.net)
 */
public class MultiSinkExample {

    /**
     * @param args the command line arguments
     */
    private static PlayBin playbin;

    public static void main(String[] args) {

        Gst.init();
        EventQueue.invokeLater(() -> {
            Bin bin = Gst.parseBinFromDescription("tee name=t t. ! queue ! videoconvert ! autovideosink "
                    + "t. ! queue ! videoconvert ! videoscale ! capsfilter caps=video/x-raw,width=640,height=480 ! appsink name=appsink", true);

            SimpleVideoComponent vc = new SimpleVideoComponent((AppSink) bin.getElementByName("appsink"));

            playbin = new PlayBin("playbin");
            playbin.setVideoSink(bin);

// set PlayBin flags to not use internal video scaling otherwise don't get native resolution output
// no mapping for GstPlayFlags in bindings yet!
            int flags = (int) playbin.get("flags");
            flags |= (1 << 6);
            playbin.set("flags", flags);
//
// uncomment this section to switch off audio playback - again GstPlayFlags
//                int flags = (int) playbin.get("flags");
//                flags &= ~(1 << 1);
//                playbin.set("flags", flags);
//
            JFrame window = new JFrame("Multiple Sinks");
            window.add(vc);
            vc.setPreferredSize(new Dimension(640, 480));
            Box buttons = Box.createHorizontalBox();
            JButton fileButton = new JButton("File...");
            buttons.add(fileButton);
            window.add(buttons, BorderLayout.SOUTH);
            window.pack();
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            fileButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                int returnValue = fileChooser.showOpenDialog(window);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    playbin.stop();
                    playbin.setURI(fileChooser.getSelectedFile().toURI());
                    playbin.play();
                }
            });

            window.setVisible(true);
        });
    }

}
