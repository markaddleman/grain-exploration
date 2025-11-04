# Grain Framework Built-in Operations

A comprehensive reference guide for all built-in operations, nodes, and functions in the Grain framework.

## Table of Contents

1. [Behavior Tree Nodes](#behavior-tree-nodes)
2. [DSPy Integration](#dspy-integration)
3. [Event Store Operations](#event-store-operations)
4. [Command & Query Processing](#command--query-processing)
5. [Utility Components](#utility-components)
6. [Schema & Validation](#schema--validation)
7. [Event Model](#event-model)

---

## Behavior Tree Nodes

**Location:** `lib-sources/grain/components/behavior-tree-v2/`

### Core DSL Functions

#### `build`

```clojure
(bt/build tree-config context)
```

Build a behavior tree from configuration and context.

**Parameters:**

- `tree-config` - Tree structure using DSL (vector format)
- `context` - Map with:
    - `:event-store` - Event store instance (optional)
    - `:st-memory` - Initial short-term memory map
    - `:queries` - Vector of event store queries (for long-term memory)
    - `:read-model-fn` - Function `[initial-state events] -> state` (for long-term memory)

**Returns:** Built behavior tree

#### `run`

```clojure
(bt/run built-tree)
```

Execute or "tick" a built behavior tree.

**Returns:**

- `:success` - Tree completed successfully
- `:running` - Tree needs another tick
- `:failure` - Tree failed

### Built-in Node Types

#### 1. `:sequence`

Executes children in order. Succeeds if all children succeed.

```clojure
[:sequence
 child-1
 child-2
 child-3]
```

**Behavior:**

- Returns `:success` if all children succeed
- Returns `:failure` immediately if any child fails
- Returns `:running` if any child returns `:running`
- Executes children sequentially from left to right

**Use Cases:**

- Step-by-step workflows
- Pipelines requiring all steps to complete
- Validation chains

---

#### 2. `:fallback`

Executes children in order. Succeeds if any child succeeds.

```clojure
[:fallback
 child-1
 child-2
 child-3]
```

**Behavior:**

- Returns `:success` immediately if any child succeeds
- Returns `:failure` if all children fail
- Returns `:running` if any child returns `:running`
- Tries each child until one succeeds

**Use Cases:**

- Fallback strategies
- Multiple alternative approaches
- Error recovery patterns

---

#### 3. `:parallel`

Executes all children concurrently using futures.

```clojure
[:parallel
 {:success-threshold 2}  ; Optional, defaults to all
 child-1
 child-2
 child-3]
```

**Options:**

- `:success-threshold` - Number of children that must succeed (default: all)

**Behavior:**

- Executes all children in parallel
- Returns `:success` if `success-threshold` met
- Returns `:failure` if too many fail
- Returns `:running` otherwise

**Use Cases:**

- Concurrent processing
- Batch operations with partial success
- Parallel API calls

**Location:** `behavior-tree-v2/src/ai/obney/grain/behavior_tree_v2/core/nodes.clj:56-73`

---

#### 4. `:condition`

Evaluates a predicate function.

```clojure
[:condition
 {:path   [:user :age]
  :schema :int}
 predicate-fn]
```

**Options:**

- `:path` - Path in memory to validate (optional)
- `:schema` - Malli schema to validate against (optional)
- Any custom opts passed to the condition function

**Behavior:**

- Calls `predicate-fn` with context
- Returns `:success` if predicate returns truthy
- Returns `:failure` if predicate returns falsy

**Built-in Condition Functions:**

##### `st-memory-has-value?`

```clojure
[:condition
 {:path   [:result]
  :schema :int}
 st-memory-has-value?]
```

Validates short-term memory path against schema.

##### `lt-memory-has-value?`

```clojure
[:condition
 {:path   [:conversation]
  :schema [:vector :map]}
 lt-memory-has-value?]
```

Validates long-term memory path against schema.

**Use Cases:**

- Input validation
- Pre-conditions
- State checking

---

#### 5. `:action`

Executes a side-effecting function.

```clojure
[:action
 {:id         :my-action
  :custom-opt "value"}
 action-fn]
```

**Options:**

- `:id` - Action identifier (optional)
- Any custom opts passed to the action function

**Behavior:**

- Calls `action-fn` with context
- Returns whatever the function returns (`:success`, `:failure`, `:running`)

**Function Signature:**

```clojure
(defn action-fn [{:keys [st-memory lt-memory event-store opts]}]
  ;; Do work
  bt/success)
```

**Use Cases:**

- State mutations
- API calls
- Event emission
- Side effects

---

### Built-in Helper Functions

#### Context Access

The `context` map passed to condition and action functions contains:

```clojure
{:st-memory   atom          ; Short-term memory (atom)
 :lt-memory   lt-memory-obj ; Long-term memory (protocol)
 :event-store es          ; Event store instance
 :opts        {}                 ; Node-specific options
 ;; ... custom keys
 }
```

#### Return Values

```clojure
bt/success  ; or :success
bt/failure  ; or :failure
bt/running  ; or :running
```

---

## DSPy Integration

**Location:** `lib-sources/grain/components/behavior-tree-v2-dspy-extensions/`

### DSPy Action Node

#### `dspy`

Execute DSPy signatures (LLM operations) within behavior trees.

```clojure
[:action
 {:id        :my-llm-call
  :signature #'MySignature
  :operation :chain-of-thought}
 dspy]
```

**Options:**

- `:id` - Node identifier (required for event tracking)
- `:signature` - DSPy signature var (created with `defsignature`)
- `:operation` - DSPy operation type

**Operation Types:**

#### 1. `:predict`

Basic LLM prediction without reasoning trace.

```clojure
[:action
 {:id        :generate-answer
  :signature #'QA
  :operation :predict}
 dspy]
```

**Emits Event:** `:grain.agent/predicted`

```clojure
{:node-id :generate-answer
 :inputs  {:question "What is 2+2?"}
 :outputs {:answer "4"}}
```

#### 2. `:chain-of-thought`

LLM prediction with step-by-step reasoning.

```clojure
[:action
 {:id        :reason-about-problem
  :signature #'ProblemSolver
  :operation :chain-of-thought}
 dspy]
```

**Emits Event:** `:grain.agent/reasoned`

```clojure
{:node-id   :reason-about-problem
 :inputs    {:problem "Complex problem"}
 :outputs   {:solution "Solution"}
 :reasoning "Step 1: ... Step 2: ..."}
```

**Behavior:**

- Reads inputs from short-term memory (and long-term if available)
- Calls DSPy signature with specified operation
- Writes outputs to short-term memory
- Emits events to event store
- Returns `:success` on completion, `:failure` on error

**Location:** `behavior-tree-v2-dspy-extensions/src/ai/obney/grain/behavior_tree_v2_dspy_extensions/core.clj:59-95`

---

### DSPy Signature Definition

**Location:** `lib-sources/grain/components/clj-dspy/`

#### `defsignature`

Define an LLM signature with inputs and outputs.

```clojure
(defsignature QA
              "Answer questions clearly and concisely"
              {:inputs  {:question ::question}
               :outputs {:answer ::answer}})
```

**Parameters:**

- Name - Signature identifier
- Docstring - Instructions for the LLM
- Map with:
    - `:inputs` - Map of input-key to schema
    - `:outputs` - Map of output-key to schema

**Example with Multiple Inputs/Outputs:**

```clojure
(defsignature Summarize
              "Given context and a question, provide a detailed summary"
              {:inputs  {:context    ::context
                         :question   ::question
                         :max_length ::max-length}
               :outputs {:summary    ::summary
                         :confidence ::confidence}})
```

**Usage in Trees:**

```clojure
[:action
 {:id        :summarize
  :signature #'Summarize
  :operation :chain-of-thought}
 dspy]
```

The signature automatically:

- Validates input schemas from memory
- Converts between Clojure/Python types
- Handles nested data structures
- Stores outputs back to memory

---

## Event Store Operations

**Location:** `lib-sources/grain/components/event-store-v2/`

### Core Functions

#### `start`

Initialize an event store.

```clojure
(es/start {:conn {:type :in-memory}})
;; or
(es/start {:conn {:type       :postgres
                  :datasource pg-datasource}})
```

**Parameters:**

- `:conn` - Connection config
    - `:type` - `:in-memory` or `:postgres`
    - `:datasource` - JDBC datasource (for postgres)
- `:event-pubsub` - Optional pubsub instance for event publishing

**Returns:** Event store instance

---

#### `stop`

Shutdown an event store.

```clojure
(es/stop event-store)
```

---

#### `->event`

Create an event map.

```clojure
(es/->event {:type :user/registered
             :body {:user-id "123" :email "user@example.com"}
             :tags #{[:user "123"]}})
```

**Parameters:**

- `:type` - Event type keyword (required)
- `:body` - Event data map (required)
- `:tags` - Set of `[entity-type entity-id]` tuples for indexing (optional)

**Returns:** Event map with auto-generated:

- `:event/id` - UUID v7 (time-ordered)
- `:event/timestamp` - ISO-8601 timestamp
- `:event/type` - From `:type` param
- `:event/tags` - From `:tags` param
- Plus all keys from `:body`

---

#### `append`

Write events to the store.

```clojure
;; Single event
(es/append event-store
           {:events [(es/->event {:type :counter/incremented
                                  :body {:counter-id "c1"}})]})

;; Multiple events (atomic transaction)
(es/append event-store
           {:events      [(es/->event {:type :user/registered :body {:user-id "u1"}})
                          (es/->event {:type :welcome-email/sent :body {:user-id "u1"}})]
            :tx-metadata {:batch-id "welcome-flow"}})

;; With Compare-And-Swap (CAS)
(es/append event-store
           {:events [new-event]
            :cas    {:tags         #{[:user "u1"]}
                     :types        #{:user/registered}
                     :predicate-fn (fn [events]
                                     (< (count events) 1))}})
```

**Parameters:**

- `:events` - Vector of event maps (required)
- `:tx-metadata` - Metadata for the transaction (optional)
- `:cas` - Compare-and-swap options (optional)
    - `:tags` - Filter events by tags
    - `:types` - Filter events by types
    - `:as-of` - Filter events before UUID v7 timestamp
    - `:after` - Filter events after UUID v7 timestamp
    - `:predicate-fn` - Function `[events] -> boolean` to validate before writing

**Returns:** `nil` on success, anomaly map on error

**Note:** CAS is useful for optimistic concurrency control - the predicate can check that expected state hasn't changed
before writing.

---

#### `read`

Stream events from the store.

```clojure
;; Read all events
(es/read event-store {})

;; Filter by type
(es/read event-store {:types #{:user/registered :user/updated}})

;; Filter by tags
(es/read event-store {:tags #{[:user "u1"]}})

;; Filter by time
(es/read event-store {:as-of event-id})  ; Events before or at this ID
(es/read event-store {:after event-id})  ; Events after this ID

;; Combine filters
(es/read event-store {:types #{:counter/incremented}
                      :tags  #{[:counter "c1"]}
                      :after last-seen-id})
```

**Parameters:**

- `:types` - Set of event types to filter (optional)
- `:tags` - Set of `[entity-type entity-id]` tuples to filter (optional)
- `:as-of` - UUID v7 event ID to read up to (optional)
- `:after` - UUID v7 event ID to read from (optional)

**Returns:** Reducible (IReduceInit) event stream

**Cannot combine:** `:as-of` and `:after` in the same query

**Usage Examples:**

```clojure
;; Direct reduction
(reduce (fn [acc event] ...) init (es/read event-store {}))

;; Collect with limit
(into [] (take 100) (es/read event-store {:types #{:my/event}}))

;; Transducer pipeline
(transduce
  (comp
    (filter #(= (:status %) "active"))
    (map :user-id))
  conj
  #{}
  (es/read event-store {:types #{:user/registered}}))

;; Build read model
(defn read-model-fn [initial events]
  (reduce
    (fn [state event]
      (case (:event/type event)
        :counter/incremented (update state :count inc)
        :counter/decremented (update state :count dec)
        state))
    initial
    events))

(read-model-fn {:count 0} (es/read event-store {:tags #{[:counter "c1"]}}))
```

**Memory Efficiency:** The reducible stream doesn't load all events into memory - it streams them efficiently.

---

### Event Store Protocol

**Location:** `event-store-v2/src/ai/obney/grain/event_store_v2/interface/protocol.cljc:6-59`

```clojure
(defprotocol EventStore
  (start [this])
  (stop [this])
  (append [this args])
  (read [this args]))
```

**Implementations:**

- In-memory (default)
- PostgreSQL (`lib-sources/grain/components/event-store-postgres-v2/`)

---

## Command & Query Processing

**Location:** `lib-sources/grain/components/command-processor/` and `query-processor/`

### Command Processing

#### `process-command`

Execute a command in the system.

```clojure
(cp/process-command
  {:command     {:command/name      :example/create-counter
                 :command/id        (random-uuid)
                 :command/timestamp (time/now)
                 :name              "Counter A"}
   :event-store event-store
   ;; ... other context
   })
```

**Context Keys:**

- `:command` - Command map with:
    - `:command/name` - Command type keyword
    - `:command/id` - UUID
    - `:command/timestamp` - Timestamp
    - Plus command-specific data
- `:event-store` - Event store instance
- Custom keys for command handlers

**Returns:** Result map or anomaly

**Command Flow:**

1. Validate command structure
2. Route to appropriate handler based on `:command/name`
3. Handler emits events via event store
4. Return result

---

### Query Processing

#### `process-query`

Execute a query in the system.

```clojure
(qp/process-query
  {:query       {:query/name      :example/counters
                 :query/id        (random-uuid)
                 :query/timestamp (time/now)}
   :event-store event-store
   ;; ... other context
   })
```

**Context Keys:**

- `:query` - Query map with:
    - `:query/name` - Query type keyword
    - `:query/id` - UUID
    - `:query/timestamp` - Timestamp
    - Plus query-specific parameters
- `:event-store` - Event store instance
- Custom keys for query handlers

**Returns:** Map with `:query/result` or anomaly

**Query Flow:**

1. Validate query structure
2. Route to appropriate handler based on `:query/name`
3. Handler reads from event store or projections
4. Return result in `:query/result`

---

### HTTP Request Handlers

**Location:** `lib-sources/grain/components/command-request-handler/` and `query-request-handler/`

#### Command HTTP Handler

```clojure
(require '[ai.obney.grain.command-request-handler.interface :as crh])

(crh/routes {:process-command-fn #(cp/process-command %)
             ;; ... context
             })
```

Creates HTTP route: `POST /command`

#### Query HTTP Handler

```clojure
(require '[ai.obney.grain.query-request-handler.interface :as qrh])

(qrh/routes {:process-query-fn #(qp/process-query %)
             ;; ... context
             })
```

Creates HTTP route: `POST /query`

Both handlers accept Transit+JSON payloads.

---

## Utility Components

### Time

**Location:** `lib-sources/grain/components/time/`

```clojure
(require '[ai.obney.grain.time.interface :as time])

;; Get current time as offset-date-time
(time/now)
;; => #<java.time.OffsetDateTime 2025-01-15T10:30:00Z>

;; Parse from string
(time/now-from-str "2025-01-15T10:30:00Z")

;; Create from epoch milliseconds
(time/now-from-ms 1705318200000)
```

Uses `tick.core` library. Returns `java.time.OffsetDateTime` instances.

---

### Anomalies

**Location:** `lib-sources/grain/components/anomalies/`

```clojure
(require '[ai.obney.grain.anomalies.interface :as anom])
(require '[cognitect.anomalies :as cog-anom])

;; Check if value is an anomaly
(anom/anomaly? result)
;; => nil or the anomaly map

;; Create anomalies
{::cog-anom/category ::cog-anom/incorrect
 ::cog-anom/message  "Invalid input"
 :details            {...}}
```

**Anomaly Categories:**

- `::cog-anom/incorrect` - Invalid input
- `::cog-anom/forbidden` - Authorization failure
- `::cog-anom/unsupported` - Feature not supported
- `::cog-anom/not-found` - Resource not found
- `::cog-anom/conflict` - State conflict
- `::cog-anom/fault` - System error
- `::cog-anom/busy` - System busy
- `::cog-anom/interrupted` - Operation interrupted
- `::cog-anom/unavailable` - Service unavailable

---

### Core Async Thread Pool

**Location:** `lib-sources/grain/components/core-async-thread-pool/`

```clojure
(require '[ai.obney.grain.core-async-thread-pool.interface :as pool])
(require '[clojure.core.async :as async])

(def work-chan (async/chan 100))

(def thread-pool
  (pool/start
    {:thread-count 4
     :execution-fn (fn [job] (process job))
     :error-fn     (fn [e] (log/error e))
     :in-chan      work-chan}))

;; Submit work
(async/>!! work-chan {:task "process-this"})

;; Shutdown
(pool/stop thread-pool)
```

**Config:**

- `:thread-count` - Number of worker threads
- `:execution-fn` - Function to process each job
- `:error-fn` - Function to handle exceptions
- `:in-chan` - Core.async channel for work queue

---

### PubSub

**Location:** `lib-sources/grain/components/pubsub/`

```clojure
(require '[ai.obney.grain.pubsub.interface :as pubsub])

;; Start
(def ps (pubsub/start {}))

;; Subscribe
(pubsub/sub ps
            {:topic   :user-events
             :handler (fn [msg] (println "Received:" msg))})

;; Publish
(pubsub/pub ps
            {:topic   :user-events
             :message {:event :user-registered :user-id "u1"}})

;; Stop
(pubsub/stop ps)
```

**Protocol:**

```clojure
(defprotocol PubSub
  (start [this])
  (stop [this])
  (pub [this args])    ; args: {:topic :message}
  (sub [this args]))   ; args: {:topic :handler}
```

---

### Periodic Task

**Location:** `lib-sources/grain/components/periodic-task/`

```clojure
(require '[ai.obney.grain.periodic-task.interface :as pt])

(def task
  (pt/start
    {:interval-ms 60000  ; Run every 60 seconds
     :task-fn     (fn []
                    (println "Running periodic task")
                    (do-work))}))

;; Stop
(pt/stop task)
```

---

### Todo Processor

**Location:** `lib-sources/grain/components/todo-processor/`

```clojure
(require '[ai.obney.grain.todo-processor.interface :as todo])

(def processor (todo/start config))

;; Stop
(todo/stop processor)
```

Note: Implementation details not documented in interface.

---

### Web Server

**Location:** `lib-sources/grain/components/webserver/`

```clojure
(require '[ai.obney.grain.webserver.interface :as web])

(def server
  (web/start
    {:http/routes #{route-1 route-2}
     :http/port   8080
     :http/join?  false}))

;; Stop
(web/stop server)
```

**Config:**

- `:http/routes` - Set of Pedestal routes
- `:http/port` - Port number
- `:http/join?` - Block thread until shutdown (default: false)

Uses Pedestal web framework.

---

## Schema & Validation

**Location:** `lib-sources/grain/components/schema-util/`

### Schema Definition

#### `defschemas`

Register Malli schemas globally.

```clojure
(require '[ai.obney.grain.schema-util.interface :refer [defschemas]])

(defschemas my-schemas
            {::user-id   :string
             ::email     [:string {:min 1}]
             ::age       [:int {:min 0 :max 150}]

             ::user      [:map
                          [:user-id ::user-id]
                          [:email ::email]
                          [:age {:optional true} ::age]]

             ::user-list [:vector ::user]})
```

**Features:**

- Schemas are registered in global Malli registry
- Can reference other schemas
- Supports all Malli schema types
- Includes time schemas from `malli.experimental.time`

**Built-in Schemas:**

- `::channel` - Core.async channel validation (Clojure only)
- All standard Malli schemas: `:string`, `:int`, `:double`, `:boolean`, `:keyword`, `:uuid`, etc.
- Time schemas: `:time/instant`, `:time/date`, `:time/offset-date-time`, etc.

---

### Common Malli Patterns

```clojure
;; Basic types
:string
:int
:double
:boolean
:keyword
:uuid
:symbol
:nil
:any

;; With constraints
[:string {:min 1 :max 100}]
[:int {:min 0}]
[:double {:min 0.0 :max 1.0}]

;; Collections
[:vector :int]
[:set :string]
[:map [:key :value-schema]]
[:tuple :int :string :boolean]

;; Optional and nullable
[:map
 [:required-field :string]
 [:optional-field {:optional true} :string]
 [:nullable-field [:maybe :string]]]

;; Enums
[:enum :red :green :blue]
[:enum "draft" "published" "archived"]

;; Or/And
[:or :string :int]
[:and :int [:fn #(even? %)]]

;; Custom validators
[:fn #(pos? %)]
[:fn {:error/message "Must be positive"} #(pos? %)]

;; References
{::positive-int [:and :int [:fn #(pos? %)]]
 ::user         [:map
                 [:age ::positive-int]]}

;; Descriptions (used by DSPy)
[:string {:desc "User's email address"}]
[:vector {:desc "List of user IDs"} ::user-id]
```

---

## Event Model

**Location:** `lib-sources/grain/components/event-model/`

Standard event structure:

```clojure
{:event/id        #uuid "..."           ; UUID v7 (time-ordered)
 :event/timestamp #inst "..."     ; ISO-8601 timestamp
 :event/type      :domain/event-name   ; Event type keyword
 :event/tags      #{[:entity "id"]}    ; Index tags
 ;; ... event body fields
 }
```

**Event Naming Convention:**

- Use past tense: `:user/registered`, `:counter/incremented`
- Namespace by domain: `:user/*`, `:order/*`, `:payment/*`
- Grain framework events: `:grain.agent/predicted`, `:grain.agent/reasoned`

**Tags for Indexing:**

```clojure
;; Single entity
:tags #{[:user "user-123"]}

;; Multiple entities
:tags #{[:user "user-123"] [:order "order-456"]}

;; No tags (global events)
:tags #{}
```

Tags enable efficient querying by entity type and ID.

---

## Summary

### Most Common Operations

**Behavior Trees:**

```clojure
;; Build and run
(def bt (bt/build [:sequence ...] {:st-memory {...}}))
(bt/run bt)

;; Nodes: :sequence, :fallback, :parallel, :condition, :action
```

**Event Store:**

```clojure
;; Lifecycle
(def es (es/start {:conn {:type :in-memory}}))

;; Write
(es/append es {:events [(es/->event {:type :my/event :body {...}})]})

;; Read
(into [] (es/read es {:types #{:my/event}}))

;; Cleanup
(es/stop es)
```

**DSPy:**

```clojure
;; Define signature
(defsignature QA "Answer questions"
              {:inputs  {:question ::question}
               :outputs {:answer ::answer}})

;; Use in tree
[:action {:id :qa :signature #'QA :operation :chain-of-thought} dspy]
```

**CQRS:**

```clojure
;; Commands (write)
(cp/process-command {:command {:command/name :create :command/id ...} ...})

;; Queries (read)
(qp/process-query {:query {:query/name :list :query/id ...} ...})
```

### Extension Points

**Custom Behavior Tree Nodes:**

```clojure
(defmethod btp/tick :my-node [{:keys [children]} context]
  ;; Custom logic
  bt/success)

(defmethod btp/build :my-node [node-type args]
  {:type     node-type
   :children (build-children args)})
```

**Custom Event Store:**

```clojure
(defmethod p/start-event-store :my-store [config]
  (reify p/EventStore
    (start [this] ...)
    (stop [this] ...)
    (append [this args] ...)
    (read [this args] ...)))
```

**Custom Command/Query Handlers:**

Register handlers using multimethods based on `:command/name` or `:query/name`.

---

## Quick Reference

| Category           | Component                                                 | Key Functions                                                                         |
|--------------------|-----------------------------------------------------------|---------------------------------------------------------------------------------------|
| **Behavior Trees** | behavior-tree-v2                                          | `build`, `run`, nodes: `:sequence`, `:fallback`, `:parallel`, `:condition`, `:action` |
| **DSPy**           | clj-dspy, behavior-tree-v2-dspy-extensions                | `defsignature`, `dspy` action, operations: `:predict`, `:chain-of-thought`            |
| **Events**         | event-store-v2                                            | `start`, `stop`, `append`, `read`, `->event`                                          |
| **CQRS**           | command-processor, query-processor                        | `process-command`, `process-query`                                                    |
| **HTTP**           | command-request-handler, query-request-handler, webserver | `routes`, `start`, `stop`                                                             |
| **Async**          | core-async-thread-pool                                    | `start`, `stop`                                                                       |
| **Messaging**      | pubsub                                                    | `start`, `stop`, `pub`, `sub`                                                         |
| **Scheduling**     | periodic-task                                             | `start`, `stop`                                                                       |
| **Schema**         | schema-util                                               | `defschemas`                                                                          |
| **Utilities**      | time, anomalies                                           | `now`, `anomaly?`                                                                     |

---

## Additional Resources

- **Framework README:** `lib-sources/grain/readme.md`
- **Demo Application:** `lib-sources/macroexpand-2-demo/README.md`
- **Example Code:** `lib-sources/grain/development/src/example_app_demo.clj`
- **Component Interfaces:** `lib-sources/grain/components/*/src/ai/obney/grain/*/interface.*`
- **Batch Processing Guide:** `BATCH_PROCESSING_FEATURES.md`z
