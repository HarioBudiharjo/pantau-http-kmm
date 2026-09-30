'use strict';

// ---------- State ----------

const txs = new Map();
const devices = new Map();
let selectedId = null;
let selectedDeviceId = null; // null = all devices
let query = '';
let statusFilter = 'all';
let activeTab = 'overview';

const $ = (selector) => document.querySelector(selector);
const deviceListEl = $('#device-list');
const listEl = $('#tx-list');
const emptyEl = $('#empty');
const detailPane = $('#detail-pane');
const detailTitle = $('#detail-title');
const detailContent = $('#detail-content');
const statusDot = $('#status-dot');

// ---------- SSE ----------

const events = new EventSource('/api/events');

events.addEventListener('init', (e) => {
  const data = JSON.parse(e.data);
  txs.clear();
  devices.clear();
  for (const device of data.devices) devices.set(device.id, device);
  for (const tx of data.transactions) txs.set(tx.id, tx);
  // Deep link: /#<transaction-id> opens that transaction's detail on its device.
  const linked = location.hash.slice(1);
  if (linked && txs.has(linked) && selectedId !== linked) {
    selectedId = linked;
    selectedDeviceId = txs.get(linked).deviceId ?? null;
    detailPane.classList.remove('hidden');
  }
  render();
});

events.addEventListener('upsert', (e) => {
  const tx = JSON.parse(e.data);
  txs.set(tx.id, tx);
  if (tx.device) devices.set(tx.device.id, { ...tx.device, lastSeen: Date.now() });
  render();
});

events.addEventListener('clear', (e) => {
  let data = {};
  try { data = JSON.parse(e.data); } catch { /* legacy empty payload */ }
  if (data.deviceId) {
    for (const [id, tx] of txs) {
      if (tx.deviceId === data.deviceId) txs.delete(id);
    }
  } else {
    txs.clear();
    devices.clear();
    selectedDeviceId = null;
  }
  if (selectedId && !txs.has(selectedId)) {
    selectedId = null;
    detailPane.classList.add('hidden');
  }
  render();
});

events.onopen = () => statusDot.classList.add('live');
events.onerror = () => statusDot.classList.remove('live');

// ---------- Helpers ----------

