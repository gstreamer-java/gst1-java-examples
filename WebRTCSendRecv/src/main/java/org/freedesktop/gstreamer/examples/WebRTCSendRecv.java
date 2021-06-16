/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2021 Neil C Smith - Codelerity Ltd.
 * Copyright 2019 Steve Vangasse 
 *
 * Copying and distribution of this file, with or without modification,
 * are permitted in any medium without royalty provided the copyright
 * notice and this notice are preserved. This file is offered as-is,
 * without any warranty.
 *
 */
package org.freedesktop.gstreamer.examples;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.Element.PAD_ADDED;
import org.freedesktop.gstreamer.elements.DecodeBin;
import org.freedesktop.gstreamer.webrtc.WebRTCBin;
import org.freedesktop.gstreamer.webrtc.WebRTCBin.CREATE_OFFER;
import org.freedesktop.gstreamer.webrtc.WebRTCBin.ON_ICE_CANDIDATE;
import org.freedesktop.gstreamer.webrtc.WebRTCBin.ON_NEGOTIATION_NEEDED;
import org.freedesktop.gstreamer.webrtc.WebRTCSDPType;
import org.freedesktop.gstreamer.webrtc.WebRTCSessionDescription;

import static org.asynchttpclient.Dsl.asyncHttpClient;

/**
 * Demo GStreamer app for negotiating and streaming a sendrecv webrtc stream
 * with a browser JS app.
 *
 * @author Neil C Smith ( https://www.codelerity.com )
 * @author Steve Vangasse
 */
public class WebRTCSendRecv {

    private static final Logger LOG = Logger.getLogger(WebRTCSendRecv.class.getName());

    private static final String REMOTE_SERVER_URL = "wss://webrtc.nirbheek.in:8443";
    private static final String REMOTE_WEBPAGE_URL = "https://webrtc.nirbheek.in";

    private static final String PIPELINE_DESCRIPTION
            = "videotestsrc is-live=true pattern=ball ! videoconvert ! queue ! vp8enc deadline=1 ! rtpvp8pay"
            + " ! queue ! application/x-rtp,media=video,encoding-name=VP8,payload=97 ! webrtcbin. "
            + "audiotestsrc is-live=true wave=sine ! audioconvert ! audioresample ! queue ! opusenc ! rtpopuspay"
            + " ! queue ! application/x-rtp,media=audio,encoding-name=OPUS,payload=96 ! webrtcbin. "
            + "webrtcbin name=webrtcbin bundle-policy=max-bundle stun-server=stun://stun.l.google.com:19302 ";

    private final String serverUrl;
    private final String sessionId;
    private final ObjectMapper mapper = new ObjectMapper();

    private AsyncHttpClient httpClient;
    private WebSocket websocket;
    private WebRTCBin webRTCBin;
    private Pipeline pipe;

    public static void main(String[] args) throws Exception {

        // Set up native paths - see adjacent file
        Utils.configurePaths();

        // Uncomment to output GStreamer debug information
        // GLib.setEnv("GST_DEBUG", "4", true);

        // Initialize GStreamer with minimum version of 1.16.
        Gst.init(Version.of(1, 16));

        // Open remote webpage in local browser
        try {
            Desktop.getDesktop().browse(URI.create(REMOTE_WEBPAGE_URL));
        } catch (Exception ex) {
            System.out.println("Open a web browser at : " + REMOTE_WEBPAGE_URL);
        }

        // Enter session ID from webpage in CLI
        // If running with Gradle in a terminal, use ./gradlew --console=plain run
        String session;
        System.out.print("Enter session ID : ");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            session = reader.readLine().trim();
        }

