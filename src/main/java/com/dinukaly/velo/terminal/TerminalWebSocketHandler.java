package com.dinukaly.velo.terminal;

import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.SandboxSession;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.SandboxRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.TerminalService;
import com.dinukaly.velo.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    // Heartbeat constants
    private static final String PING_MESSAGE = "__ping__";
    private static final String PONG_MESSAGE = "__pong__";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SandboxRepository sandboxRepository;
    private final TerminalService terminalService;
    private final TerminalSessionManager sessionManager;

    //Connection established

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        String wsId = wsSession.getId();
        log.info("[Terminal] WebSocket connected: ws={} uri={}", wsId, wsSession.getUri());

        try {
            //1 Validate JWT token
            String token = extractToken(wsSession);
            if (token == null || !jwtUtil.validateToken(token)) {
                log.warn("[Terminal] Rejected unauthenticated connection: ws={}", wsId);
                closeWithError(wsSession, CloseStatus.POLICY_VIOLATION, "Invalid or missing JWT token");
                return;
            }

            //2 checks ownership
            String email = jwtUtil.getEmailFromToken(token);
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                log.warn("[Terminal] User not found: ws={} email={}", wsId, email);
                closeWithError(wsSession, CloseStatus.NOT_ACCEPTABLE, "User not found");
                return;
            }
            log.info("[Terminal] Authenticated user: ws={} email={}", wsId, email);

            // Resolve project
            String projectIdStr = extractProjectId(wsSession);
            if (projectIdStr == null) {
                closeWithError(wsSession, CloseStatus.BAD_DATA, "Could not resolve project ID from path");
                return;
            }

            UUID projectId;
            try {
                projectId = UUID.fromString(projectIdStr);
            } catch (IllegalArgumentException e) {
                closeWithError(wsSession, CloseStatus.BAD_DATA, "Invalid project ID format");
                return;
            }

            Project project = projectRepository.findByIdAndOwner(projectId, user).orElse(null);
            if (project == null) {
                log.warn("[Terminal] Project not found or ownership mismatch: user={} project={}", email, projectIdStr);
                closeWithError(wsSession, CloseStatus.BAD_DATA, "Project not found or access denied");
                return;
            }

            // find the container
            SandboxSession sandbox = sandboxRepository.findByProject(project).orElse(null);
            if (sandbox == null || !sandbox.isActive()) {
                closeWithError(wsSession, CloseStatus.SERVER_ERROR,
                        "No active sandbox for project " + projectIdStr);
                return;
            }

            String containerId = sandbox.getContainerId();
            log.info("[Terminal] Attaching to container={} for project={}", containerId, projectIdStr);

            // create Docker exec session
            TerminalSession terminalSession = terminalService.createSession(containerId, wsSession);

            // register
            sessionManager.add(wsId, terminalSession);

            log.info("[Terminal] Terminal session ready: ws={} container={}", wsId, containerId);

        } catch (Exception e) {
            log.error("[Terminal] Unexpected error on connection: ws={} error={}", wsId, e.getMessage(), e);
            closeWithError(wsSession, CloseStatus.SERVER_ERROR, "Internal server error");
        }
    }

    // Incoming message

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        String wsId = wsSession.getId();
        String payload = message.getPayload();

        // Heartbeat
        if (PING_MESSAGE.equals(payload)) {
            try {
                wsSession.sendMessage(new TextMessage(PONG_MESSAGE));
            } catch (Exception e) {
                log.warn("[Terminal] Failed to send pong: ws={}", wsId);
            }
            return;
        }

        // Forward to container stdin
        sessionManager.get(wsId).ifPresentOrElse(
                session -> session.sendToContainer(payload),
                () -> log.warn("[Terminal] Received message for unknown session: ws={}", wsId));
    }

    // Connection closed

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        String wsId = wsSession.getId();
        log.info("[Terminal] WebSocket disconnected: ws={} status={}", wsId, status);
        sessionManager.remove(wsId);
    }

    // Error handler

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        log.error("[Terminal] Transport error: ws={} error={}", wsSession.getId(), exception.getMessage());
        sessionManager.remove(wsSession.getId());
    }

    // Helpers
    private String extractToken(WebSocketSession session) {
        String attrToken = (String) session.getAttributes().get("token");
        if (attrToken != null) {
            return attrToken;
        }
        URI uri = session.getUri();
        System.out.println(uri);
        if (uri != null && uri.getQuery() != null) {
            for (String part : uri.getQuery().split("&")) {
                if (part.startsWith("token=")) {
                    return part.substring("token=".length());
                }
            }
        }

        return null;
    }

    //extract the project id
    private String extractProjectId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null)
            return null;
        String path = uri.getPath();
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("projects".equals(parts[i])) {
                return parts[i + 1];
            }
        }
        return null;
    }

    /**
     send error message to the browser
     */
    private void closeWithError(WebSocketSession session, CloseStatus status, String reason) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(
                        "\r\n\u001b[1;31m[Error: " + reason + "]\u001b[0m\r\n"));
                session.close(status.withReason(reason));
            }
        } catch (Exception e) {
            log.warn("[Terminal] Could not close session gracefully: {}", e.getMessage());
        }
    }
}
