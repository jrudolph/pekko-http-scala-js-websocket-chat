# Akka HTTP / Scala.js / Websocket Chat App

A simple chat app that uses akka-http backend and a scala.js frontend to implement a simple
websocket based chat application.

To run:

```
sbt

> project backend
> reStart
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

## CLI

The `cli` project contains a command line client for the chat to demonstrate the Websocket client and
how to deal with console input in a streaming way.

![CLI Screencast](https://github.com/jrudolph/akka-http-scala-js-websocket-chat/raw/master/docs/cli-screencast.gif)

It runs best directly from a terminal.

Start the server as explained above. Then, to build a fat jar use

```
sbt

> project cli
> assembly
```

Run

```
java -jar cli/target/scala-2.11/cli-assembly-0.1-SNAPSHOT.jar
```

or 

```
./chat
```

Here's another screencast that shows live tab completion in action:

![CLI completion screencast](https://github.com/jrudolph/akka-http-scala-js-websocket-chat/raw/master/docs/cli-completion.gif)

## Known issues

### The "frontend"

There isn't more than absolutely necessary there right now.