// Apple publishes no API for marketing names, so map the hardware identifier
// (device.model, e.g. "iPhone17,3") ourselves. Unknown ids fall back to the
// raw identifier, so future models degrade gracefully.
const APPLE_MODEL_NAMES = {
  // iPhone
  'iPhone8,1': 'iPhone 6s', 'iPhone8,2': 'iPhone 6s Plus', 'iPhone8,4': 'iPhone SE',
  'iPhone9,1': 'iPhone 7', 'iPhone9,3': 'iPhone 7', 'iPhone9,2': 'iPhone 7 Plus', 'iPhone9,4': 'iPhone 7 Plus',
  'iPhone10,1': 'iPhone 8', 'iPhone10,4': 'iPhone 8', 'iPhone10,2': 'iPhone 8 Plus', 'iPhone10,5': 'iPhone 8 Plus',
  'iPhone10,3': 'iPhone X', 'iPhone10,6': 'iPhone X',
  'iPhone11,2': 'iPhone XS', 'iPhone11,4': 'iPhone XS Max', 'iPhone11,6': 'iPhone XS Max', 'iPhone11,8': 'iPhone XR',
  'iPhone12,1': 'iPhone 11', 'iPhone12,3': 'iPhone 11 Pro', 'iPhone12,5': 'iPhone 11 Pro Max', 'iPhone12,8': 'iPhone SE (2nd gen)',
  'iPhone13,1': 'iPhone 12 mini', 'iPhone13,2': 'iPhone 12', 'iPhone13,3': 'iPhone 12 Pro', 'iPhone13,4': 'iPhone 12 Pro Max',
  'iPhone14,4': 'iPhone 13 mini', 'iPhone14,5': 'iPhone 13', 'iPhone14,2': 'iPhone 13 Pro', 'iPhone14,3': 'iPhone 13 Pro Max',
  'iPhone14,6': 'iPhone SE (3rd gen)',
  'iPhone14,7': 'iPhone 14', 'iPhone14,8': 'iPhone 14 Plus', 'iPhone15,2': 'iPhone 14 Pro', 'iPhone15,3': 'iPhone 14 Pro Max',
  'iPhone15,4': 'iPhone 15', 'iPhone15,5': 'iPhone 15 Plus', 'iPhone16,1': 'iPhone 15 Pro', 'iPhone16,2': 'iPhone 15 Pro Max',
  'iPhone17,3': 'iPhone 16', 'iPhone17,4': 'iPhone 16 Plus', 'iPhone17,1': 'iPhone 16 Pro', 'iPhone17,2': 'iPhone 16 Pro Max',
  'iPhone17,5': 'iPhone 16e',
  'iPhone18,1': 'iPhone 17 Pro', 'iPhone18,2': 'iPhone 17 Pro Max', 'iPhone18,3': 'iPhone 17', 'iPhone18,4': 'iPhone Air',
  // iPad (recent generations)
  'iPad7,11': 'iPad (7th gen)', 'iPad7,12': 'iPad (7th gen)',
  'iPad11,6': 'iPad (8th gen)', 'iPad11,7': 'iPad (8th gen)',
  'iPad12,1': 'iPad (9th gen)', 'iPad12,2': 'iPad (9th gen)',
  'iPad13,18': 'iPad (10th gen)', 'iPad13,19': 'iPad (10th gen)',
  'iPad11,3': 'iPad Air (3rd gen)', 'iPad11,4': 'iPad Air (3rd gen)',
  'iPad13,1': 'iPad Air (4th gen)', 'iPad13,2': 'iPad Air (4th gen)',
  'iPad13,16': 'iPad Air (5th gen)', 'iPad13,17': 'iPad Air (5th gen)',
  'iPad14,8': 'iPad Air 11" (M2)', 'iPad14,9': 'iPad Air 11" (M2)',
  'iPad14,10': 'iPad Air 13" (M2)', 'iPad14,11': 'iPad Air 13" (M2)',
  'iPad11,1': 'iPad mini (5th gen)', 'iPad11,2': 'iPad mini (5th gen)',
  'iPad14,1': 'iPad mini (6th gen)', 'iPad14,2': 'iPad mini (6th gen)',
  'iPad16,1': 'iPad mini (A17 Pro)', 'iPad16,2': 'iPad mini (A17 Pro)',
  'iPad8,9': 'iPad Pro 11" (2nd gen)', 'iPad8,10': 'iPad Pro 11" (2nd gen)',
  'iPad8,11': 'iPad Pro 12.9" (4th gen)', 'iPad8,12': 'iPad Pro 12.9" (4th gen)',
  'iPad13,4': 'iPad Pro 11" (M1)', 'iPad13,5': 'iPad Pro 11" (M1)', 'iPad13,6': 'iPad Pro 11" (M1)', 'iPad13,7': 'iPad Pro 11" (M1)',
  'iPad13,8': 'iPad Pro 12.9" (M1)', 'iPad13,9': 'iPad Pro 12.9" (M1)', 'iPad13,10': 'iPad Pro 12.9" (M1)', 'iPad13,11': 'iPad Pro 12.9" (M1)',
  'iPad14,3': 'iPad Pro 11" (M2)', 'iPad14,4': 'iPad Pro 11" (M2)',
  'iPad14,5': 'iPad Pro 12.9" (M2)', 'iPad14,6': 'iPad Pro 12.9" (M2)',
  'iPad16,3': 'iPad Pro 11" (M4)', 'iPad16,4': 'iPad Pro 11" (M4)',
  'iPad16,5': 'iPad Pro 13" (M4)', 'iPad16,6': 'iPad Pro 13" (M4)',
  // iPod
  'iPod9,1': 'iPod touch (7th gen)',
};

// iOS 16+ reports these to apps instead of the user's personal device name.
const GENERIC_NAMES = new Set(['iPhone', 'iPad', 'iPod touch']);

function deviceDisplayName(device) {
  if (device.name && !GENERIC_NAMES.has(device.name)) return device.name;
  return APPLE_MODEL_NAMES[device.model] ?? device.model ?? device.name ?? 'Device';
}

function statusBucket(tx) {
  if (tx.state === 'failed') return 'failed';
  if (tx.statusCode == null) return 'pending';
  return `${Math.floor(tx.statusCode / 100)}xx`;
}

function statusLabel(tx) {
  if (tx.state === 'failed') return '!!!';
  if (tx.statusCode == null) return '…';
  return String(tx.statusCode);
}

function matches(tx) {
  if (selectedDeviceId && tx.deviceId !== selectedDeviceId) return false;
  if (statusFilter !== 'all' && statusBucket(tx) !== statusFilter) return false;
  if (!query) return true;
  const haystack = `${tx.method} ${tx.url ?? ''} ${tx.statusCode ?? ''}`.toLowerCase();
  return haystack.includes(query);
}

