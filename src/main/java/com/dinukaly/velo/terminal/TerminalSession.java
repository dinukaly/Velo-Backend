package com.dinukaly.velo.terminal;

import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


@Slf4j
@Getter
public class TerminalSession {

    private final WebSocketSession wsSession;
    private final String containerId;

    /** The exec instance ID returned by DockerClient.execCreateCmd. */
    private final String execId;

    /**
     * OutputStream connected to the container's stdin.
     * Write here to send keystrokes / commands to the shell.
     */
    private final OutputStream stdin;

    /**
     * The read end of the pipe. Read by the Docker SDK to forward to container's stdin.
     */
    private final InputStream stdinRead;

    /**
     * Callback that reads Docker output frames and forwards them to the WebSocket
     */
    private final ExecStartResultCallback callback;

    // -------------------------------------------------------------------------

    public TerminalSession(
            WebSocketSession wsSession,
            String containerId,
            String execId,
            OutputStream stdin,
            InputStream stdinRead,
            ExecStartResultCallback callback) {
        this.wsSession    = wsSession;
        this.containerId  = containerId;
        this.execId       = execId;
        this.stdin        = stdin;
        this.stdinRead    = stdinRead;
        this.callback     = callback;
    }

    /**
     * Forward raw bytes from the browser keyboard to the container's stdin.
     *
     */
    public void sendToContainer(String data) {
        try {
            stdin.write(data.getBytes());
            stdin.flush();
        } catch (IOException e) {
            log.warn("[Terminal] Failed to write to container stdin (session={}): {}",
                    wsSession.getId(), e.getMessage());
        }
    }

    /**
     * Release all resources held by this session:
     * -Close the stdin pipe
     * -Close the stdout stream
     */
    public void close() {
        log.info("[Terminal] Closing terminal session for container={} ws={}",
                containerId, wsSession.getId());

        tryClose(stdin,"stdinWrite");
        tryClose(stdinRead,"stdinRead");
        tryClose(callback,"exec-callback");
    }

    // Helpers -------

    private void tryClose(AutoCloseable resource, String label) {
        if (resource == null) return;
        try {
            resource.close();
        } catch (Exception e) {
            log.warn("[Terminal] Error closing {} for session {}: {}",
                    label, wsSession.getId(), e.getMessage());
        }
    }
}
