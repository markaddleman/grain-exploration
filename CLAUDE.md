# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**grain-exploration** is an experimental repository for exploring the **Grain framework** - an AI-native framework for building Event-Sourced systems with integrated Agentic Workflows. The project contains:

1. **Main project** (`/`) - Uses Grain as external dependencies via Git
2. **Grain framework source** (`lib-sources/grain/`) - Full Polylith monorepo with 21 reusable components
3. **Demo application** (`lib-sources/macroexpand-2-demo/`) - Example implementations at 3 complexity levels

The architecture combines **Event Sourcing/CQRS** with **LLM-based Behavior Trees** using **DSPy** (Language Model Programming), demonstrating how to build autonomous agents with persistent memory.

## Build & Development Commands

### Main Project (grain-exploration)

```bash
# Start interactive REPL with external Grain dependencies resolved
clj

# Run REPL with additional development tools
clj -M:dev
```

### Grain Framework (lib-sources/grain)

```bash
# Start development REPL with all framework components
cd lib-sources/grain
clj -M:dev

# Run Polylith CLI for workspace management
clj -M:poly check          # Verify workspace integrity
clj -M:poly ns             # Show namespace organization
clj -M:poly libs           # Show library dependencies
clj -M:poly info           # Get workspace information
```

### Demo Application (lib-sources/macroexpand-2-demo)

```bash
cd lib-sources/macroexpand-2-demo

# First-time setup
python -m venv .venv
source .venv/bin/activate  # macOS/Linux
pip install -r requirements.txt

# Run tests
clj -M:test                # Via test alias
clj -M:build test          # Via build script

# Run full CI pipeline
clj -M:build ci            # Runs tests and builds JAR

# Run single test
clj -M:test -- --var ai.obney.grain.example-namespace/test-name

# Interactive REPL
clj
```

## Testing

**Test Framework:** Clojure's `clojure.test` with `cognitect/test-runner` and `test.check` (property-based testing)

**Test Location:** Tests are colocated with components in `test/` directories using namespace pattern `ai.obney.grain.{component}/{interface}_test.clj`

**Running Tests:**
- `clj -M:test` - Run all tests
- `clj -M:test -- --var my.namespace/test-name` - Run specific test
- `clj -M:build test` - Run via build script (demo app)

## Linting & Code Quality

**clj-kondo** is configured in `lib-sources/grain/.clj-kondo/config.edn` with custom macro rules:
- Maps Grain DSL macros to standard forms for proper linting
- Examples: `defschemas`, `defsignature`, `defmodel`

```bash
# Run linter (requires clj-kondo CLI installed)
clj-kondo --lint src lib-sources/grain/components
```

## Architecture & Design Patterns

### Core Patterns

1. **Polylith Architecture** - Monorepo with clear separation:
   - **Components** (`lib-sources/grain/components/`) - 21 reusable, independent modules
   - **Bases** (`lib-sources/grain/bases/`) - Application entry points
   - **Projects** (`lib-sources/grain/projects/`) - Published libraries (grain-core, grain-dspy-extensions, etc.)

2. **Event Sourcing + CQRS**
   - All state changes captured as immutable events
   - Single source of truth in event store
   - Commands → Events → Projections (query models)
   - Protocol-driven: In-memory or PostgreSQL backend

3. **Agent Framework** - Behavior Trees + DSPy
   - **Behavior Trees** - Declarative tree structure with sequences, conditions, actions
   - **DSPy Integration** - Clojure wrapper for LLM programming (signatures, models, chainable modules)
   - **Memory** - Short-term state + long-term event-sourced history
   - **Tool Use** - Agents can call external tools via LLM function calling

4. **Protocol-Driven Design** - Interfaces abstracted as protocols, enabling multiple implementations

### Key Components

**Event Sourcing & Storage:**
- `event-store-v2` - Protocol-based event store (in-memory default)
- `event-store-postgres-v2` - PostgreSQL backend
- `event-model` - Event definition

**Agent Framework:**
- `behavior-tree-v2` - Declarative behavior tree engine
- `behavior-tree-v2-dspy-extensions` - DSPy integration for trees
- `clj-dspy` - Clojure wrapper for DSPy

