#!/usr/bin/env node
// PantauHTTP Dashboard — zero-dependency local-network log viewer.
//
//   node Dashboard/server.js          # default port 9435
//   PORT=8888 node Dashboard/server.js
//
// The iOS library POSTs transactions to /api/ingest; browsers watch them
// live at http://<this-mac>:9435 via Server-Sent Events.

'use strict';

const http = require('node:http');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');

const PORT = Number(process.env.PORT) || 9435;
const MAX_TRANSACTIONS_PER_DEVICE = 1000;
const MAX_INGEST_BYTES = 8 * 1024 * 1024;
const PUBLIC_DIR = path.join(__dirname, 'public');
const UNKNOWN_DEVICE = {
  id: 'unknown', name: 'Unknown device', model: '', systemName: '', systemVersion: '',
  appName: '', appVersion: '', bundleId: '', isSimulator: false,
};

// deviceId → identity + lastSeen/txCount; first-seen order.
const devices = new Map();
// deviceId → (txId → tx), insertion-ordered; upserts keep position.
const txsByDevice = new Map();
const sseClients = new Set();

function deviceList() {
  return [...devices.values()];
}

function allTransactions() {
  return [...txsByDevice.values()].flatMap((txs) => [...txs.values()]);
}

const STATIC_FILES = {
  '/': { file: 'index.html', type: 'text/html; charset=utf-8' },
  '/index.html': { file: 'index.html', type: 'text/html; charset=utf-8' },
  '/app.js': { file: 'app.js', type: 'text/javascript; charset=utf-8' },
  '/style.css': { file: 'style.css', type: 'text/css; charset=utf-8' },
};

function broadcast(event, data) {
  const frame = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
  for (const client of sseClients) client.write(frame);
}

function upsert(tx) {
  const identity = tx.device && typeof tx.device.id === 'string' && tx.device.id
    ? tx.device
    : UNKNOWN_DEVICE;
  tx.deviceId = identity.id;

  let txs = txsByDevice.get(identity.id);
  if (!txs) {
    txs = new Map();
    txsByDevice.set(identity.id, txs);
  }
  const isNew = !txs.has(tx.id);
  txs.set(tx.id, tx);
  if (isNew && txs.size > MAX_TRANSACTIONS_PER_DEVICE) {
    txs.delete(txs.keys().next().value);
  }

  devices.set(identity.id, { ...identity, lastSeen: Date.now(), txCount: txs.size });
  broadcast('upsert', tx);
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(Object.assign(new Error('payload too large'), { statusCode: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  try {
    if (req.method === 'POST' && url.pathname === '/api/ingest') {
      const body = await readBody(req, MAX_INGEST_BYTES);
      let tx;
      try {
        tx = JSON.parse(body.toString('utf8'));
      } catch {
        res.writeHead(400).end('invalid JSON');
        return;
      }
      if (!tx || typeof tx.id !== 'string' || tx.id.length === 0) {
        res.writeHead(400).end('missing id');
        return;
      }
      upsert(tx);
      res.writeHead(204).end();
      return;
    }

    if (req.method === 'GET' && url.pathname === '/api/transactions') {
      const deviceId = url.searchParams.get('device');
      const list = deviceId
        ? [...(txsByDevice.get(deviceId)?.values() ?? [])]
        : allTransactions();
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(list));
      return;
    }

    if (req.method === 'GET' && url.pathname === '/api/devices') {
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(deviceList()));
      return;
    }

    if (req.method === 'POST' && url.pathname === '/api/clear') {
      const deviceId = url.searchParams.get('device');
      if (deviceId) {
        // Clear one device's log; the device stays listed.
        txsByDevice.delete(deviceId);
        const device = devices.get(deviceId);
        if (device) devices.set(deviceId, { ...device, txCount: 0 });
        broadcast('clear', { deviceId });
      } else {
        txsByDevice.clear();
        devices.clear();
        broadcast('clear', {});
      }
      res.writeHead(204).end();
      return;
    }

    if (req.method === 'GET' && url.pathname === '/api/events') {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
      });
      res.write(`event: init\ndata: ${JSON.stringify({ devices: deviceList(), transactions: allTransactions() })}\n\n`);
      sseClients.add(res);
      req.on('close', () => sseClients.delete(res));
      return;
    }

    if (req.method === 'GET' && STATIC_FILES[url.pathname]) {
      const { file, type } = STATIC_FILES[url.pathname];
      const content = fs.readFileSync(path.join(PUBLIC_DIR, file));
      res.writeHead(200, { 'Content-Type': type });
      res.end(content);
      return;
    }

    res.writeHead(404).end('not found');
  } catch (error) {
    res.writeHead(error.statusCode || 500).end(error.message);
  }
});

// Heartbeat: keeps proxies from closing idle SSE streams and reaps dead sockets.
setInterval(() => {
  for (const client of sseClients) client.write(': ping\n\n');
}, 25_000).unref();

server.listen(PORT, '0.0.0.0', () => {
  const addresses = Object.values(os.networkInterfaces())
    .flat()
    .filter((iface) => iface && iface.family === 'IPv4' && !iface.internal)
    .map((iface) => iface.address);

  console.log('PantauHTTP Dashboard');
  console.log(`  Browser UI:  http://localhost:${PORT}`);
  if (addresses.length === 0) {
    console.log('  No LAN interface found — device pushes need Wi-Fi/Ethernet.');
  } else {
    for (const address of addresses) {
      console.log(`  Set dashboardURL to: http://${address}:${PORT}   (simulator can use http://127.0.0.1:${PORT})`);
    }
  }
});
