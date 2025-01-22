package bgu.spl.net.impl.stomp;

import java.util.Map;
import java.util.HashMap;

public class Frame {
    private String command;
    private Map<String, String> headers = new HashMap<>();
    private String body;

    public Frame(String command) {
        this.command = command;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCommand() {
        return command;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    public String getBody() {
        return body;
    }

    public static Frame parseFrame(String rawFrame) {
        if (rawFrame == null || rawFrame.isEmpty()) {
            throw new IllegalArgumentException("Frame is empty or null");
        }

        String[] lines = rawFrame.split("\n");
        if (lines.length == 0) {
            throw new IllegalArgumentException("Frame has no content");
        }

        // First line is the command
        String command = lines[0].trim();
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Frame command is empty");
        }

        Frame frame = new Frame(command);

        // Parse headers
        int i = 1;
        while (i < lines.length && !lines[i].trim().isEmpty()) {
            String[] header = lines[i].split(":", 2);
            if (header.length != 2) {
                throw new IllegalArgumentException("Invalid header format: " + lines[i]);
            }
            frame.addHeader(header[0].trim(), header[1].trim());
            i++;
        }

        // Parse body (if any)
        if (i + 1 < lines.length) {
            StringBuilder body = new StringBuilder();
            for (int j = i + 1; j < lines.length; j++) {
                body.append(lines[j]).append("\n");
            }
            frame.setBody(body.toString().trim());
        }

        return frame;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(command).append("\n");
        headers.forEach((key, value) -> sb.append(key).append(":").append(value).append("\n"));
        if (body != null && !body.isEmpty()) {
            sb.append("\n").append(body);
        }
        sb.append('\u0000');
        return sb.toString();
    }
}