**CQRS Infrastructure:**
- `command-processor` - Command handling
- `query-processor` - Query processing
- `command-request-handler` - HTTP command routing
- `query-request-handler` - HTTP query routing

**Utilities:**
- `webserver` - Pedestal-based HTTP server
- `schema-util` - Schema definitions
- `pubsub` - Pub/Sub messaging
- `periodic-task` - Scheduled task execution
- `time`, `anomalies` - Common utilities

### Dependency Injection

Uses **Integrant** for component lifecycle and dependency injection. Configuration typically in `resources/system.edn` or similar.

## Dependencies

**Package Manager:** Clojure CLI with `deps.edn` (Git and Maven coordinates)

**Key Dependencies:**
- `clojure 1.12.2` - Core language
- `core.async` - Asynchronous programming
- `pedestal` (0.7.2) - Web framework
- `malli` - Schema/validation
- `cheshire` - JSON processing
- `libpython-clj` - Python interop (for DSPy)
- `dspy` (Python) - Language Model Programming library
- `litellm` (Python) - LLM API abstraction
- `mulog` - Structured logging
- `integrant` - Dependency injection
- `tools.build` - Build tooling (demo app)

**Python Requirements:**
- Python 3.12+ required for demo app
- `dspy==2.6.27` - Language Model Programming
- `litellm==1.75.3` - LLM API abstraction

## Project Structure Quick Reference

```
grain-exploration/
├── deps.edn                           # External Grain dependencies
├── python.edn                         # Python env config
├── CLAUDE.md                          # This file
├── .gitignore
├── src/                               # Local exploration code (empty)
│
└── lib-sources/
    ├── grain/                         # Full Grain framework source
    │   ├── components/                # 21 reusable components
    │   ├── bases/                     # Application templates
    │   ├── projects/                  # Published libraries
    │   ├── development/               # Demo code
    │   ├── workspace.edn              # Polylith workspace config
    │   ├── deps.edn
    │   ├── python.edn
    │   └── readme.md                  # Comprehensive documentation
    │
    └── macroexpand-2-demo/            # Demo application
        ├── src/                       # Demo code (4 examples + utilities)
        ├── test/                      # Tests
        ├── deps.edn
        ├── build.clj                  # Build script
        ├── python.edn
        ├── requirements.txt
        └── README.md
```

## Demo Application Examples

Located in `lib-sources/macroexpand-2-demo/src/`:

1. **01_hello_world.clj** - Naive chatbot (no memory)
2. **02_conversational_memory.clj** - Chatbot with persistent memory
3. **03_code_agent_jr.clj** - Research agent with tool use (external API calls)
4. **04_llama_parse_demo.clj** - Document parsing example (bonus)

Run any example in the REPL: `(load-file "src/01_hello_world.clj")`

## Documentation References

- **Framework Overview:** `lib-sources/grain/readme.md`
- **Demo Setup:** `lib-sources/macroexpand-2-demo/README.md`
- **Example Code:** `lib-sources/grain/development/src/example_app_demo.clj`
- **Component APIs:** Inspect interface namespaces in `lib-sources/grain/components/*/src/ai/obney/grain/*/interface.clj`

## Common Development Tasks

### Exploring Components

```bash
cd lib-sources/grain
clj
# In REPL:
(require '[ai.obney.grain.event-store-v2.interface :as es])
(require '[ai.obney.grain.command-processor.interface :as cp])
```

### Running Demo in REPL

```bash
cd lib-sources/macroexpand-2-demo
clj
# In REPL:
(load-file "src/01_hello_world.clj")
; Follow prompts and interact with chatbot
```

### Checking Workspace Health

```bash
cd lib-sources/grain
clj -M:poly check
```

## Notes

- All state is immutable; changes are captured as events
- Event store defaults to in-memory but can switch to PostgreSQL via configuration
- DSPy integration requires Python 3.12+ and virtual environment setup
- Components are designed to be mixed and matched; don't depend on implementation details—use public interfaces
- The Polylith architecture allows components to be extracted into separate libraries independently
