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

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Version;
import org.freedesktop.gstreamer.fx.FXImageSink;

/**
 * Displays camera input to a JavaFX window. By using autovideosrc, this will
 * revert to a test video source if no camera is detected.
 *
 * @author Neil C Smith ( https://www.codelerity.com )
 */
public class FXCamera extends Application {

    /**
     * Always store the top-level pipeline reference to stop it being garbage
     * collected.
     */
    private Pipeline pipeline;

    @Override
    public void init() throws Exception {
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
        Gst.init(Version.BASELINE, "FXCamera");
    }

    @Override
    public void start(Stage stage) throws Exception {

        /**
         * FXImageSink from gst1-java-fx wraps the native data from the
         * GStreamer AppSink in a JavaFX image. Requested dimensions will be set
         * on the caps for the AppSink.
         */
        FXImageSink imageSink = new FXImageSink();
        imageSink.requestFrameSize(640, 480);

        /**
         * Parse a Bin to contain the autovideosrc from a GStreamer string
         * representation. The alternative approach would be to create and link
         * the elements in code using ElementFactory::make.
         *
         * The Bin uses a videoconvert element to convert the video format to
         * that required by the FXImageSink, a videoscale in case the source
         * does not support the required resolution.
         *
         * The bin is added to a top-level pipeline and linked to the AppSink
         * from the image sink.
         */
        Bin bin = Gst.parseBinFromDescription(
                "autovideosrc ! videoscale ! videoconvert",
                true);
        pipeline = new Pipeline();
        pipeline.addMany(bin, imageSink.getSinkElement());
        Pipeline.linkMany(bin, imageSink.getSinkElement());

        /**
         * Set up a simple JavaFX window with ImageView.
         */
        stage.setTitle("FX Camera");
        BorderPane pane = new BorderPane();
        pane.setBackground(new Background(new BackgroundFill(
                Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
        ImageView view = new ImageView();
        pane.setCenter(view);

        /**
         * Bind the ImageView to the image from the FXImageSink, and scale the
         * view within the window.
         */
        view.imageProperty().bind(imageSink.imageProperty());
        view.fitWidthProperty().bind(pane.widthProperty());
        view.fitHeightProperty().bind(pane.heightProperty());
        view.setPreserveRatio(true);

        /**
         * Show the window and start the pipeline.
         */
        stage.setScene(new Scene(pane, 640, 480));
        stage.show();
        pipeline.play();
    }

    public static void main(String[] args) {
        // pass to JavaFX
        launch(args);
    }

}
