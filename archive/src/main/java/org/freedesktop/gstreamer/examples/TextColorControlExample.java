/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith / Tim-Philipp Müller
 *
 * Copying and distribution of this file, with or without modification,
 * are permitted in any medium without royalty provided the copyright
 * notice and this notice are preserved.    This file is offered as-is,
 * without any warranty.
 *
 */
package org.freedesktop.gstreamer.examples;

import java.util.concurrent.TimeUnit;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.controller.ARGBControlBinding;
import org.freedesktop.gstreamer.controller.DirectControlBinding;
import org.freedesktop.gstreamer.controller.LFOControlSource;

/**
 * Adapted from the C example by Tim-Philipp Müller at
 * <a href="https://gitlab.freedesktop.org/gstreamer/gstreamer/blob/master/tests/examples/controller/text-color-example.c">
 * https://gitlab.freedesktop.org/gstreamer/gstreamer/blob/master/tests/examples/controller/text-color-example.c</a>
 */
public class TextColorControlExample {
    
    private static Pipeline pipe;
    
    public static void main(String[] args) {
        Gst.init("TextColorExample");
        
        pipe = new Pipeline();
        
        Element src = ElementFactory.make("videotestsrc", "src");
        src.set("pattern", 11);
        
        Element text = ElementFactory.make("textoverlay", "text");
        text.set("text", "GStreamer rocks!");
        text.set("font-desc", "Sans, 42");
        text.set("halignment", 4);
        text.set("valignment", 3);
        
        Element capsfilter = ElementFactory.make("capsfilter", "caps");
        capsfilter.setAsString("caps", "video/x-raw, width=800, height=600");
        
        Element sink = ElementFactory.make("autovideosink", "sink");
        
        pipe.addMany(src, text, capsfilter, sink);
        Pipeline.linkMany(src, text, capsfilter, sink);
        
        
        LFOControlSource csXPos = new LFOControlSource();
        csXPos.setFrequency(0.11).setAmplitude(0.2).setOffset(0.5);
        text.addControlBinding(DirectControlBinding.create(text, "xpos", csXPos));
        
        LFOControlSource csYPos = new LFOControlSource();
        csYPos.setFrequency(0.04).setAmplitude(0.4).setOffset(0.5);
        text.addControlBinding(DirectControlBinding.create(text, "ypos", csYPos));
        
        LFOControlSource csR = new LFOControlSource();
        csR.setFrequency(0.19).setAmplitude(0.5).setOffset(0.5);
        LFOControlSource csG = new LFOControlSource();
        csG.setFrequency(0.27).setAmplitude(0.5).setOffset(0.5);
        LFOControlSource csB = new LFOControlSource();
        csB.setFrequency(0.13).setAmplitude(0.5).setOffset(0.5);
        text.addControlBinding(ARGBControlBinding.create(text, "color", null, csR, csG, csB));
        
        pipe.play();
        
        Gst.getExecutor().schedule(Gst::quit, 15, TimeUnit.SECONDS);
        
        Gst.main();
        
    }
    
}
