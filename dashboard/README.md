# PantauHTTP Dashboard

A zero-dependency web dashboard for watching an app's HTTP traffic live from any
browser on your local network. The PantauHTTP library pushes each captured
transaction here; the page updates in real time via Server-Sent Events.

## Run

```bash
node Dashboard/server.js          # Node 18+, no npm install needed
PORT=8888 node Dashboard/server.js
```

On startup the server prints the URLs to use:

```
PantauHTTP Dashboard
  Browser UI:  http://localhost:9435
  Set dashboardURL to: http://192.168.1.20:9435   (simulator can use http://127.0.0.1:9435)
```

Then in your app:

```swift
var configuration = PantauHTTP.Configuration()
configuration.dashboardURL = URL(string: "http://192.168.1.20:9435") // Mac's LAN IP
PantauHTTP.start(with: configuration)
```

See the root README's **Web dashboard** section for the Info.plist keys a real
device needs (`NSAllowsLocalNetworking`, `NSLocalNetworkUsageDescription`).

## Features

- **Multi-device**: one server collects traffic from many phones/simulators at
  once. A sidebar lists every device (name, model, OS, app, live request count,
  `SIM` tag); click one to see just its traffic, or "All devices" for the merged
  view. Devices idle for over 2 minutes are dimmed.
- Live list of transactions (method, URL, status, duration, size) — newest first
- Detail pane: Overview / Request / Response with pretty-printed JSON bodies,
  image previews, and truncation notes
- Text search + status filter chips (All / 2xx / 3xx / 4xx / 5xx / Failed)
- Copy any request as a cURL command
- HAR 1.2 download — one transaction, one device's log, or everything
- **Clear** wipes the selected device's log (or everything when All is selected)
- Deep links: `http://<mac>:9435/#<transaction-id>` opens that transaction on its device
- Keeps the last 1000 transactions per device in memory (the device itself keeps 200 by default)

Each device is identified automatically (identifier-for-vendor, hardware model,
OS, app name/version) and named after its hardware spec — `iPhone17,3` shows as
**iPhone 16**. Set `configuration.dashboardDeviceName = "QA Phone 3"` to use a
custom name instead.

## API

| Endpoint | Method | Description |
|---|---|---|
| `/api/ingest` | POST | Upsert one transaction (JSON, keyed by `id`, carries `device` identity); used by the library |
| `/api/devices` | GET | Known devices with `lastSeen` and `txCount` |
| `/api/transactions` | GET | All transactions (`?device=<id>` filters to one device) |
| `/api/clear` | POST | Clear everything, or one device's log with `?device=<id>` |
| `/api/events` | GET | SSE stream: `init` (`{devices, transactions}` on connect), `upsert`, `clear` (`{deviceId}` for per-device) |

Nothing is persisted — restarting the server starts with an empty log.
