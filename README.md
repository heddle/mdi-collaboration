# MDI Collaboration

Optional, transport-neutral collaboration support for the
[MDI framework](https://github.com/heddle/mdi).

The repository is a multi-module build:

- `mdi-collaboration-core` contains the Swing-free model, JSON codec,
  asynchronous service API, and in-JVM transport.
- `mdi-collaboration-mdi` contains the optional MDI/Swing view, EDT adapter, and
  two-client demonstration.
- `mdi-collaboration-rabbitmq` contains the optional RabbitMQ network transport.

The core also defines the file-transfer data plane and includes a same-machine
`LocalFileTransferService` for deterministic tests. File bytes never travel
through collaboration messages or RabbitMQ.

The core has no RabbitMQ or MDI dependency. The MDI demo declares the RabbitMQ
module optional so downstream applications do not inherit it automatically.

Build with Java 17 and Maven:

```shell
mvn clean verify
```

The UI module targets the current MDI `1.2.4-SNAPSHOT`. Install the neighboring
MDI checkout once, then run the in-memory Alice/Bob demonstration:

```shell
cd ../mdi
mvn install -DskipTests
cd ../mdi-collaboration
mvn -pl mdi-collaboration-mdi -am install
mvn -pl mdi-collaboration-mdi exec:java
```

The default demo opens Alice and Bob in one JVM using the in-memory transport.
That mode also demonstrates file offer, accept/reject, and same-machine transfer
without placing bytes in the message transport.
See `docs/collaboration.md` for the two-process RabbitMQ commands.

See [docs/collaboration.md](docs/collaboration.md) for the architecture and
implementation sequence.
