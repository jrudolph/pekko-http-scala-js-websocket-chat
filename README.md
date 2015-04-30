# Akka HTTP / Scala.js / Websocket Chat App

A simple chat app that uses akka-http backend and a scala.js frontend to implement a simple
websocket based chat application.

To run:

```
sbt

> project backend
> re-start
```

Navigate to [http://localhost:8080/](http://localhost:8080/).

You can build a fully self-contained jar using `assembly` in the backend project.

## Configuration

You can set `app.interface` and `app.port` in `application.conf` to configure where the server
should listen to.

This also works on the command line using JVM properties, e.g. using `re-start`:

```
> re-start --- -Dapp.interface=0.0.0.0 -Dapp.port=8080
```

will start the server listening on all interfaces.

## Known issues

### Handling of backpressure

The chat (actor) itself doesn't yet implement any meaningful backpressure logic.
  * On the incoming side you probably want to backpressure (rate-limit) each client itself and the total rate of messages maybe as well
  * On the ougoing side you don't want one slow chat participant to slow down the complete chat. Right now the outgoing side uses a `Source.actorRef` with an overflow strategy of fail: if a client doesn't keep up with receiving messages and the network send buffer on the chat server for that client fills up the client will be failed. (This is somewhat similar to [what Twitter does in its streaming APIs](https://dev.twitter.com/streaming/overview/connecting)). A better strategy may be to drop
messages (and leave a note for the user) until the client catches up with the action.

### Usage of stream combinators

Ideally, akka-stream would support dynamic merge/broadcast operations, so that you never need to break out of stream logic. Right now, collecting and broadcasting messages is done by the chat actor and for each user a manual stream pipeline needs to be setup.

### The "frontend"

There isn't more than absolutely necessary there right now.
