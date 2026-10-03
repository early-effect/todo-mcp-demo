# Todo MCP demo

One board, two ways to drive it.

`/` is the page. It talks to the same server over HTTP, and stays in sync through `GET /todos/events` (server-sent events).

`/host` frames the MCP App (`ui://todo/app`) in the browser. The frame and the page share the board.

In dsh, the Plugins page is how the host is pointed at this server. Add an HTTP row whose URL is `http://127.0.0.1:8080/mcp`. `dsh.patch.yml` is that same JSON, for a profile that does not open the page. A stdio row is a separate process and a separate board.

`resources/subscribe` is experimental. The view hears board changes through it. The page does not open its own subscription from inside the frame.

A tool call from the view asks first. The answers are Allow once, Allow for this session, and Don't allow.

```sh
sbt server/run
```

The server listens on `http://127.0.0.1:8080`.