function formatBytes(n) {
  if (n == null) return '—';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(2)} MB`;
}

function formatTime(ms) {
  return ms == null ? '—' : new Date(ms).toLocaleTimeString('en-GB');
}

function formatDuration(ms) {
  if (ms == null) return '—';
  return ms < 1000 ? `${Math.round(ms)} ms` : `${(ms / 1000).toFixed(2)} s`;
}

function base64ToBytes(b64) {
  const raw = atob(b64);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

function decodeUTF8(bytes) {
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    return null;
  }
}

function contentType(headers) {
  const key = Object.keys(headers).find((k) => k.toLowerCase() === 'content-type');
  return key ? headers[key] : null;
}

function escapeHTML(s) {
  return s.replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

function toast(message) {
  let el = $('.toast');
  if (!el) {
    el = document.createElement('div');
    el.className = 'toast';
    document.body.appendChild(el);
  }
  el.textContent = message;
  el.classList.add('show');
  setTimeout(() => el.classList.remove('show'), 1800);
}

// ---------- Rendering ----------

const STALE_DEVICE_MS = 2 * 60 * 1000;

function renderDevices() {
  if (selectedDeviceId && !devices.has(selectedDeviceId)) selectedDeviceId = null;

  const counts = new Map();
  for (const tx of txs.values()) counts.set(tx.deviceId, (counts.get(tx.deviceId) ?? 0) + 1);

  const allLi = document.createElement('li');
  allLi.classList.toggle('selected', selectedDeviceId === null);
  allLi.innerHTML = `
    <span class="device-name">All devices</span>
    <span class="device-count">${txs.size}</span>
  `;
  allLi.addEventListener('click', () => selectDevice(null));

  const rows = [...devices.values()].map((device) => {
    const li = document.createElement('li');
    li.dataset.id = device.id;
    li.classList.toggle('selected', selectedDeviceId === device.id);
    if (device.lastSeen && Date.now() - device.lastSeen > STALE_DEVICE_MS) li.classList.add('stale');
    const osLine = [device.model, [device.systemName, device.systemVersion].filter(Boolean).join(' ')]
      .filter(Boolean).join(' · ');
    const appLine = [device.appName, device.appVersion].filter(Boolean).join(' ');
    li.innerHTML = `
      <span class="device-name">${escapeHTML(deviceDisplayName(device))}${device.isSimulator ? '<span class="sim-tag">SIM</span>' : ''}</span>
      <span class="device-count">${counts.get(device.id) ?? 0}</span>
      <span class="device-line">${escapeHTML(osLine)}</span>
      <span class="device-line">${escapeHTML(appLine)}</span>
    `;
    li.addEventListener('click', () => selectDevice(device.id));
    return li;
  });

  deviceListEl.replaceChildren(allLi, ...rows);
}

function selectDevice(id) {
  selectedDeviceId = id;
  // Keep the open detail only if its transaction belongs to the chosen device.
  if (selectedId) {
    const tx = txs.get(selectedId);
    if (id !== null && (!tx || tx.deviceId !== id)) {
      selectedId = null;
      history.replaceState(null, '', location.pathname);
      detailPane.classList.add('hidden');
    }
  }
  render();
}

function render() {
  renderDevices();

  const visible = [...txs.values()]
    .filter(matches)
    .sort((a, b) => b.requestDate - a.requestDate);

  emptyEl.style.display = visible.length ? 'none' : 'block';

  const showDeviceTag = selectedDeviceId === null && devices.size > 1;
  listEl.replaceChildren(...visible.map((tx) => {
    const li = document.createElement('li');
    li.dataset.id = tx.id;
    if (tx.id === selectedId) li.classList.add('selected');
    const deviceTag = showDeviceTag && devices.has(tx.deviceId)
      ? `<span class="device-tag">${escapeHTML(deviceDisplayName(devices.get(tx.deviceId)))}</span> · `
      : '';
    li.innerHTML = `
      <span class="method m-${escapeHTML(tx.method)}">${escapeHTML(tx.method)}</span>
      <span class="tx-url"><span class="host">${escapeHTML(tx.host ?? '')}</span>${escapeHTML(tx.path ?? '')}</span>
      <span class="tx-status s-${statusBucket(tx)}">${escapeHTML(statusLabel(tx))}</span>
      <span class="tx-meta">${deviceTag}${formatTime(tx.requestDate)} · ${formatDuration(tx.durationMs)} · ${formatBytes(tx.responseBodySize)}</span>
    `;
    li.addEventListener('click', () => select(tx.id));
    return li;
  }));

  if (selectedId && !txs.has(selectedId)) {
    selectedId = null;
    detailPane.classList.add('hidden');
  }
  if (selectedId) renderDetail();
}

function select(id) {
  selectedId = id;
  history.replaceState(null, '', `#${id}`);
  detailPane.classList.remove('hidden');
  for (const li of listEl.children) {
    li.classList.toggle('selected', li.dataset.id === id);
  }
  renderDetail();
}

