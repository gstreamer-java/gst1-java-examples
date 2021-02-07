package org.freedesktop.gstreamer.examples;

import org.freedesktop.gstreamer.*;

import java.io.*;

public class BatchTranscode
{
    public static void main(String[] argv)
    {
        Gst.init();

        if (argv.length!=2) {
            usage(System.err);
            System.exit(1);
        }

        String srcVideo = argv[0];
        String outFile = argv[1];

        System.out.println(srcVideo+" -> "+outFile);

        String pipeSpec = "filesrc name=src ! decodebin name=dec ! " +
            ("audioconvert ! faac ! queue ! mux. " +
                "dec. ! videoconvert " +
                "! videorate ! video/x-raw,framerate=30/1 " +
                "! x264enc pass=4 quantizer=21 ! queue ! mux. " +
                "mpegtsmux name=mux") +
            " ! filesink name=dst";
        Pipeline pipe = (Pipeline) Gst.parseLaunch(pipeSpec);

        pipe.getElementByName("src").set("location", srcVideo);
        pipe.getElementByName("dst").set("location", outFile);

        Bus bus = pipe.getBus();
        bus.connect((Bus.EOS) gstObject -> System.out.println("EOS "+gstObject));
        bus.connect((Bus.ERROR) (gstObject, i, s) -> System.out.println("ERROR "+i+" "+s+" "+gstObject));
        bus.connect((Bus.WARNING) (gstObject, i, s) -> System.out.println("WARN "+i+" "+s+" "+gstObject));
        bus.connect((Bus.EOS) obj -> Gst.quit() );

        pipe.play();

        Gst.main();
    }

    public static void usage(PrintStream ps)
    {
        ps.println("usage:\n  java "+BatchTranscode.class.getName()+" inputVideo output.ts");
    }
}
