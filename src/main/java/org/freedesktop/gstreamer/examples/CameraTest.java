
package org.freedesktop.gstreamer.examples;

import java.awt.Dimension;
import java.awt.EventQueue;
import javax.swing.JFrame;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;

/**
 *
 * @author Neil C Smith (http://neilcsmith.net)
 */
public class CameraTest {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        Gst.init("CameraTest", args);
        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                SimpleVideoComponent vc = new SimpleVideoComponent();
                Bin bin = Bin.launch("autovideosrc ! videoconvert ! capsfilter caps=video/x-raw,width=640,height=480", true);
                Pipeline pipe = new Pipeline();
                pipe.addMany(bin, vc.getElement());
                Pipeline.linkMany(bin, vc.getElement());           

                JFrame f = new JFrame("Camera Test");
                f.add(vc);
                vc.setPreferredSize(new Dimension(640, 480));
                f.pack();
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                
                pipe.play();
                f.setVisible(true);
            }
        });
    }

}
