# Todo MCP demo

One board, two ways to drive it.

`/` is the page. It talks to the same server over HTTP, and stays in sync through `GET /todos/events` (server-sent events).

`/host` frames the MCP App (`ui://todo/app`) in the browser. The frame and the page share the board.

In dsh, the Plugins page points the host at this server. The checked-in row is an HTTP server named `todos` at `http://127.0.0.1:8080/mcp`. A stdio row is a separate process and a separate board.

`resources/subscribe` is experimental. The view hears board changes through it. The page does not open its own subscription from inside the frame.

A tool call from the view asks first. The question is the tool summary and the server name. The answers are Allow once (focused), Allow for this session, and Don't allow.

## Run

You need JDK 25, sbt (this build pins 2.1.0-M3), and dsh. The server and the view resolve snapshot builds, so set `COURSIER_TTL=0s`.

Start the server from this repo:

```sh
COURSIER_TTL=0s sbt server/run
```

It listens on `http://127.0.0.1:8080`. Open `/` for the page and `/host` for the browser frame.

The dsh path needs [@early-effect/dsh-heddle-apps](https://github.com/early-effect/dsh-heddle-apps). The client bundle is not in git. From a checkout of that repo:

```sh
sbt heddlePlugin/stagePlugin
dsh plugin --profile web add "$PWD/plugin"
```

`stagePlugin` writes `plugin/lib/index.js` and `plugin/client.js`. Then, from this repo, with the server already running:

```sh
dsh web --patch dsh.patch.yml --no-open --port 8791
```

`--patch` is a launcher flag. Put it before any flag that belongs to the web app. dsh prints a URL with a token. Open that. The Plugins card shows one connected server, `todos`, at `http://127.0.0.1:8080/mcp`.

Add a todo from the page and from a tool in the dock. Both change the same board. Allow once adds the row. Don't allow replaces the question with "Not allowed."

`dsh.patch.yml` is the same JSON the Plugins page writes. It also disables the stock `ui-mcp-mgr` row.