function renderDetail() {
  const tx = txs.get(selectedId);
  if (!tx) return;
  detailTitle.textContent = `${tx.method} ${tx.url ?? ''}`;
  if (activeTab === 'overview') detailContent.innerHTML = renderOverview(tx);
  else if (activeTab === 'request') detailContent.innerHTML = renderPayload(tx, 'request');
  else detailContent.innerHTML = renderPayload(tx, 'response');
}

function renderOverview(tx) {
  const rows = [
    ['URL', tx.url],
    ['Method', tx.method],
    ['State', tx.state],
    ['Status', tx.statusCode],
    ['Requested at', tx.requestDate ? new Date(tx.requestDate).toLocaleString('en-GB') : null],
    ['Responded at', tx.responseDate ? new Date(tx.responseDate).toLocaleString('en-GB') : null],
    ['Duration', formatDuration(tx.durationMs)],
    ['Request size', formatBytes(tx.requestBodySize)],
    ['Response size', formatBytes(tx.responseBodySize)],
    ['Error', tx.errorDescription],
    ...tx.redirects.map((r, i) => [`Redirect ${i + 1}`, `${r.statusCode}: ${r.fromURL} → ${r.toURL}`]),
  ].filter(([, v]) => v != null && v !== '—');

  return `<table class="kv">${rows.map(([k, v]) =>
    `<tr><td>${escapeHTML(String(k))}</td><td>${escapeHTML(String(v))}</td></tr>`).join('')}</table>`;
}

function renderPayload(tx, side) {
  const headers = side === 'request' ? tx.requestHeaders : tx.responseHeaders;
  const bodyB64 = side === 'request' ? tx.requestBodyBase64 : tx.responseBodyBase64;
  const truncated = side === 'request' ? tx.isRequestBodyTruncated : tx.isResponseBodyTruncated;
  const fullSize = side === 'request' ? tx.requestBodySize : tx.responseBodySize;

  const headerRows = Object.entries(headers).sort(([a], [b]) => a.localeCompare(b));
  let html = `<div class="section-title">Headers</div>`;
  html += headerRows.length
    ? `<table class="kv">${headerRows.map(([k, v]) =>
        `<tr><td>${escapeHTML(k)}</td><td>${escapeHTML(v)}</td></tr>`).join('')}</table>`
    : `<p class="placeholder">No headers</p>`;

  html += `<div class="section-title">Body</div>`;

  if (!bodyB64) {
    html += `<p class="placeholder">Empty body</p>`;
    return html;
  }

  const bytes = base64ToBytes(bodyB64);
  if (truncated) {
    html += `<p class="note">Body truncated by size limit — showing ${formatBytes(bytes.length)} of ${formatBytes(fullSize)}.</p>`;
  }

  const ct = contentType(headers) ?? '';
  if (ct.toLowerCase().startsWith('image/')) {
    html += `<img class="body-image" src="data:${escapeHTML(ct)};base64,${bodyB64}" alt="body image preview">`;
    return html;
  }

  const text = decodeUTF8(bytes);
  if (text == null) {
    html += `<p class="placeholder">${formatBytes(bytes.length)} of binary data</p>`;
    return html;
  }

  let pretty = text;
  if (ct.toLowerCase().includes('json') || looksLikeJSON(text)) {
    try { pretty = JSON.stringify(JSON.parse(text), null, 2); } catch { /* keep raw */ }
  }
  html += `<pre class="body">${escapeHTML(pretty)}</pre>`;
  return html;
}

function looksLikeJSON(text) {
  const t = text.trimStart();
  return t.startsWith('{') || t.startsWith('[');
}

// ---------- cURL ----------