        // Initialize call - make sure webpage is set to allow audio in browser
        WebRTCSendRecv webrtcSendRecv = new WebRTCSendRecv(session, REMOTE_SERVER_URL);
        webrtcSendRecv.startCall();
    }

    private WebRTCSendRecv(String sessionId, String serverUrl) {
        this.sessionId = sessionId;
        this.serverUrl = serverUrl;
        pipe = (Pipeline) Gst.parseLaunch(PIPELINE_DESCRIPTION);
        webRTCBin = (WebRTCBin) pipe.getElementByName("webrtcbin");

        setupPipeLogging(pipe);

        // When the pipeline goes to PLAYING, the on_negotiation_needed() callback
        // will be called, and we will ask webrtcbin to create an offer which will
        // match the pipeline above.
        webRTCBin.connect(onNegotiationNeeded);
        webRTCBin.connect(onIceCandidate);
        webRTCBin.connect(onIncomingStream);
    }

    private void startCall() throws Exception {
        httpClient = asyncHttpClient();
        websocket = httpClient
                .prepareGet(serverUrl)
                .execute(
                        new WebSocketUpgradeHandler.Builder()
                                .addWebSocketListener(webSocketListener)
                                .build())
                .get();

        Gst.main();
    }

    private final WebSocketListener webSocketListener = new WebSocketListener() {

        @Override
        public void onOpen(WebSocket websocket) {
            LOG.info("websocket onOpen");
            websocket.sendTextFrame("HELLO 852978");
        }

        @Override
        public void onClose(WebSocket websocket, int code, String reason) {
            LOG.info(() -> "WebSocket onClose : " + code + " : " + reason);
            endCall();
        }

        @Override
        public void onTextFrame(String payload, boolean finalFragment, int rsv) {
            if (payload.equals("HELLO")) {
                websocket.sendTextFrame("SESSION " + sessionId);
            } else if (payload.equals("SESSION_OK")) {
                pipe.play();
            } else if (payload.startsWith("ERROR")) {
                LOG.severe(payload);
                endCall();
            } else {
                handleSdp(payload);
            }
        }

        @Override
        public void onError(Throwable t) {
            LOG.log(Level.SEVERE, "onError", t);
        }
    };

    private void handleSdp(String payload) {
        try {
            JsonNode answer = mapper.readTree(payload);
            if (answer.has("sdp")) {
                String sdpStr = answer.get("sdp").get("sdp").textValue();
                LOG.info(() -> "Answer SDP:\n" + sdpStr);
                SDPMessage sdpMessage = new SDPMessage();
                sdpMessage.parseBuffer(sdpStr);
                WebRTCSessionDescription description = new WebRTCSessionDescription(WebRTCSDPType.ANSWER, sdpMessage);
                webRTCBin.setRemoteDescription(description);
            } else if (answer.has("ice")) {
                String candidate = answer.get("ice").get("candidate").textValue();
                int sdpMLineIndex = answer.get("ice").get("sdpMLineIndex").intValue();
                LOG.info(() -> "Adding ICE candidate : " + candidate);
                webRTCBin.addIceCandidate(sdpMLineIndex, candidate);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Problem reading payload", e);
        }
    }

    private void setupPipeLogging(Pipeline pipe) {
        Bus bus = pipe.getBus();
        bus.connect((Bus.EOS) source -> {
            LOG.info(() -> "Reached end of stream : " + source.toString());
            endCall();
        });

        bus.connect((Bus.ERROR) (source, code, message) -> {
            LOG.severe(() -> "Error from source : " + source
                    + ", with code : " + code + ", and message : " + message);
            endCall();
        });

        bus.connect((source, old, current, pending) -> {
            if (source instanceof Pipeline) {
                LOG.info(() -> "Pipe state changed from " + old + " to " + current);
            }
        });
    }

    private void endCall() {
        pipe.setState(State.NULL);
        try {
            httpClient.close();
        } catch (IOException ex) {
        }
        Gst.quit();
    }

    private CREATE_OFFER onOfferCreated = offer -> {
        webRTCBin.setLocalDescription(offer);
        try {
            ObjectNode rootNode = mapper.createObjectNode();
            ObjectNode sdpNode = mapper.createObjectNode();
            sdpNode.put("type", "offer");
            sdpNode.put("sdp", offer.getSDPMessage().toString());
            rootNode.set("sdp", sdpNode);
            String json = mapper.writeValueAsString(rootNode);
            LOG.info(() -> "Sending offer:\n" + json);
            websocket.sendTextFrame(json);
        } catch (JsonProcessingException e) {
            LOG.log(Level.SEVERE, "Couldn't write JSON", e);
        }
    };

    private final ON_NEGOTIATION_NEEDED onNegotiationNeeded = elem -> {
        LOG.info(() -> "onNegotiationNeeded: " + elem.getName());

        // When webrtcbin has created the offer, it will hit our callback and we
        // send SDP offer over the websocket to signalling server
        webRTCBin.createOffer(onOfferCreated);
    };

    private final ON_ICE_CANDIDATE onIceCandidate = (sdpMLineIndex, candidate) -> {
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode iceNode = mapper.createObjectNode();
        iceNode.put("candidate", candidate);
        iceNode.put("sdpMLineIndex", sdpMLineIndex);
        rootNode.set("ice", iceNode);

        try {
            String json = mapper.writeValueAsString(rootNode);
            LOG.info(() -> "ON_ICE_CANDIDATE: " + json);
            websocket.sendTextFrame(json);
        } catch (JsonProcessingException e) {
            LOG.log(Level.SEVERE, "Couldn't write JSON", e);
        }
    };

    private final PAD_ADDED onDecodedStream = (element, pad) -> {
        if (!pad.hasCurrentCaps()) {
            LOG.info("Pad has no current Caps - ignoring");
            return;
        }
        Caps caps = pad.getCurrentCaps();
        LOG.info(() -> "Received decoded stream with caps : " + caps.toString());
        if (caps.isAlwaysCompatible(Caps.fromString("video/x-raw"))) {
            Element q = ElementFactory.make("queue", "videoqueue");
            Element conv = ElementFactory.make("videoconvert", "videoconvert");
            Element sink = ElementFactory.make("autovideosink", "videosink");
            pipe.addMany(q, conv, sink);
            q.syncStateWithParent();
            conv.syncStateWithParent();
            sink.syncStateWithParent();
            pad.link(q.getStaticPad("sink"));
            q.link(conv);
            conv.link(sink);
        } else if (caps.isAlwaysCompatible(Caps.fromString("audio/x-raw"))) {
            Element q = ElementFactory.make("queue", "audioqueue");
            Element conv = ElementFactory.make("audioconvert", "audioconvert");
            Element resample = ElementFactory.make("audioresample", "audioresample");
            Element sink = ElementFactory.make("autoaudiosink", "audiosink");
            pipe.addMany(q, conv, resample, sink);
            q.syncStateWithParent();
            conv.syncStateWithParent();
            resample.syncStateWithParent();
            sink.syncStateWithParent();
            pad.link(q.getStaticPad("sink"));
            q.link(conv);
            conv.link(resample);
            resample.link(sink);
        }
    };

    private final PAD_ADDED onIncomingStream = (element, pad) -> {
        LOG.info(()
                -> "Receiving stream! Element : " + element.getName()
                + " Pad : " + pad.getName());
        if (pad.getDirection() != PadDirection.SRC) {
            return;
        }
        DecodeBin decodeBin = new DecodeBin("decodebin_" + pad.getName());
        decodeBin.connect(onDecodedStream);
        pipe.add(decodeBin);
        decodeBin.syncStateWithParent();
        pad.link(decodeBin.getStaticPad("sink"));
    };

}
