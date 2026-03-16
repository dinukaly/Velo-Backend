package com.dinukaly.velo.service;

import com.dinukaly.velo.terminal.TerminalSession;
import org.springframework.web.socket.WebSocketSession;


public interface TerminalService {
    TerminalSession createSession(String containerId, WebSocketSession wsSession);
}
