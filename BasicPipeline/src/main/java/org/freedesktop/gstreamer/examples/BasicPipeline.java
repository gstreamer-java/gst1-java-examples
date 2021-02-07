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

import java.util.concurrent.TimeUnit;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Version;

/**
 * Simply launches a test GStreamer pipeline using the Java bindings.
 * 
 * @author Neil C Smith ( https://www.codelerity.com )
 */
public class BasicPipeline {

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
        Gst.init(Version.BASELINE, "BasicPipeline", args);

        /**
         * Use Gst.parseLaunch() to create a pipeline from a GStreamer string
         * definition. This method returns Pipeline when more than one element
         * is specified.
         */
        pipeline = (Pipeline) Gst.parseLaunch("videotestsrc ! autovideosink");

        /**
         * Start the pipeline.
         */
        pipeline.play();

        /**
         * GStreamer native threads will not be taken into account by the JVM
         * when deciding whether to shutdown, so we have to keep the main thread
         * alive. Gst.main() will keep the calling thread alive until Gst.quit()
         * is called. Here we use the built-in executor to schedule a quit after
         * 10 seconds.
         */
        Gst.getExecutor().schedule(Gst::quit, 10, TimeUnit.SECONDS);
        Gst.main();

    }



}
