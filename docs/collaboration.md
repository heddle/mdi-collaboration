# MDI collaboration architecture

## Purpose and placement

Collaboration is an optional extension for small groups of known scientific
collaborators. It is a separate `io.github.heddle:mdi-collaboration` artifact,
not a package inside the main MDI jar. This prevents broker clients and other
networking dependencies from appearing in applications that do not collaborate.
The public packages retain MDI's `edu.cnu.mdi` namespace.

The core module is deliberately Swing-free and does not depend on the MDI jar.
The separate MDI UI module depends on both artifacts and reuses
`BaseMDIApplication`, `BaseView`, `ViewManager`, `MenuManager`, `Log`, and the
`prepareForShutdown()` lifecycle hook.

## Components

```text
 MDI application / CollaborationView
                 |
        CollaborationService
                 |
       CollaborationTransport
          /               \
 InMemoryTransport    RabbitMQTransport
```

- `model`: immutable collaborators, projects, types, and message envelopes.
- `codec`: strict required-field validation and version-tolerant JSON parsing.
- `transport`: a replaceable, asynchronous, Swing-free boundary.
- `event`: compact service notifications with removable listeners.
- `transport.memory`: an explicit in-JVM bus with per-client daemon executors.
- `mdi-collaboration-mdi`: the EDT adapter, minimal view, and demonstration.

Jackson is the sole production dependency. MDI already uses Jackson 2.17.0;
`jackson-datatype-jsr310` is added to encode `Instant` as an ISO-8601 value
rather than a Java implementation detail. Java 17 matches MDI's compiler target.

## Message flow and threading

```text
transport callback thread -> CollaborationService -> application listener
                                                   -> SwingUtilities.invokeLater
                                                   -> update Swing view
```

All connect, disconnect, and publish operations return `CompletableFuture` and
must not block the Swing event-dispatch thread. Service listeners execute on the
transport callback thread. A future UI adapter will marshal only UI mutations
to the EDT. The in-memory implementation uses one named daemon delivery thread
per client, isolates listener failures, rechecks state during disconnect races,
and shuts its executor down on close.

Applications own and close their `CollaborationService` from
`BaseMDIApplication.prepareForShutdown()`, calling the superclass implementation
as required by MDI.

## JSON protocol

The protocol uses a simple explicit envelope: protocol version, message and
participant UUIDs, timestamp, type token, metadata object, and optional textual
content. Arbitrary Java objects are never deserialized. Unknown JSON fields are
ignored. Unknown valid type tokens are retained, allowing an older client to
log or ignore a future type without breaking its receiver. Metadata is intended
for small JSON-compatible control data, not file bytes.

## RabbitMQ topology

The network transport uses a configurable topic exchange, defaulting
to `mdi.collaboration`. A connected client will have its own server-named,
exclusive, auto-delete phase-one queue, bound as appropriate to:

```text
user.<collaborator UUID>
project.<project UUID>
broadcast
```

Publishing uses the matching routing key. The queue policy deliberately favors
a small and understandable online demonstration; durable/offline delivery needs
an explicit identity, retention, and stale-queue policy before it is enabled.
Broker URI, virtual host, username, TLS settings, exchange name, recovery mode,
and project subscriptions live in `RabbitMqConfiguration` behind the transport
boundary. Secrets come from environment variables and are never included in
configuration diagnostics. AMQPS uses the RabbitMQ client's JVM trust and
hostname verification. Normal tests never require a broker.

### Local broker and two-process demo

For a local development broker, one option is the official container image:

```shell
docker run --rm --name mdi-rabbit -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

Install the reactor artifacts, then launch Alice and Bob in separate terminals:

```shell
cd /Users/davidheddle/mdi-collaboration
mvn clean install

export MDI_RABBITMQ_URI='amqp://guest:guest@localhost:5672/%2f'
mvn -pl mdi-collaboration-mdi exec:java \
  -Dcollaboration.transport=rabbitmq -Dcollaboration.user=Alice -Dcollaboration.peer=Bob
```

```shell
cd /Users/davidheddle/mdi-collaboration
export MDI_RABBITMQ_URI='amqp://guest:guest@localhost:5672/%2f'
mvn -pl mdi-collaboration-mdi exec:java \
  -Dcollaboration.transport=rabbitmq -Dcollaboration.user=Bob -Dcollaboration.peer=Alice
```

The `guest` example is only for a broker on localhost. Use an `amqps://` URI and
proper broker-managed credentials for remote deployments. Credentials may be
provided separately as `MDI_RABBITMQ_USERNAME` and `MDI_RABBITMQ_PASSWORD`; the
exchange can be overridden with `MDI_RABBITMQ_EXCHANGE`.

Run the opt-in live broker test with:

```shell
MDI_RABBITMQ_URI='amqp://guest:guest@localhost:5672/%2f' \
  mvn -Prabbitmq-integration verify
```

## Control plane and data plane

```text
file offer / accept / reject: CollaborationTransport (control plane)
file bytes:                  FileTransferService     (data plane)
```

No large file bytes belong in collaboration messages. `FileTransferService`,
`FileOffer`, and `TransferHandle` are planned after the broker slice. Received
filenames will be reduced to safe leaf names, remote paths will not be trusted,
and received content will never be executed automatically.

## Smallest useful vertical slice

The implemented slice creates Alice and Bob services on one explicit in-memory
bus, connects both, sends a direct chat envelope asynchronously, and observes it
through Bob's listener. This proves the domain, protocol, lifecycle, routing,
and threading boundaries without a broker. Because MDI intentionally permits
only one `BaseMDIApplication` per JVM, the in-memory two-client proof remains a
service-level test; the future RabbitMQ demo will provide the two-process GUI
proof.

## Implementation sequence

1. **Complete:** core values, codec, service/events, in-memory transport, tests.
2. **Complete:** minimal optional MDI view and EDT adapter; services close
   through the application shutdown lifecycle.
3. **Complete:** RabbitMQ client/configuration in an isolated module, opt-in
   broker integration test, automatic recovery events, and two-process demo.
4. Add file-transfer interfaces and a safe same-machine test implementation.
5. Review public APIs/Javadocs, add developer run instructions, and run the full
   MDI plus collaboration verification builds.

Reconnect policy, durable history, presence, NATS, Magic Wormhole, scientific
view sharing, collaborative editing, distributed locking, and remote execution
are intentionally outside this first slice. Reconnection should eventually be
implemented by a policy object rather than hidden infinite retries; malformed
wire data should become logged error events without terminating consumers.
