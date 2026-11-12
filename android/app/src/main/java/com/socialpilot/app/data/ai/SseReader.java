package com.socialpilot.app.data.ai;

import java.io.IOException;
import java.net.ProtocolException;
import okio.BufferedSource;
import okio.ByteString;

final class SseReader {
    private static final long MAX_LINE_BYTES = 256 * 1024;
    private static final int MAX_EVENT_CHARS = 256 * 1024;
    private static final ByteString LINE_ENDS = ByteString.encodeUtf8("\r\n");
    private final BufferedSource source;
    private boolean skipLineFeed;

    SseReader(BufferedSource source) {
        this.source = source;
    }

    /** Returns the next complete SSE data event. A truncated event is a protocol error. */
    String next() throws IOException {
        StringBuilder data = new StringBuilder();
        boolean hasData = false;
        while (true) {
            String line = nextLine();
            if (line == null) {
                if (hasData) throw new ProtocolException("Unterminated SSE event");
                return null;
            }
            if (line.isEmpty()) {
                if (hasData) return data.toString();
                continue;
            }
            if (!line.equals("data") && !line.startsWith("data:")) continue;
            int start = line.equals("data") ? line.length() : 5;
            if (start < line.length() && line.charAt(start) == ' ') start++;
            if (data.length() + line.length() - start + (hasData ? 1 : 0) > MAX_EVENT_CHARS) {
                throw new ProtocolException("SSE event too large");
            }
            if (hasData) data.append('\n');
            data.append(line, start, line.length());
            hasData = true;
        }
    }

    private String nextLine() throws IOException {
        if (skipLineFeed && source.request(1) && source.getBuffer().getByte(0) == '\n') {
            source.skip(1);
        }
        long scanned = 0;
        while (source.request(scanned + 1)) {
            long end = source.getBuffer().indexOfElement(LINE_ENDS, scanned);
            if (end != -1 && end <= MAX_LINE_BYTES) {
                String line = source.readUtf8(end);
                skipLineFeed = source.readByte() == '\r';
                return line;
            }
            scanned = source.getBuffer().size();
            if (scanned > MAX_LINE_BYTES) throw new ProtocolException("SSE line too large");
        }
        if (scanned != 0) throw new ProtocolException("Unterminated SSE line");
        return null;
    }
}
