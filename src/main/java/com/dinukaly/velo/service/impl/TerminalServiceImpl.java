package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.service.TerminalService;
import com.dinukaly.velo.terminal.TerminalSession;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.api.model.Frame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;


@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalServiceImpl implements TerminalService {

    private final DockerClient dockerClient;

    private static final int PIPE_BUFFER_SIZE = 64 * 1024;

    @Override
    public TerminalSession createSession(String containerId, WebSocketSession wsSession) {
        log.info("[TerminalService] Creating exec session: container={} ws={}",
                containerId, wsSession.getId());

        // Create exec instance
        ExecCreateCmdResponse execResponse = dockerClient
                .execCreateCmd(containerId)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
               // .withEnv("TERM=xterm-256color", "COLORTERM=truecolor")
                .withCmd("sh", "-c",
                        "export TERM=xterm-256color; " +
                                "export COLORTERM=truecolor; " +
                                "exec sh -l"
                )
                .exec();

        String execId = execResponse.getId();
        log.info("[TerminalService] Exec created: execId={}", execId);

        // Build a piped stdin
        // PipedOutputStream is the write end that hand to the WS handler.
        // PipedInputStream is the read end the Docker SDK pulls from.
        PipedOutputStream stdinWrite = new PipedOutputStream();
        InputStream  stdinRead;
        try {
            stdinRead = new PipedInputStream(stdinWrite, PIPE_BUFFER_SIZE);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create stdin pipe", e);
        }

        // Build the output callback
        // This callback runs on the Docker SDK's background thread.
        // Each Frame payload is a chunk of terminal output (text + ANSI).
        ExecStartResultCallback callback = new ExecStartResultCallback() {
            @Override
            public void onNext(Frame frame) {
                if (frame == null || frame.getPayload() == null) return;
                String chunk = new String(frame.getPayload());
                sendSafe(wsSession, chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("[TerminalService] Docker exec error (execId={}): {}",
                        execId, throwable.getMessage());
                // Notify the browser that the session died.
                sendSafe(wsSession, "\r\n\u001b[1;31m[Terminal process exited]\u001b[0m\r\n");
                closeWs(wsSession);
            }

            @Override
            public void onComplete() {
                log.info("[TerminalService] Docker exec completed: execId={}", execId);
                sendSafe(wsSession, "\r\n\u001b[1;33m[Shell session ended]\u001b[0m\r\n");
                closeWs(wsSession);
            }
        };

        // Start the exec (non-blocking)
        // withStdIn pipes our PipedInputStream into the container's stdin.
        // The .exec(callback) call returns immediately; output frames are delivered to callback.onNext() on the SDK thread pool.
        dockerClient
                .execStartCmd(execId)
                .withStdIn(stdinRead)
                .exec(callback);

        log.info("[TerminalService] Exec started: execId={} container={}", execId, containerId);

        // Return the session handle
        return new TerminalSession(
                wsSession,
                containerId,
                execId,
                stdinWrite,   // write end  > WS handler uses this
                stdinRead,    // read end   > Docker SDK reads this
                callback
        );
    }

    // Helpers

    /**
     * Send text to the WebSocket,  synchronising on the session to prevent
     * concurrent writes from the Docker callback thread.
     */
    private void sendSafe(WebSocketSession ws, String text) {
        if (!ws.isOpen()) return;
        synchronized (ws) {
            try {
                ws.sendMessage(new org.springframework.web.socket.TextMessage(text));
            } catch (Exception e) {
                log.warn("[TerminalService] Failed to send to ws={}: {}", ws.getId(), e.getMessage());
            }
        }
    }

    /**
     * Close the WebSocket session cleanly when the backend process ends.
     */
    private void closeWs(WebSocketSession ws) {
        if (!ws.isOpen()) return;
        try {
            ws.close();
        } catch (Exception e) {
            log.warn("[TerminalService] Failed to close ws={}: {}", ws.getId(), e.getMessage());
        }
    }
}
