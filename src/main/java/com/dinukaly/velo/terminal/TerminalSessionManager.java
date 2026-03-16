package com.dinukaly.velo.terminal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
public class TerminalSessionManager {

    private final ConcurrentHashMap<String, TerminalSession> sessions = new ConcurrentHashMap<>();

    // CRUD operations

    /**
     * Register terminal session.
     */
    public void add(String wsSessionId, TerminalSession terminalSession) {
        sessions.put(wsSessionId, terminalSession);
        log.info("[TerminalSessionManager] Registered session: ws={} container={}",
                wsSessionId, terminalSession.getContainerId());
    }

    /**
     * get active session
     */
    public Optional<TerminalSession> get(String wsSessionId) {
        return Optional.ofNullable(sessions.get(wsSessionId));
    }

    /**
     * Remove and cleanly close the terminal session
     */
    public void remove(String wsSessionId) {
        TerminalSession session = sessions.remove(wsSessionId);
        if (session != null) {
            log.info("[TerminalSessionManager] Removing and closing session: ws={}", wsSessionId);
            session.close();
        } else {
            log.debug("[TerminalSessionManager] No session to remove for ws={}", wsSessionId);
        }
    }

    // return active terminal counts
    public int activeCount() {
        return sessions.size();
    }
}
