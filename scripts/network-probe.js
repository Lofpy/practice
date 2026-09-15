'use strict';
// Loopback-only diagnostics. Never disables authentication on the actual public proxy.
const net = require('node:net');
const zlib = require('node:zlib');
const assert = require('node:assert/strict');

function vi(value) {
  const bytes = [];
  do { let byte = value & 127; value >>>= 7; if (value) byte |= 128; bytes.push(byte); } while (value);
  return Buffer.from(bytes);
}
function readVi(data, offset = 0) {
  let value = 0;
  for (let i = 0; i < 5; i++) {
    if (offset + i >= data.length) return null;
    const byte = data[offset + i];
    value |= (byte & 127) << (7 * i);
    if (!(byte & 128)) return { value, next: offset + i + 1 };
  }
  throw Error('Invalid VarInt');
}
function str(value) { const bytes = Buffer.from(value); return Buffer.concat([vi(bytes.length), bytes]); }
function readStr(data, offset) { const length = readVi(data, offset); return { value: data.toString('utf8', length.next, length.next + length.value), next: length.next + length.value }; }
function handshake(protocol, port, state) {
  const number = Buffer.alloc(2); number.writeUInt16BE(port);
  return Buffer.concat([vi(protocol), str('localhost'), number, vi(state)]);
}
function connection(port, onPacket) {
  let incoming = Buffer.alloc(0), threshold = -1;
  const socket = net.createConnection({ host: '127.0.0.1', port });
  const api = {
    socket,
    compression(value) { threshold = value; },
    send(id, payload = Buffer.alloc(0)) {
      let body = Buffer.concat([vi(id), payload]);
      if (threshold >= 0) body = body.length >= threshold
        ? Buffer.concat([vi(body.length), zlib.deflateSync(body)]) : Buffer.concat([vi(0), body]);
      socket.write(Buffer.concat([vi(body.length), body]));
    }
  };
  socket.on('data', chunk => {
    incoming = Buffer.concat([incoming, chunk]);
    try {
      for (;;) {
        const length = readVi(incoming);
        if (!length || incoming.length < length.next + length.value) break;
        let body = incoming.subarray(length.next, length.next + length.value);
        incoming = incoming.subarray(length.next + length.value);
        if (threshold >= 0) { const plain = readVi(body); body = plain.value ? zlib.inflateSync(body.subarray(plain.next)) : body.subarray(plain.next); }
        const id = readVi(body);
        onPacket(id.value, body.subarray(id.next), api);
      }
    } catch (error) { socket.destroy(error); }
  });
  return api;
}

function status(port, protocol) {
  return new Promise((resolve, reject) => {
    const client = connection(port, (id, data) => {
      if (id !== 0) return;
      const result = JSON.parse(readStr(data, 0).value);
      assert.ok(result.version && result.players);
      client.socket.end();
      resolve({ protocol, advertised: result.version.name, online: result.players.online });
    });
    client.socket.on('error', reject);
    client.socket.setTimeout(10000, () => client.socket.destroy(Error('Status timeout')));
    client.socket.on('connect', () => { client.send(0, handshake(protocol, port, 1)); client.send(0); });
  });
}

function authenticationRequired(port) {
  return new Promise((resolve, reject) => {
    let completed = false;
    const timer = setTimeout(() => finish(Error('Authentication check timeout')), 10000);
    const client = connection(port, (id) => {
      if (id === 1) finish(null, { port, onlineAuthenticationRequested: true });
      if (id === 2) finish(Error('Public proxy accepted login without account authentication'));
      if (id === 0) finish(Error('Public proxy rejected the handshake before its authentication challenge'));
    });
    function finish(error, result) {
      if (completed) return;
      completed = true; clearTimeout(timer); client.socket.destroy();
      error ? reject(error) : resolve(result);
    }
    client.socket.on('error', finish);
    client.socket.on('connect', () => { client.send(0, handshake(47, port, 2)); client.send(0, str('NetAuthProbe')); });
  });
}

// Verifies login, the compass, /pvp transfer, /hub return, and legacy keepalives.
function legacyLogin(port, protocol) {
  if (![5, 47].includes(protocol)) throw Error('This login probe implements only1.7.10 and1.8.9');
  return new Promise((resolve, reject) => {
    let play = false, stage = 'lobby', joined = false, transitions = 0, compass = false, returnedCompass = false;
    const messages = [], timers = [];
    const delay = (fn, ms) => { const timer = setTimeout(fn, ms); timers.push(timer); };
    const client = connection(port, (id, data) => {
      if (!play) {
        if (id === 0) throw Error('Login rejected: ' + readStr(data, 0).value);
        if (id === 3) { client.compression(readVi(data).value); return; }
        if (id === 2) { play = true; return; }
        return;
      }
      if (id === 0) { client.send(0, data); return; }
      if (id === 0x40) throw Error('Disconnected: ' + readStr(data, 0).value);
      if (id === 0x02) messages.push(readStr(data, 0).value);
      if ((id === 0x2f && data.length >= 5 && data.readInt16BE(3) === 345)
          || (id === 0x30 && data.includes(Buffer.from([1, 89])))) {
        compass = true;
        if (stage === 'hub') returnedCompass = true;
      }
      if (id === 0x01 && !joined) {
        joined = true;
        delay(() => { stage = 'pvp'; client.send(1, str('/pvp')); }, 2200);
      }
      if (id === 0x07) transitions++;
    });
    const finish = (error, result) => { timers.forEach(clearTimeout); client.socket.destroy(); error ? reject(error) : resolve(result); };
    client.socket.on('error', error => finish(error));
    client.socket.on('connect', () => {
      client.send(0, handshake(protocol, port, 2));
      client.send(0, str('NetProbe' + protocol));
    });
    delay(() => {
      if (!joined) { finish(Error('Must join lobby')); return; }
      stage = 'hub'; client.send(1, str('/hub'));
    }, 6000);
    delay(() => {
      try {
        assert.ok(joined, 'Must receive join game');
        assert.ok(transitions >= 2, 'Must receive server-transfer respawns');
        assert.ok(compass, 'Must receive lobby compass');
        assert.ok(returnedCompass, 'Must receive the lobby compass again after /hub');
        finish(null, { protocol, joined, compass, returnedCompass, transitions, messages });
      } catch (error) { finish(error); }
    }, 9500);
  });
}

(async () => {
  const port = Number(process.argv[2] || '25565');
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw Error('Invalid port');
  for (const protocol of [5, 47, 107, 340, 754, 763, 772, 776]) console.log('STATUS_PASS', JSON.stringify(await status(port, protocol)));
  if (process.argv.includes('--auth-check')) console.log('AUTH_PASS', JSON.stringify(await authenticationRequired(port)));
  if (process.argv.includes('--legacy-login')) {
    if (port !== 25568) throw Error('Offline login probes are restricted to the isolated loopback proxy on25568');
    for (const protocol of [5,47]) console.log('LEGACY_LOGIN_PASS', JSON.stringify(await legacyLogin(port, protocol)));
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
