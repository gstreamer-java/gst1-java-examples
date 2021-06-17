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

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeInfo;
import org.freedesktop.gstreamer.PadProbeReturn;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Version;

/**
 * A demonstration of HTTP Live Streaming using GStreamer's HLSSink2 element.
 *
 * Launches a GStreamer pipeline, uses a buffer probe to draw an animation on
 * top of the video stream using Java2D, then serves the stream to the browser
 * using the Javalin framework and GStreamer's hlssink2 element.
 *
 * The default latency is quite high (15-30s). This can be lowered using
 * hlssink2 element properties and hls.js configuration.
 *
 * @author Neil C Smith ( https://www.codelerity.com )
 */
public class HLS {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int FPS = 30;

    /**
     * Keep a reference to the Pipeline to ensure it isn't GC'd.
     */
    private static Pipeline pipeline;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {

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
        Gst.init(Version.of(1, 16), "HLS", args);

        /**
         * Set up a Caps string with the width, height and buffer format
         * required for reading and writing into the BufferedImage.
         */
        String caps = "video/x-raw, width=" + WIDTH + ", height=" + HEIGHT
                + ", pixel-aspect-ratio=1/1, framerate=" + FPS + "/1, "
                + (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                ? "format=BGRx" : "format=xRGB");

        /**
         * Use Gst.parseLaunch() to create a pipeline from a GStreamer string
         * definition. This method returns Pipeline when more than one element
         * is specified.
         *
         * The named Identity element can be acquired from the pipeline by name
         * and the probe attached to its sink pad.
         *
         * The named HLSSink2 element can be acquired from the pipeline and
         * configured to place files in a temporary directory to be served by
         * Javalin.
         */
        pipeline = (Pipeline) Gst.parseLaunch("autovideosrc ! "
                + "videorate ! videoconvert ! videoscale ! "
                + caps + " ! identity name=identity ! videoconvert ! "
                + "x264enc ! video/x-h264, profile=baseline ! "
                + "hlssink2 name=sink");
        Element identity = pipeline.getElementByName("identity");
        identity.getStaticPad("sink")
                .addProbe(PadProbeType.BUFFER, new Renderer(WIDTH, HEIGHT));

        /**
         * Configure a temporary directory for playlist and video files. A
         * shutdown hook is added in deleteOnExit() to remove the playlist and
         * video files on shutdown.
         */
        Path playlistRoot = Files.createTempDirectory("hls");
        deleteOnExit(playlistRoot);
        Element sink = pipeline.getElementByName("sink");
        sink.set("playlist-location",
                playlistRoot.resolve("playlist.m3u8").toString());
        sink.set("location",
                playlistRoot.resolve("segment%05d.ts").toString());

        /**
         * Start the pipeline. Attach a bus listener to call Gst.quit on EOS or
         * error.
         */
        pipeline.getBus().connect((Bus.ERROR) ((source, code, message) -> {
            System.out.println(message);
            Gst.quit();
        }));
        pipeline.getBus().connect((Bus.EOS) (source) -> Gst.quit());
        pipeline.play();

        /**
         * Configure the Javalin server. The main index.html is served from the
         * /public folder on the classpath (see
         * src/main/resource/public/index.html). The hls.js library is added via
         * webjars. The temporary folder for playlist and video files is added
         * under the /hls url path.
         */
        Javalin app = Javalin.create(cfg -> {
            cfg.addStaticFiles("/public");
            cfg.addStaticFiles("/hls", playlistRoot.toString(), Location.EXTERNAL);
            cfg.enableWebjars();
        });
        /**
         * Return a 503 status if the playlist.m3u8 file is not available. The
         * hls.js library will stop retrying to load the playlist if it receives
         * a 404. The playlist file is not available until a number of segments
         * have been written.
         */
        app.error(404, ctx -> {
            if (ctx.path().endsWith(".m3u8")) {
                ctx.status(503);
            }
        });
        /**
         * Start the server on port 8000, and once started try to open the page
         * in the local browser.
         */
        app.events(e -> e.serverStarted(() -> {
            try {
                Desktop.getDesktop().browse(URI.create("http://localhost:8000"));
            } catch (Exception ex) {
            }
        }));
        app.start(8000);

        /**
         * Wait until Gst.quit() called.
         */
        Gst.main();

        pipeline.stop();
        app.stop();
    }

    /**
     * A Pad.PROBE implementation that acquires the Buffer, reads it into the
     * data array of a BufferedImage, renders an animation on top, and writes
     * back into the Buffer.
     */
    static class Renderer implements Pad.PROBE {

        private final BufferedImage image;
        private final int[] data;
        private final Point[] points;
        private final Paint fill;

        private Renderer(int width, int height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            data = ((DataBufferInt) (image.getRaster().getDataBuffer())).getData();
            points = new Point[18];
            for (int i = 0; i < points.length; i++) {
                points[i] = new Point();
            }
            fill = new GradientPaint(0, 0, new Color(1.0f, 0.3f, 0.5f, 0.9f),
                    60, 20, new Color(0.3f, 1.0f, 0.7f, 0.8f), true);
        }

        @Override
        public PadProbeReturn probeCallback(Pad pad, PadProbeInfo info) {
            Buffer buffer = info.getBuffer();
            if (buffer.isWritable()) {
                IntBuffer ib = buffer.map(true).asIntBuffer();
                ib.get(data);
                render();
                ib.rewind();
                ib.put(data);
                buffer.unmap();
            }
            return PadProbeReturn.OK;
        }

        private void render() {
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            for (Point point : points) {
                point.tick();
            }
            GeneralPath path = new GeneralPath();
            path.moveTo(points[0].x, points[0].y);
            for (int i = 2; i < points.length; i += 2) {
                path.quadTo(points[i - 1].x, points[i - 1].y,
                        points[i].x, points[i].y);
            }
            path.closePath();
            path.transform(AffineTransform.getScaleInstance(image.getWidth(), image.getHeight()));
            g2d.setPaint(fill);
            g2d.fill(path);
            g2d.setColor(Color.BLACK);
            g2d.draw(path);
        }

    }

    static class Point {

        private double x, y, dx, dy;

        private Point() {
            this.x = Math.random();
            this.y = Math.random();
            this.dx = 0.02 * Math.random();
            this.dy = 0.02 * Math.random();
        }

        private void tick() {
            x += dx;
            y += dy;
            if (x < 0 || x > 1) {
                dx = -dx;
            }
            if (y < 0 || y > 1) {
                dy = -dy;
            }
        }

    }

    private static void deleteOnExit(Path dir) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (file.toString().endsWith(".m3u8")
                                || file.toString().endsWith(".ts")) {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        } else {
                            throw new IOException("Unexpected file type at " + file);
                        }
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException e)
                            throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete " + dir, e);
            }
        }));
    }

}
