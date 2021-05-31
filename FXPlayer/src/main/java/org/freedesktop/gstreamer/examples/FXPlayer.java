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

import java.io.File;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.Version;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.event.SeekFlags;
import org.freedesktop.gstreamer.fx.FXImageSink;
import org.freedesktop.gstreamer.message.Message;

/**
 * A simple PlayBin-based video player with JavaFX UI for file selection, video
 * control, seek and volume meters.
 *
 * @author Neil C Smith ( https://www.codelerity.com )
 */
public class FXPlayer extends Application {

    /**
     * Always store the top-level pipeline (in this case PlayBin) reference to
     * stop it being garbage collected.
     */
    private PlayBin playbin;

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
        Gst.init(Version.BASELINE, "FXPlayer");
    }

    @Override
    public void start(Stage stage) throws Exception {

        /**
         * FXImageSink from gst1-java-fx wraps the native data from the
         * GStreamer AppSink in a JavaFX image.
         */
        FXImageSink imageSink = new FXImageSink();

        /**
         * Create a PlayBin element and set the AppSink from the image sink as
         * the video sink.
         */
        playbin = new PlayBin("playbin");
        playbin.setVideoSink(imageSink.getSinkElement());

        /**
         * Create a level component and set it as the audio-filter property on
         * the playbin - this will post audio level messages to the bus - see
         * below how to display them.
         */
        Element level = ElementFactory.make("level", "level");
        playbin.set("audio-filter", level);

        /**
         * Set up a simple JavaFX UI with the ImageView.
         */
        stage.setTitle("FX Player");

        //Create a video display from a BorderPane and enclosed ImageView.
        BorderPane videoPane = new BorderPane();
        videoPane.setMinSize(100, 100);
        videoPane.setPrefSize(1280, 720);
        videoPane.setBackground(new Background(new BackgroundFill(
                Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
        ImageView view = new ImageView();
        videoPane.setCenter(view);
        view.imageProperty().bind(imageSink.imageProperty());
        view.fitWidthProperty().bind(videoPane.widthProperty());
        view.fitHeightProperty().bind(videoPane.heightProperty());
        view.setPreserveRatio(true);

        // Create buttons for choosing a file and controlling the playbin.
        Button fileButton = new Button("File...");
        FileChooser chooser = new FileChooser();
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));
        fileButton.setOnAction((e) -> {
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                chooser.setInitialDirectory(file.getParentFile());
                playbin.stop();
                playbin.setURI(file.toURI());
                playbin.play();
            }
        });

        // playback controls
        Button playButton = new Button("Play");
        playButton.setOnAction(e -> playbin.play());
        Button pauseButton = new Button("Pause");
        pauseButton.setOnAction(e -> playbin.pause());
        ToggleButton loopButton = new ToggleButton("Loop");
        loopButton.setSelected(true);

        // position slider
        Slider position = new Slider(0, 1, 0);
        HBox.setHgrow(position, Priority.ALWAYS);
        position.valueProperty().addListener(o -> {
            if (position.isValueChanging()) {
                long dur = playbin.queryDuration(Format.TIME);
                if (dur > 0) {
                    playbin.seekSimple(Format.TIME,
                            EnumSet.of(SeekFlags.FLUSH),
                            (long) (position.getValue() * dur));
                }
            }
        });
        // sync slider position to video when not dragging
        Timeline timer = new Timeline(new KeyFrame(Duration.millis(50),
                e -> {
                    if (!position.isValueChanging()) {
                        long dur = playbin.queryDuration(Format.TIME);
                        long pos = playbin.queryPosition(Format.TIME);
                        if (dur > 0) {
                            double relPos = (double) pos / dur;
                            position.setValue(relPos);
                        }
                    }
                }
        ));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();

        // level display using ProgressBars
        VBox levels = new VBox();
        ProgressBar leftLevel = new ProgressBar(0);
        leftLevel.setPrefSize(100, 10);
        ProgressBar rightLevel = new ProgressBar(0);
        rightLevel.setPrefSize(100, 10);
        levels.getChildren().addAll(leftLevel, rightLevel);

        HBox mediaBar = new HBox(2);
        mediaBar.setAlignment(Pos.CENTER_LEFT);
        mediaBar.setPadding(new Insets(2));
        mediaBar.getChildren().addAll(
                fileButton,
                playButton,
                pauseButton,
                position,
                loopButton,
                levels
        );

        BorderPane pane = new BorderPane();
        pane.setCenter(videoPane);
        pane.setBottom(mediaBar);

        // loop on EOS if button selected
        playbin.getBus().connect((Bus.EOS) source -> {
            // handle on event thread!
            Platform.runLater(() -> {
                if (loopButton.isSelected()) {
                    playbin.seekSimple(Format.TIME,
                            EnumSet.of(SeekFlags.FLUSH),
                            0);
                } else {
                    playbin.stop();
                    position.setValue(0);
                }
            });
        });

        // listen for level messages on the bus
        playbin.getBus().connect("element", new Bus.MESSAGE() {

            @Override
            public void busMessage(Bus arg0, Message message) {
                if (message.getSource() == level) {
                    Structure struct = message.getStructure();
                    // We can get either rms or peak
                    double[] levels = struct.getDoubles("peak");
                    // Calculate the time offset required to get the level
                    // information in sync with the video display
                    long timeDelay = getTimeOffset(struct);
                    Gst.getExecutor().schedule(
                            () -> Platform.runLater(() -> updateLevelDisplay(levels)),
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
                        leftLevel.setProgress(Math.max(0, Math.min(levels[0], 1)));
                        rightLevel.setProgress(Math.max(0, Math.min(levels[1], 1)));
                    } else {
                        leftLevel.setProgress(Math.max(0, Math.min(levels[0], 1)));
                        rightLevel.setProgress(Math.max(0, Math.min(levels[0], 1)));
                    }
                } else {
                    leftLevel.setProgress(0);
                    rightLevel.setProgress(0);
                }
            }
        });

        /**
         * Show the window and start the pipeline.
         */
        stage.setScene(new Scene(pane));
        stage.show();
    }

    public static void main(String[] args) {
        // pass to JavaFX
        launch(args);
    }

}
