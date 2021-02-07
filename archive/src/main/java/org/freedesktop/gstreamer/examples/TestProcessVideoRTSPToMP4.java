/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 Tend Wong.
 *
 * Copying and distribution of this file, with or without modification,
 * are permitted in any medium without royalty provided the copyright
 * notice and this notice are preserved.    This file is offered as-is,
 * without any warranty.
 *
 */
 package org.freedesktop.gstreamer.examples;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.BufferFlags;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.freedesktop.gstreamer.glib.NativeFlags;

/**
 * This example shows how to use the various gstreamer mechanisms to
 * read from an RTSP stream, process it frame by frame, then write the processed
 * frames to an MP4 file.
 *
 * @author Tend Wong
 */
public class TestProcessVideoRTSPToMP4 {
	private static boolean sendData = false;
	private static ArrayBlockingQueue<Buffer> videoQueue = new ArrayBlockingQueue<Buffer>(1);
	
	private static StringBuffer videoCaps = new StringBuffer();
	private static Semaphore gotCaps = new Semaphore(2);
	private static Semaphore canSend = new Semaphore(2);
	private static Semaphore gotEOSPlaybin = new Semaphore(1);
	private static Semaphore gotEOSPipeline = new Semaphore(1);
	
	private static int videoWidth;
	private static int videoHeight;
	private static int numPixels;

	private static ArrayBlockingQueue<FrameInfo> processingQueue = new ArrayBlockingQueue<FrameInfo>(1);
	
	private static boolean processData = true;
	private static ProcessingThread processingThread;

	public static void main(String args[]) throws Exception {
		Gst.init();
		
		System.out.println("GST finished initialization.");
		
		Scanner s = new Scanner(System.in);

        Bin videoBin = Gst.parseBinFromDescription("appsink name=videoAppSink", true);

        AppSink videoAppSink = (AppSink) videoBin.getElementByName("videoAppSink");
        videoAppSink.set("emit-signals", true);
//        videoAppSink.enableAsync(false);
        videoAppSink.set("async", true);
        
        AppSinkListener videoAppSinkListener = new AppSinkListener(processingQueue,videoCaps,gotCaps);
        videoAppSink.connect((AppSink.NEW_SAMPLE) videoAppSinkListener);   

        StringBuilder caps = new StringBuilder("video/x-raw,pixel-aspect-ratio=1/1,");
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)
            caps.append("format=BGRx");
        else
            caps.append("format=xRGB");
        videoAppSink.setCaps(new Caps(caps.toString()));
        
        PlayBin playbin = new PlayBin("playbin");
		playbin.setURI(URI.create("rtsp://ip:port/uri"));
        playbin.setVideoSink(videoBin);
        playbin.setAudioSink(null);

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
        
        processData = true;
        processingThread = new ProcessingThread();
        processingThread.setDaemon(true);
        processingThread.start();
        
		gotEOSPlaybin.drainPermits();
        gotCaps.drainPermits();
        playbin.play();
		
		System.out.println("Processing of RTSP feed started, please wait...");
				
		Pipeline pipeline = null;
		AppSrc videoAppSrc = null;
		AppSrcListener videoAppSrcListener = null;
		
		gotCaps.acquire(1);

		pipeline = (Pipeline) Gst.parseLaunch(
			"appsrc name=videoAppSrc "+
			"! videoconvert ! video/x-raw,format=I420 "+
			"! x264enc ! h264parse "+
			"! mpegtsmux name=mux "+
			"! filesink name=filesink "
		);			

        videoAppSrc = (AppSrc) pipeline.getElementByName("videoAppSrc");
		videoAppSrc.setCaps(new Caps(videoCaps.toString()));
        videoAppSrc.set("emit-signals", true);

        videoAppSrcListener = new AppSrcListener(videoQueue,canSend);
        videoAppSrc.connect((AppSrc.NEED_DATA) videoAppSrcListener);   

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
		
		BaseSink filesink = (BaseSink) pipeline.getElementByName("filesink");
		
		while (true) {
			System.out.println("Press ENTER to start processing footage from the cam, or type 'QUIT' and press ENTER to exit...");
			if (!s.nextLine().isEmpty())
				break;
	
			filesink.set("location", "processed"+System.currentTimeMillis()+".mp4");
	
			clearQueue(videoQueue);
			clearQueue(processingQueue);
			videoAppSrcListener.resetSendFlagged();
			
			gotEOSPipeline.drainPermits();
			canSend.drainPermits();
			
	        pipeline.play();
	
			canSend.acquire(1);
			sendData = true;
	
			System.out.println("Press ENTER to stop the process...");
			s.nextLine();

			pipeline.sendEvent(new EOSEvent());
			gotEOSPipeline.acquire(1);
			System.out.println("Process stopped.");
	
			pipeline.stop();	
			sendData = false;
		}
		
		playbin.sendEvent(new EOSEvent());
		gotEOSPlaybin.acquire(1);
		System.out.println("Stopped processing of RTSP feed.");
		
		playbin.stop();
		
		processData = false;
		processingThread.interrupt();

		System.out.println("Exiting program.");

