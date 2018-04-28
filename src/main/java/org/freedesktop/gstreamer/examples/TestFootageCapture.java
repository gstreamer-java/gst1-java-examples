/*
 * This example shows how to use the various gstreamer mechanisms to 
 * 1) read from an RTSP stream
 * 2) put it into a time delaying queue so that the sample that arrives at 
 *    a sink is footage captured 5 seconds ago,
 * 3) place the sample into an AppSink which can discard or collect it into 
 *    a Java queue
 * 4) at the user's discretion, start reading the samples from the Java queue
 * 5) encode the samples into an MP4 stream
 * 6) write the stream to a file using FileSink using a filename that is based 
 *    on the current time.
 */
package org.freedesktop.gstreamer.examples;

import java.net.URI;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.event.EOSEvent;

public class TestFootageCapture {
	// Size of Java queue
	private final static int BUFFER_SIZE = 1000000;
	
	private static boolean sendData = false;
	private static ArrayBlockingQueue<Buffer> videoQueue = new ArrayBlockingQueue<Buffer>(BUFFER_SIZE);
	private static ArrayBlockingQueue<Buffer> audioQueue = new ArrayBlockingQueue<Buffer>(BUFFER_SIZE);
	private static StringBuffer videoCaps = new StringBuffer();
	private static StringBuffer audioCaps = new StringBuffer();
	private static Semaphore gotCaps = new Semaphore(2);
	private static Semaphore canSend = new Semaphore(2);
	private static Semaphore gotEOSPlaybin = new Semaphore(1);
	private static Semaphore gotEOSPipeline = new Semaphore(1);
	
	public static void main(String args[]) throws Exception {
		Gst.init();
		
		System.out.println("GST finished initialization.");
		
		Scanner s = new Scanner(System.in);

		// The time delaying queue is specified below
        Bin videoBin = Bin.launch("queue max-size-time=10000000000 min-threshold-time=5000000000 flush-on-eos=true ! appsink name=videoAppSink", true);
        Bin audioBin = Bin.launch("queue max-size-time=10000000000 min-threshold-time=5000000000 flush-on-eos=true ! appsink name=audioAppSink", true);

        AppSink videoAppSink = (AppSink) videoBin.getElementByName("videoAppSink");
        AppSink audioAppSink = (AppSink) audioBin.getElementByName("audioAppSink");
        videoAppSink.set("emit-signals", true);
        audioAppSink.set("emit-signals", true);
        
        AppSinkListener videoAppSinkListener = new AppSinkListener(videoQueue,videoCaps,gotCaps);
        videoAppSink.connect((AppSink.NEW_SAMPLE) videoAppSinkListener);   
        AppSinkListener audioAppSinkListener = new AppSinkListener(audioQueue,audioCaps,gotCaps);
        audioAppSink.connect((AppSink.NEW_SAMPLE) audioAppSinkListener);   

        // Specify rtsp url below
        PlayBin playbin = new PlayBin("playbin");
        playbin.setURI(URI.create("rtsp://ip:port/uri"));
        playbin.setVideoSink(videoBin);
        playbin.setAudioSink(audioBin);

        playbin.getBus().connect((Bus.EOS) (source) -> {
			System.out.println("Received the EOS on the playbin!!!");
			gotEOSPlaybin.release();       	
        });
		       
//		playbin.getBus().connect((Bus.ERROR) (source, code, message) -> {
//			System.out.println("Error Source: " + source.getName());
//			System.out.println("Error Code: " + code);
//			System.out.println("Error Message: " + message);
//		});
//		playbin.getBus().connect((Bus.MESSAGE) (bus, message) -> {
//			System.out.println("Bus Message : " + message.getStructure());
//		});
        
		gotEOSPlaybin.drainPermits();
        gotCaps.drainPermits();
        playbin.play();
		
		System.out.println("Processing of RTSP feed started, please wait...");
		
		// Pipeline below encodes and writes samples to MP4 file
		Pipeline pipeline = Pipeline.launch(
				"appsrc name=videoAppSrc "+
				"! rawvideoparse use-sink-caps=true "+
				"! videoconvert ! x264enc ! h264parse "+
				"! mpegtsmux name=mux "+
				"! filesink sync=false name=filesink "+
				"appsrc name=audioAppSrc "+
				"! rawaudioparse use-sink-caps=true "+
				"! audioconvert ! voaacenc ! aacparse ! mux. "
		);

        AppSrc videoAppSrc = (AppSrc) pipeline.getElementByName("videoAppSrc");
        AppSrc audioAppSrc = (AppSrc) pipeline.getElementByName("audioAppSrc");
        videoAppSrc.set("emit-signals", true);
        audioAppSrc.set("emit-signals", true);
        
        AppSrcListener videoAppSrcListener = new AppSrcListener(videoQueue,canSend);
        videoAppSrc.connect((AppSrc.NEED_DATA) videoAppSrcListener);   
        AppSrcListener audioAppSrcListener = new AppSrcListener(audioQueue,canSend);
        audioAppSrc.connect((AppSrc.NEED_DATA) audioAppSrcListener);   

		pipeline.getBus().connect((Bus.EOS) (source) -> {
			System.out.println("Received the EOS on the pipeline!!!");
			gotEOSPipeline.release();       	
        });
        
//		pipeline.getBus().connect((Bus.ERROR) (source, code, message) -> {
//            System.out.println("Error Source: " + source.getName());
//            System.out.println("Error Code: " + code);
//            System.out.println("Error Message: " + message);			
//		});
//		pipeline.getBus().connect((Bus.MESSAGE) (bus, message) -> {
//			System.out.println("Bus Message : "+message.getStructure());			
//		});
		
		// Get caps from original video stream and copy them into both
		// the AppSrcs.
		gotCaps.acquire(2);
		videoAppSrc.setCaps(new Caps(videoCaps.toString()));
		audioAppSrc.setCaps(new Caps(audioCaps.toString()));

//		System.out.println("VIDEO CAPS : "+videoCaps.toString());
//		System.out.println("AUDIO CAPS : "+audioCaps.toString());
		
		while (true) {
			System.out.println("Press ENTER to start capturing footage from the cam 5 seconds ago, or type 'QUIT' and press ENTER to exit...");
			if (!s.nextLine().isEmpty())
				break;
	
			// Specify filename of MP4 file based on current time
			BaseSink filesink = (BaseSink) pipeline.getElementByName("filesink");
			filesink.set("location", "capture"+System.currentTimeMillis()+".mp4");
	
			// Clear any unread buffers from previous capture from the Java queues
			clearQueue(videoQueue);
			clearQueue(audioQueue);
			
			videoAppSrcListener.resetSendFlagged();
			audioAppSrcListener.resetSendFlagged();
			
			gotEOSPipeline.drainPermits();
			canSend.drainPermits();
	        pipeline.play();
	
	        // Make sure that both video and audio buffers are streamed out at the same time
	        // otherwise you get video or sound first.
			canSend.acquire(2);
			sendData = true;
	
			System.out.println("Press ENTER to stop the capture...");
			s.nextLine();
	
			pipeline.sendEvent(new EOSEvent());
			gotEOSPipeline.acquire(1);
			System.out.println("Capture stopped.");
	
			pipeline.stop();	
			sendData = false;
		}
		
		playbin.sendEvent(new EOSEvent());
		gotEOSPlaybin.acquire(1);
		System.out.println("Stopped processing of RTSP feed.");
		
		playbin.stop();
		
		System.out.println("Exiting program.");
		
		Gst.deinit();
	}
	
