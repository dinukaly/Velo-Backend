# ⚙️ Velo Backend

The core backend service for the **Velo** Cloud Development Environment. Built with **Spring Boot 4**, **Java 21**, **Spring AI**, and **Docker Engine API**.

---

## ⚡ Quick Start

### Prerequisites
- **Java Development Kit (JDK)**: `v21`
- **Docker Desktop / Docker Engine**: Running locally
  - Windows: `npipe:////./pipe/docker_engine`
  - Linux/macOS: `unix:///var/run/docker.sock`
- **MySQL Server**: `v8.0+`
- **Redis Server**: `v7.0+`
- **Elasticsearch**: `v8.x` *(Optional — automatic fallback to file-system search if not running)*

---

### Installation & Setup

1. **Navigate to the backend directory:**
   ```bash
   cd backend/velo
   ```

2. **Set up your local configuration:**
   Copy the provided `application.properties.example` template to create your local overrides file:
   ```bash
   cp src/main/resources/application.properties.example src/main/resources/application-local.properties
   ```
   *Note: `application-local.properties` is git-ignored. Activate the `local` profile to load it.*

3. **Configure your database credentials and API keys in `application-local.properties`:**
   ```properties
   spring.datasource.password=your_mysql_password
   spring.ai.openai.api-key=your_openrouter_api_key
   velo.ai.embedding.api-key=your_gemini_api_key
   ```
   Set `jwt.secret` to a unique random value of at least 32 bytes. The `dev-only-change-me` fallback is rejected by startup validation.
   In IntelliJ's Spring Boot run configuration, set **Active profiles** to `local`.

4. **Compile and run the application:**
   ```bash
   # Windows
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

   # Linux / macOS
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```
   The server will start on **`http://localhost:8080`**.

---

## 🛠️ Build & Test Commands

| Command | Description |
|---|---|
| `.\mvnw.cmd test-compile` | Compiles source files and validates tests |
| `.\mvnw.cmd test` | Executes the test suite |
| `.\mvnw.cmd clean package -DskipTests` | Packages the service into a runnable JAR (`target/velo-0.0.1-SNAPSHOT.jar`) |

---

## 🧩 Core Architecture & Services

- **Agent Execution Pipeline (`com.dinukaly.velo.service.impl.AgentExecutionServiceImpl`)**:
  - Asynchronously executes autonomous agent tasks in a dedicated `agentTaskExecutor` thread pool.
  - Current path: `QUEUED` ➔ `RUNNING` ➔ `WAITING_FOR_APPROVAL` ➔ `APPLYING` ➔ `DONE`, with failure, cancellation, rejection, and conflict terminal states.
  - `VERIFYING` is defined for the planned Phase 9 pipeline, but verification commands are not executed yet.
- **Hybrid Code Search & RAG (`HybridSearchServiceImpl`)**:
  - Combines Elasticsearch BM25 lexical keyword search with Google Gemini vector embeddings (`GeminiEmbeddingProviderImpl`), fused using Reciprocal Rank Fusion (RRF).
- **Safe-Apply Engine (`SafeApplyServiceImpl`)**:
  - Verifies SHA-256 base file hashes against disk content before applying hunks.
  - Applies patches using reverse line-offset algorithms to eliminate offset drift.
  - Writes updates atomically via temporary files and swap replace.
- **Docker Sandbox Manager (`DockerService`)**:
  - Manages container lifecycles, memory/CPU quotas, and volume mounting under `workspace.root`.
- **Terminal WebSocket Gateway (`TerminalSession`)**:
  - Connects xterm.js clients to container Linux pseudo-terminals (PTY) with raw binary streaming.
- **Real-Time SSE Service (`AgentSseServiceImpl`)**:
  - Dispatches live step progress and proposal updates after database transaction commits, supporting `Last-Event-ID` reconnection replay.
