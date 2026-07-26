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
    private final com.dinukaly.velo.service.AgentToolService agentToolService;
    private final com.dinukaly.velo.service.IndexManagementService indexManagementService;
    private final com.dinukaly.velo.service.HybridSearchService hybridSearchService;

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

    // -------------------------------------------------------------------------
    // Tool Inspection Endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/tools/list-directory")
    public ResponseEntity<APIResponse> listDirectory(
            @RequestParam UUID projectId,
            @RequestParam(defaultValue = "") String relativePath,
            @AuthenticationPrincipal UserDetails userDetails) {

        var result = agentToolService.listDirectory(projectId, relativePath, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Directory listed", result));
    }

    @GetMapping("/tools/read-file")
    public ResponseEntity<APIResponse> readFile(
            @RequestParam UUID projectId,
            @RequestParam String relativePath,
            @AuthenticationPrincipal UserDetails userDetails) {

        var result = agentToolService.readFile(projectId, relativePath, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "File read", result));
    }

    @GetMapping("/tools/read-file-range")
    public ResponseEntity<APIResponse> readFileRange(
            @RequestParam UUID projectId,
            @RequestParam String relativePath,
            @RequestParam int startLine,
            @RequestParam int endLine,
            @AuthenticationPrincipal UserDetails userDetails) {

        var result = agentToolService.readFileRange(projectId, relativePath, startLine, endLine, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "File range read", result));
    }

    @GetMapping("/tools/search-code")
    public ResponseEntity<APIResponse> searchCode(
            @RequestParam UUID projectId,
            @RequestParam String query,
            @AuthenticationPrincipal UserDetails userDetails) {

        var result = agentToolService.searchCode(projectId, query, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Code search completed", result));
    }

    /**
     * Hybrid semantic + lexical code search (BM25 + vector via RRF).
     * Falls back to BM25-only or filesystem search when ES or embeddings are unavailable.
     */
    @GetMapping("/tools/hybrid-search")
    public ResponseEntity<APIResponse> hybridSearch(
            @RequestParam UUID projectId,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK,
            @AuthenticationPrincipal UserDetails userDetails) {

        var result = hybridSearchService.search(projectId, query, topK, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Hybrid search completed", result));
    }

    @GetMapping("/tools/git-status")
    public ResponseEntity<APIResponse> getGitStatus(
            @RequestParam UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        var result = agentToolService.getGitStatus(projectId, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Git status fetched", result));
    }

    @GetMapping("/tools/git-diff")
    public ResponseEntity<APIResponse> getGitDiff(
            @RequestParam UUID projectId,
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "false") boolean staged,
            @AuthenticationPrincipal UserDetails userDetails) {

        var result = agentToolService.getGitDiff(projectId, path, staged, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Git diff fetched", result));
    }

    // -------------------------------------------------------------------------
    // Project Index Management Endpoints
    // -------------------------------------------------------------------------

    @PostMapping("/projects/{projectId}/index")
    public ResponseEntity<APIResponse> indexProject(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "INCREMENTAL") String mode,
            @AuthenticationPrincipal UserDetails userDetails) {

        var result = indexManagementService.indexProject(projectId, mode, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Project indexing triggered", result));
    }

    @GetMapping("/projects/{projectId}/index/status")
    public ResponseEntity<APIResponse> getIndexStatus(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        var result = indexManagementService.getIndexStatus(projectId, userDetails.getUsername());
        return ResponseEntity.ok(new APIResponse(200, "Project index status fetched", result));
    }
}
