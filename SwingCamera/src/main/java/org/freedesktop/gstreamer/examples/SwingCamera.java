/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith - Codelerity Ltd.
 *
 * Copying and distribution of this file, with or without modification,
 * are permitted in any medium without royalty provided the copyright
 * notice and this notice are preserved. This file is offered as-is,
 * without any warranty.
 *
 */
package org.freedesktop.gstreamer.examples;

import java.awt.Dimension;
import java.awt.EventQueue;
import javax.swing.JFrame;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Version;
import org.freedesktop.gstreamer.swing.GstVideoComponent;

/**
 * Displays camera input to a Swing window. By using autovideosrc, this will
 * revert to a test video source if no camera is detected.
 *
 * @author Neil C Smith ( https://www.codelerity.com )
 *
 */
public class SwingCamera {

    /**
     * Always store the top-level pipeline reference to stop it being garbage
     * collected.
     */
    private static Pipeline pipeline;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        /**
         * Set up paths to native GStreamer libraries - see adjacent file.
         */
        Utils.configurePaths();

        /**
         * Initialize GStreamer. Always pass the lowest version you require -
         * Version.BASELINE is GStreamer 1.8. Use Version.of() for higher.
         * Features requiring later versions of GStreamer than passed here will
         * throw an exception in the bindings even if the actual native library
         * is a higher version.
         */
        Gst.init(Version.BASELINE, "SwingCamera", args);

        EventQueue.invokeLater(() -> {

            /**
             * GstVideoComponent from gst1-java-swing is a Swing component that
             * wraps a GStreamer AppSink to display video in a Swing UI.
             */
            GstVideoComponent vc = new GstVideoComponent();

            /**
             * Parse a Bin to contain the autovideosrc from a GStreamer string
             * representation. The alternative approach would be to create and
             * link the elements in code using ElementFactory::make.
             *
             * The Bin uses a capsfilter to specify a width and height, as well
             * as a videoconvert element to convert the video format to that
             * required by GstVideoComponent, and a videoscale in case the
             * source does not support the required resolution.
             *
             * The bin is added to a top-level pipeline and linked to the
             * AppSink from the Swing component.
             */
            Bin bin = Gst.parseBinFromDescription(
                    "autovideosrc ! "
                    + "videoscale ! videoconvert ! "
                    + "capsfilter caps=video/x-raw,width=640,height=480",
                    true);
            pipeline = new Pipeline();
            pipeline.addMany(bin, vc.getElement());
            Pipeline.linkMany(bin, vc.getElement());
            
            JFrame f = new JFrame("Camera Test");
            f.add(vc);
            vc.setPreferredSize(new Dimension(640, 480));
            f.pack();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            pipeline.play();
            f.setVisible(true);

        });

    }

}