	private static void clearQueue(ArrayBlockingQueue<Buffer> queue) {
		Buffer buffer;
		do {
			buffer = queue.poll();
			if (buffer==null)
				break;
		}
		while (true);
	}
	
	private static class AppSinkListener implements AppSink.NEW_SAMPLE {
		private ArrayBlockingQueue<Buffer> queue;
		private StringBuffer caps;
		private Semaphore gotCaps;
		
		private boolean capsSet;
		
		public AppSinkListener(ArrayBlockingQueue<Buffer> queue,StringBuffer caps,Semaphore gotCaps) {
			this.queue = queue;
			this.caps = caps;
			this.gotCaps = gotCaps;
			capsSet = false;
		}
		
		@Override
		public FlowReturn newSample(AppSink elem) {
            Sample sample = elem.pullSample();
            
            if (!capsSet) {
            	caps.append(sample.getCaps().toString());
            	capsSet = true;
            	gotCaps.release();
            }
            
            // This section will be executed only when the sample needs to be passed to the src
            // When sendData is true, the sample's buffer will be duplicated using buffer.copy
            // and offered to the respective queue (videoQueue or audioQueue).
            // If the buffer's copy cannot be inserted to the queue, it will be disposed

            if (sendData) {
            	Buffer buffer = sample.getBuffer().copy();
            	if (!queue.offer(buffer))
            		buffer.dispose();
            }

            sample.dispose();
            
            return FlowReturn.OK;		
		}
	}
	
	private static class AppSrcListener implements AppSrc.NEED_DATA {
		private ArrayBlockingQueue<Buffer> queue;
		private Semaphore canSend;
		
		private boolean sendFlagged;
		
		public AppSrcListener(ArrayBlockingQueue<Buffer> queue,Semaphore canSend) {
			this.queue = queue;
			this.canSend = canSend;
			sendFlagged = false;
		}

		public void resetSendFlagged() {
			sendFlagged = false;
		}
		
		@Override
		public void needData(AppSrc elem, int size) {
			if (!sendFlagged) {
				sendFlagged = true;
				canSend.release();
			}
			
			try {
				elem.pushBuffer(queue.take());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
