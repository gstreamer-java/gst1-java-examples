/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 Kyle R Wenholz.
 *
 * Copying and distribution of this file, with or without modification,
 * are permitted in any medium without royalty provided the copyright
 * notice and this notice are preserved.    This file is offered as-is,
 * without any warranty.
 *
 */
package org.freedesktop.gstreamer.examples;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.lowlevel.MainLoop;

/**
 *
 * A simple example of transcoding an audio file using AppSink and AppSrc.
 *
 * Depending on which gstreamer plugins are installed, this example can decode
 * nearly any media type, likewise for encoding. Specify the input and output
 * file locations with the first two arguments.
 *
 * @author Kyle R Wenholz (http://krwenholz.com)
 */
public class AppSrcToAppSinkExample {

    private static Pipeline pipe;
    private static byte[] soundBytes = null;

    public static void main(String[] args) throws Exception {
        Gst.init();
        String inputFileLocation = args[0];
        String outputFileLocation = args[1];
        File soundFile = new File(inputFileLocation);
        FileInputStream inStream = null;
        FileChannel output =
            new FileOutputStream(outputFileLocation).getChannel();
        final MainLoop loop = new MainLoop();

        if (soundFile.exists()){
            try {
                System.out.println("Read media file.");
                long fileSize = soundFile.length();
                soundBytes = new byte[(int)fileSize];
                inStream = new FileInputStream(soundFile);
                int byteCount = inStream.read(soundBytes);
                System.out.println("Number of bytes read: " + byteCount);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        AppSrc source = (AppSrc)ElementFactory.make("appsrc", "app-source");
        Element decoder = ElementFactory.make("decodebin", "decoder");
        Element converter = ElementFactory.make("audioconvert", "converter");
        // The encoder element determines your output format.
        Element encoder = ElementFactory.make("wavenc", "wavenc");
        //Element encoder = ElementFactory.make("lamemp3enc", "mp3enc");
        AppSink sink = (AppSink)ElementFactory.make("appsink", "audio-output");

        Pipeline pipe = new Pipeline();
        // We connect to EOS and ERROR on the bus for cleanup and error messages.
        Bus bus = pipe.getBus();
        bus.connect(new Bus.EOS() {

            @Override
            public void endOfStream(GstObject source) {
                System.out.println("Reached end of stream");
                loop.quit();
            }

        });

        bus.connect(new Bus.ERROR() {

            @Override
            public void errorMessage(GstObject source, int code, String message) {
                System.out.println("Error detected");
                System.out.println("Error source: " + source.getName());
                System.out.println("Error code: " + code);
                System.out.println("Message: " + message);
                loop.quit();
            }
        });

        source.set("emit-signals", true);
        source.connect(new AppSrc.NEED_DATA() {

            private final ByteBuffer bb = ByteBuffer.wrap(soundBytes);

            @Override
            public void needData(AppSrc elem, int size) {
                if (bb.hasRemaining()) {
                    System.out.println("needData: size = " + size);
                    byte[] tempBuffer;
                    Buffer buf;
                    int copyLength = (bb.remaining() >= size) ? size : bb.remaining();
                    tempBuffer = new byte[copyLength];
                    buf = new Buffer(copyLength);
                    bb.get(tempBuffer);
                    System.out.println("Temp Buffer remaining bytes: " + bb.remaining());
                    buf.map(true).put(ByteBuffer.wrap(tempBuffer));
                    elem.pushBuffer(buf);
                } else {
                    elem.endOfStream();
                }
            }
        });

        // We connect to NEW_SAMPLE and NEW_PREROLL because either can come up
        // as sources of data, although usually just one does.
        sink.set("emit-signals", true);
        // sync=false lets us run the pipeline faster than real (based on the file)
        // time
        sink.set("sync", false);
        sink.connect(new AppSink.NEW_SAMPLE() {
            @Override
            public FlowReturn newSample(AppSink elem) {
                Sample sample = elem.pullSample();
                ByteBuffer bytes = sample.getBuffer().map(false);
                try {
                    output.write(bytes);
                } catch (Exception e) {
                    System.err.println(e);
                }
                sample.dispose();
                return FlowReturn.OK;
            }
        });

        sink.connect(new AppSink.NEW_PREROLL() {

            @Override
            public FlowReturn newPreroll(AppSink elem) {
                Sample sample = elem.pullPreroll();
                ByteBuffer bytes = sample.getBuffer().map(false);
                try {
                    output.write(bytes);
                } catch (Exception e) {
                    System.err.println(e);
                }
                sample.dispose();
                return FlowReturn.OK;
            }
        });

        pipe.addMany(source, decoder, converter, encoder, sink);
        source.link(decoder);
        converter.link(encoder);
        encoder.link(sink);
        decoder.connect(new Element.PAD_ADDED() {

            @Override
            public void padAdded(Element element, Pad pad) {
                System.out.println("Dynamic pad created, linking decoder/converter");
                System.out.println("Pad name: " + pad.getName());
                System.out.println("Pad type: " + pad.getTypeName());
                Pad sinkPad = converter.getStaticPad("sink");
                try {
                    pad.link(sinkPad);
                    System.out.println("Pad linked.");
                } catch (PadLinkException ex) {
                    System.out.println("Pad link failed : " + ex.getLinkResult());
                }
            }

        });

        System.out.println("Playing...");
        pipe.play();
        System.out.println("Running...");
        loop.run();
        System.out.println("Returned, stopping playback");
        pipe.stop();
        Gst.deinit();
        Gst.quit();

        output.close();
    }
}

