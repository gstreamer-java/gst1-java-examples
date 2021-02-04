/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith - Codelerity Ltd. / Tim-Philipp Müller
 *
 * Copying and distribution of this file, with or without modification,
 * are permitted in any medium without royalty provided the copyright
 * notice and this notice are preserved. This file is offered as-is,
 * without any warranty.
 *
 */
package org.freedesktop.gstreamer.examples;

import java.util.concurrent.TimeUnit;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Version;
import org.freedesktop.gstreamer.controller.ARGBControlBinding;
import org.freedesktop.gstreamer.controller.DirectControlBinding;
import org.freedesktop.gstreamer.controller.LFOControlSource;

/**
 * Builds a pipeline with videotestsrc and textoverlay, and uses control sources
 * to modulate text colour and position.
 * <p>
 * Adapted from the C example by Tim-Philipp Müller at
 * <a href="https://gitlab.freedesktop.org/gstreamer/gstreamer/blob/master/tests/examples/controller/text-color-example.c">
 * https://gitlab.freedesktop.org/gstreamer/gstreamer/blob/master/tests/examples/controller/text-color-example.c</a>
 *
 * @author Neil C Smith ( https://www.codelerity.com )
 */
public class Controllers {

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
        Gst.init(Version.BASELINE, "Controllers", args);

        pipeline = new Pipeline();

        /**
         * Set up a videotestsrc. Use setAsString to pass the pattern as a name
         * rather than a number.
         */
        Element src = ElementFactory.make("videotestsrc", "src");
        src.setAsString("pattern", "circular");

        /**
         * Set up a textoverlay. Configure horizontal and vertical alignment to
         * position so the controllers can move the text.
         */
        Element text = ElementFactory.make("textoverlay", "text");
        text.set("text", "GStreamer rocks!");
        text.set("font-desc", "Sans, 42");
        text.setAsString("halignment", "position");
        text.setAsString("valignment", "position");

        /**
         * Use a capsfilter to increase the default dimensions of the output.
         */
        Element capsfilter = ElementFactory.make("capsfilter", "capsfilter");
        capsfilter.setAsString("caps", "video/x-raw, width=800, height=600");

        /**
         * Use an autovideosink. Add all the elements to the pipeline and link.
         */
        Element sink = ElementFactory.make("autovideosink", "sink");
        pipeline.addMany(src, text, capsfilter, sink);
        Pipeline.linkMany(src, text, capsfilter, sink);

        /**
         * Create and configure LFO control sources to control the text
         * position. Create direct control bindings mapped to the element
         * properties, then add the bindings to the element.
         */
        LFOControlSource csXPos = new LFOControlSource();
        csXPos.setFrequency(0.11).setAmplitude(0.2).setOffset(0.5);
        text.addControlBinding(DirectControlBinding.create(text, "xpos", csXPos));

        LFOControlSource csYPos = new LFOControlSource();
        csYPos.setFrequency(0.04).setAmplitude(0.4).setOffset(0.5);
        text.addControlBinding(DirectControlBinding.create(text, "ypos", csYPos));

        /**
         * Create and configure 3 LFO control sources for the colour channels,
         * create an ARGB control binding mapped to the colour property, and add
         * the binding to the element.
         */
        LFOControlSource csR = new LFOControlSource();
        csR.setFrequency(0.19).setAmplitude(0.5).setOffset(0.5);
        LFOControlSource csG = new LFOControlSource();
        csG.setFrequency(0.27).setAmplitude(0.5).setOffset(0.5);
        LFOControlSource csB = new LFOControlSource();
        csB.setFrequency(0.13).setAmplitude(0.5).setOffset(0.5);
        text.addControlBinding(ARGBControlBinding.create(text, "color", null, csR, csG, csB));

        // start the pipeline
        pipeline.play();

        /**
         * GStreamer native threads will not be taken into account by the JVM
         * when deciding whether to shutdown, so we have to keep the main thread
         * alive. Gst.main() will keep the calling thread alive until Gst.quit()
         * is called. Here we use the built-in executor to schedule a quit after
         * 15 seconds.
         */
        Gst.getExecutor().schedule(Gst::quit, 15, TimeUnit.SECONDS);
        Gst.main();

    }

}
