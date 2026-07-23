package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.APIResponse;
import com.dinukaly.velo.dto.agent.AgentRunDetailDTO;
import com.dinukaly.velo.dto.agent.AgentRunResponseDTO;
import com.dinukaly.velo.dto.agent.CreateAgentRunRequestDTO;
import com.dinukaly.velo.service.AgentRunService;
import com.dinukaly.velo.service.AgentSseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/agent")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class AgentController {

    private final AgentRunService agentRunService;
    private final AgentSseService agentSseService;

    // -------------------------------------------------------------------------
    // POST /runs  —  Create a new agent run
    // -------------------------------------------------------------------------

    @PostMapping("/runs")
    public ResponseEntity<APIResponse> createRun(
            @Valid @RequestBody CreateAgentRunRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Create agent run for project [{}] by user [{}]",
                request.getProjectId(), userDetails.getUsername());

        AgentRunResponseDTO run = agentRunService.createRun(request, userDetails.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new APIResponse(201, "Agent run created", run));
    }

    // -------------------------------------------------------------------------
    // GET /runs/{runId}  —  Fetch full run detail (run + steps)
    // -------------------------------------------------------------------------

    @GetMapping("/runs/{runId}")
    public ResponseEntity<APIResponse> getRunDetail(
            @PathVariable UUID runId,
            @AuthenticationPrincipal UserDetails userDetails) {

        AgentRunDetailDTO detail = agentRunService.getRunDetail(runId, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Agent run fetched", detail));
    }

    // -------------------------------------------------------------------------
    // GET /runs/{runId}/events  —  SSE stream for live progress
    // -------------------------------------------------------------------------

    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToEvents(
            @PathVariable UUID runId,
            @RequestHeader(value = "Last-Event-ID", defaultValue = "0") long lastEventId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("SSE subscribe to run [{}] by user [{}], lastEventId={}",
                runId, userDetails.getUsername(), lastEventId);

        return agentSseService.subscribe(runId, userDetails.getUsername(), lastEventId);
    }

    // -------------------------------------------------------------------------
    // POST /runs/{runId}/cancel  —  Cancel an active run
    // -------------------------------------------------------------------------

    @PostMapping("/runs/{runId}/cancel")
    public ResponseEntity<APIResponse> cancelRun(
            @PathVariable UUID runId,
            @AuthenticationPrincipal UserDetails userDetails) {

        agentRunService.cancelRun(runId, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Agent run cancelled", null));
    }

    // -------------------------------------------------------------------------
    // POST /runs/{runId}/reject  —  Reject a run awaiting approval
    // -------------------------------------------------------------------------

    @PostMapping("/runs/{runId}/reject")
    public ResponseEntity<APIResponse> rejectRun(
            @PathVariable UUID runId,
            @AuthenticationPrincipal UserDetails userDetails) {

        agentRunService.rejectRun(runId, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Agent run rejected", null));
    }
}