function shellQuote(s) {
  return `'${s.replace(/'/g, `'\\''`)}'`;
}

function buildCurl(tx) {
  const parts = ['curl', '-X', tx.method, shellQuote(tx.url ?? '')];
  for (const [k, v] of Object.entries(tx.requestHeaders).sort(([a], [b]) => a.localeCompare(b))) {
    parts.push('-H', shellQuote(`${k}: ${v}`));
  }
  if (tx.requestBodyBase64) {
    const text = decodeUTF8(base64ToBytes(tx.requestBodyBase64));
    parts.push('--data-binary', text != null
      ? shellQuote(text)
      : shellQuote(`<${tx.requestBodySize} bytes of binary data>`));
  }
  return parts.join(' ');
}

// ---------- HAR ----------

function harEntry(tx) {
  const requestText = tx.requestBodyBase64 ? decodeUTF8(base64ToBytes(tx.requestBodyBase64)) : null;
  const responseText = tx.responseBodyBase64 ? decodeUTF8(base64ToBytes(tx.responseBodyBase64)) : null;
  const toHeaders = (headers) => Object.entries(headers).map(([name, value]) => ({ name, value }));
  const time = tx.durationMs ?? 0;

  let queryString = [];
  try {
    queryString = [...new URL(tx.url).searchParams].map(([name, value]) => ({ name, value }));
  } catch { /* no valid URL */ }

  return {
    startedDateTime: new Date(tx.requestDate).toISOString(),
    time,
    request: {
      method: tx.method,
      url: tx.url ?? '',
      httpVersion: 'HTTP/1.1',
      headers: toHeaders(tx.requestHeaders),
      queryString,
      cookies: [],
      headersSize: -1,
      bodySize: Number(tx.requestBodySize ?? 0),
      ...(requestText != null && {
        postData: {
          mimeType: contentType(tx.requestHeaders) ?? 'application/octet-stream',
          text: requestText,
        },
      }),
    },
    response: {
      status: tx.statusCode ?? 0,
      statusText: '',
      httpVersion: 'HTTP/1.1',
      headers: toHeaders(tx.responseHeaders),
      cookies: [],
      content: {
        size: Number(tx.responseBodySize ?? 0),
        mimeType: contentType(tx.responseHeaders) ?? '',
        ...(responseText != null && { text: responseText }),
      },
      redirectURL: '',
      headersSize: -1,
      bodySize: Number(tx.responseBodySize ?? 0),
    },
    cache: {},
    timings: { send: 0, wait: time, receive: 0 },
  };
}

function buildHar(list) {
  return {
    log: {
      version: '1.2',
      creator: { name: 'PantauHTTP Dashboard', version: '1.0' },
      entries: list.filter((tx) => tx.state !== 'inProgress').map(harEntry),
    },
  };
}

function downloadHar(list, filename) {
  const blob = new Blob([JSON.stringify(buildHar(list), null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

// ---------- Wiring ----------

$('#search').addEventListener('input', (e) => {
  query = e.target.value.trim().toLowerCase();
  render();
});

$('#filters').addEventListener('click', (e) => {
  const chip = e.target.closest('.chip');
  if (!chip) return;
  statusFilter = chip.dataset.filter;
  for (const c of document.querySelectorAll('.chip')) c.classList.toggle('active', c === chip);
  render();
});

document.querySelector('.tabs').addEventListener('click', (e) => {
  const tab = e.target.closest('.tab');
  if (!tab) return;
  activeTab = tab.dataset.tab;
  for (const t of document.querySelectorAll('.tab')) t.classList.toggle('active', t === tab);
  renderDetail();
});

$('#clear').addEventListener('click', () => {
  const target = selectedDeviceId ? `/api/clear?device=${encodeURIComponent(selectedDeviceId)}` : '/api/clear';
  fetch(target, { method: 'POST' });
});

$('#download-har').addEventListener('click', () => {
  const list = [...txs.values()].filter((tx) => !selectedDeviceId || tx.deviceId === selectedDeviceId);
  downloadHar(list, 'pantauhttp.har');
});

$('#download-har-one').addEventListener('click', () => {
  const tx = txs.get(selectedId);
  if (tx) downloadHar([tx], `transaction-${tx.id}.har`);
});

$('#copy-curl').addEventListener('click', async () => {
  const tx = txs.get(selectedId);
  if (!tx) return;
  try {
    await navigator.clipboard.writeText(buildCurl(tx));
    toast('cURL copied to clipboard');
  } catch {
    toast('Copy failed — clipboard needs HTTPS or localhost');
  }
});

$('#close-detail').addEventListener('click', () => {
  selectedId = null;
  history.replaceState(null, '', location.pathname);
  detailPane.classList.add('hidden');
  render();
});

// Refresh the sidebar's stale-device dimming even when no traffic arrives.
setInterval(renderDevices, 30_000);