		Gst.deinit();
	}
	
	private static void clearQueue(ArrayBlockingQueue<?> queue) {
		queue.clear();
	}
	
	private static class AppSinkListener implements AppSink.NEW_SAMPLE {
		private ArrayBlockingQueue<FrameInfo> queue;
		private StringBuffer caps;
		private Semaphore gotCaps;
		
		private boolean capsSet;
		
		public AppSinkListener(ArrayBlockingQueue<FrameInfo> queue,StringBuffer caps,Semaphore gotCaps) {
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
            	
                Structure capsStruct = sample.getCaps().getStructure(0);
                videoWidth = capsStruct.getInteger("width");
                videoHeight = capsStruct.getInteger("height");
        		numPixels = videoWidth * videoHeight;

                capsSet = true;
            	gotCaps.release();
            }

            if (sendData) {
            	Buffer srcBuffer = sample.getBuffer();
            	ByteBuffer bb = srcBuffer.map(false);

        		int[] pixels = new int[numPixels];
        		bb.asIntBuffer().get(pixels, 0, numPixels);

        		FrameInfo info = new FrameInfo();
        		info.setCapacity(bb.capacity());
        		info.setPixels(pixels);
        		info.setFlags(NativeFlags.toInt(srcBuffer.getFlags()));
        		info.setDuration(srcBuffer.getDuration());
        		info.setOffset(srcBuffer.getOffset());
        		info.setOffsetEnd(srcBuffer.getOffsetEnd());
        		info.setDecodeTimestamp(srcBuffer.getDecodeTimestamp());
        		info.setPresentationTimestamp(srcBuffer.getPresentationTimestamp());
            	
        		queue.offer(info);
        		
            	srcBuffer.unmap();
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
	
	private static class FrameInfo {
		private int capacity;
		private int[] pixels;
		
		private int flags;
		private long duration;
		private long offset;
		private long offsetEnd;
		private long decodeTimestamp;
		private long presentationTimestamp;

		public int getCapacity() {
			return capacity;
		}
		public void setCapacity(int capacity) {
			this.capacity = capacity;
		}
		public int[] getPixels() {
			return pixels;
		}
		public void setPixels(int[] pixels) {
			this.pixels = pixels;
		}
		public int getFlags() {
			return flags;
		}
		public void setFlags(int flags) {
			this.flags = flags;
		}
		public long getDuration() {
			return duration;
		}
		public void setDuration(long duration) {
			this.duration = duration;
		}
		public long getOffset() {
			return offset;
		}
		public void setOffset(long offset) {
			this.offset = offset;
		}
		public long getOffsetEnd() {
			return offsetEnd;
		}
		public void setOffsetEnd(long offsetEnd) {
			this.offsetEnd = offsetEnd;
		}
		public long getDecodeTimestamp() {
			return decodeTimestamp;
		}
		public void setDecodeTimestamp(long decodeTimestamp) {
			this.decodeTimestamp = decodeTimestamp;
		}
		public long getPresentationTimestamp() {
			return presentationTimestamp;
		}
		public void setPresentationTimestamp(long presentationTimestamp) {
			this.presentationTimestamp = presentationTimestamp;
		}	
	}
	
    private static class ProcessingThread extends Thread {
    	BufferedImage image = null;
        int width;
        int height;
    	int x;
    	int y;
    	int dx;
    	int dy;
		
		@Override
		public void run() {
			while (processData) {
				try {
					FrameInfo info = processingQueue.take();
					
					// Perform processing here, currently all this does
					// is bounce a rectangle around the edges of the video.
					// The process should be as quick as possible. If it takes too long,
					// frames will get skipped and video will look jaggedy.
					if (image==null) {
						image = new BufferedImage(videoWidth, videoHeight, BufferedImage.TYPE_INT_RGB);
						image.setAccelerationPriority(0.0f);
						
			            width = videoWidth/2;
			            height = videoHeight/2;
			            x = width/2;
			            y = height/2;
			            dx = Math.random()<0.5 ? -1 : 1;
			            dy = Math.random()<0.5 ? -1 : 1;
					}
					
		            int[] destPixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		            System.arraycopy(info.getPixels(), 0, destPixels, 0, numPixels);
					
	        		Graphics2D g2d = image.createGraphics();
        			g2d.setColor(Color.RED);
        			g2d.drawRect(x, y, width, height);
            		g2d.dispose();            
            		
            		if (((x+width)==videoWidth)||(x==0))
            			dx = -dx;
            		if (((y+height)==videoHeight)||(y==0))
            			dy = -dy;
            		x += dx;
            		y += dy;
					// End of processing
					
	            	Buffer dstBuffer = new Buffer(info.getCapacity());	            	
	            	dstBuffer.map(true).asIntBuffer().put(destPixels);
	            	dstBuffer.unmap();
	            	
	            	dstBuffer.setFlags(NativeFlags.fromInt(BufferFlags.class, info.getFlags()));
	            	dstBuffer.setDuration(info.getDuration());
	            	dstBuffer.setOffset(info.getOffset());
	            	dstBuffer.setOffsetEnd(info.getOffsetEnd());
	            	dstBuffer.setDecodeTimestamp(info.getDecodeTimestamp());
	            	dstBuffer.setPresentationTimestamp(info.getPresentationTimestamp());
					
	            	dstBuffer.disown();
	        		
	        		videoQueue.put(dstBuffer);
				}
				catch (InterruptedException i) {
					break;
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
    }

}
