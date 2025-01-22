package bgu.spl.net.impl.stomp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

import bgu.spl.net.api.MessageEncoderDecoder;

public class StompEncoderDecoder implements MessageEncoderDecoder<String> {
    
    private static final Logger logger = Logger.getLogger(StompEncoderDecoder.class.getName());
    private StringBuilder currentMessage = new StringBuilder();

    @Override
    public String decodeNextByte(byte nextByte) {
        logger.fine("Decoding byte: " + nextByte);
        if (nextByte == '\u0000') { // End of frame
            try {
                Frame frame = Frame.parseFrame(currentMessage.toString());
                currentMessage.setLength(0); // Reset buffer
                logger.info("Decoded frame: " + frame.toString());
                return frame.toString();
            } catch (IllegalArgumentException e) {
                logger.severe("[ERROR] Failed to parse frame: " + e.getMessage());
                currentMessage.setLength(0); // Reset buffer even if an error occurs
                return null;
            }
        }
        currentMessage.append((char) nextByte);
        return null;
    }

    @Override
    public byte[] encode(String message) {
        logger.info("Encoding message: " + message);
        return (message).getBytes(StandardCharsets.UTF_8);
    }
}
