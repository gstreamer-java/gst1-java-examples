GStreamer Java examples
=======================

This repository contains a series of example projects for using
[GStreamer 1.x][gstreamer] with Java via the [GStreamer Java][gstreamer-java]
libraries, including [gst1-java-core][gst1-core] and extensions.

The code steps in each project source file are documented. Any questions, please
use the [mailing list][gstreamer-java-group].

## Requirements

All examples are self-contained Gradle projects. They should work inside your IDE
or via `./gradlew run` on the command line.

All the examples require an [installation of GStreamer][gstreamer-download] itself.
Windows users installing GStreamer should select the complete profile, rather than
the typical one.

Most examples work with JDK 8+. The JavaFX integration example requires JDK 11+
(and uses JavaFX 15).

## Examples

Inside each example there is an identical `Utils.java` file that contains some
useful code for setting up native paths for an installed version of GStreamer.
This code, and all the example code (aside from some files in the archive), is
free to adapt for your own usage.

### Getting started

- **BasicPipeline** : getting started running a video test source into a GStreamer
output window.

### Desktop (Swing / JavaFX)

- **SwingCamera** : using a camera (or test source) inside a Swing application,
using `gst1-java-swing`.
- **SwingPlayer** : a simple media player with Swing UI, including file selection,
playback controls, seeking and volume meters.
- **FXCamera** : using a camera (or test source) inside a JavaFX application,
using `gst1-java-fx`.
- **FXPlayer** : a simple media player with JavaFX UI, including file selection,
playback controls, seeking and volume meters.

### Server / Internet

- **HLS** : using HTTP Live Streaming to stream live video with Java2D rendered
overlay to browser using Javalin framework.
- **WebRTCSendRecv** : example of sending and receiving via WebRTC using the test
page at https://webrtc.nirbheek.in This is the test server used in upstream
GStreamer examples. You may need to configure permissions in the browser to
always allow audio for that site. You need to pass in the session ID from the
page on the CLI. If running in the terminal via Gradle, it is recommended to
use `./gradlew --console=plain run`.

### Miscellaneous

- **BufferProbe** : using a buffer probe to draw an animation on top of the video
stream using Java2D.
- **Controllers** : configuring controllers to control element properties (ported
from an upstream C example).

### Archive

Inside the archive folder are all the previously available examples, some of which
have not yet been adapted into self-contained example projects.

[gstreamer]: https://gstreamer.freedesktop.org/
[gstreamer-download]: https://gstreamer.freedesktop.org/download/
[gstreamer-java]: https://github.com/gstreamer-java
[gstreamer-java-group]: https://groups.google.com/forum/#!forum/gstreamer-java
[gst1-core]: https://github.com/gstreamer-java/gst1-java-core
