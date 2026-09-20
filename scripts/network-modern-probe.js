'use strict';
// Synthetic offline diagnostic ONLY for the isolated proxy on 127.0.0.1:25569.
// Never changes public authentication or sends match/queue commands.
// Protocol 776 is Minecraft Java 26.2. Packet IDs checked against the installed
// Velocity 4.1.1 StateRegistry and ViaVersion 5.11.0 Client/ServerboundPackets26_1.
// Reference: https://github.com/PaperMC/Velocity/blob/dev/3.0.0/proxy/src/main/java/com/velocitypowered/proxy/protocol/StateRegistry.java
const net = require('node:net');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');

const HOST = '127.0.0.1';
const PORT = 25569;
const PROTOCOL = 776;
const USERNAME = 'NetProbe776';
const MAX_PACKET = 8 * 1024 * 1024;
const ROUND_TRIP = process.argv.includes('--roundtrip');

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
function str(value) {
  const bytes = Buffer.from(value, 'utf8');
  return Buffer.concat([vi(bytes.length), bytes]);
}
function readStr(data, offset = 0) {
  const length = readVi(data, offset);
  assert.ok(length && length.value >= 0 && length.next + length.value <= data.length, 'Complete string required');
  return { value: data.toString('utf8', length.next, length.next + length.value), next: length.next + length.value };
}
function offlineUuid(name) {
  const uuid = crypto.createHash('md5').update('OfflinePlayer:' + name, 'utf8').digest();
  uuid[6] = (uuid[6] & 0x0f) | 0x30;
  uuid[8] = (uuid[8] & 0x3f) | 0x80;
  return uuid;
}
function clientInformation() {
  // locale, view distance, chat visibility, colors, skin mask, main hand,
  // text filtering, listed in player list, particle status.
  return Buffer.concat([str('en_us'), Buffer.from([4]), vi(0), Buffer.from([1, 127]),
    vi(1), Buffer.from([0, 1]), vi(0)]);
}

function modernLogin() {
  return new Promise((resolve, reject) => {
    let incoming = Buffer.alloc(0), threshold = -1, state = 'login', done = false;
    let loginSuccess = false, configured = false, joined = false, entityId = null;
    let registries = 0, knownPackRequests = 0, playPackets = 0, inventoryPackets = 0, teleportAcks = 0;
    let configurations = 0, respawns = 0, transferStage = 'initial';
    const serverVisits = [], transferWorlds = [], commandTimers = [];
    const observed = [];
    const socket = net.createConnection({ host: HOST, port: PORT });
    let settleTimer;
    const timeout = setTimeout(() => finish(Error('Modern login timed out in ' + state + ': ' + observed.join(', '))), 20000);

    function finish(error) {
      if (done) return;
      done = true;
      clearTimeout(timeout);
      clearTimeout(settleTimer);
      commandTimers.forEach(clearTimeout);
      socket.destroy();
      if (error) reject(error);
      else resolve({ protocol: PROTOCOL, username: USERNAME, loginSuccess, configured, joined,
        entityId, registries, knownPackRequests, playPackets, inventoryPackets, teleportAcks,
        configurations, respawns, roundTrip: ROUND_TRIP, serverVisits, transferWorlds });
    }
    function send(id, payload = Buffer.alloc(0)) {
      let body = Buffer.concat([vi(id), payload]);
      if (threshold >= 0) {
        body = body.length >= threshold ? Buffer.concat([vi(body.length), zlib.deflateSync(body)])
          : Buffer.concat([vi(0), body]);
      }
      socket.write(Buffer.concat([vi(body.length), body]));
    }
    function cookieResponse(id, payload) {
      const key = readStr(payload);
      send(id, Buffer.concat([str(key.value), Buffer.from([0])]));
    }
    function schedule(fn, milliseconds) {
      commandTimers.push(setTimeout(() => {
        if (done) return;
        try { fn(); } catch (error) { finish(error); }
      }, milliseconds));
    }
    function validateAfterSettling() {
      settleTimer = setTimeout(() => {
        try {
          assert.ok(loginSuccess && configured && joined, 'Must complete actual login, configuration and join game');
          assert.ok(playPackets > 1, 'Must receive continuing play traffic');
          if (ROUND_TRIP) {
            assert.deepEqual(serverVisits, ['lobby', 'pvp', 'lobby'], 'Both command transfers must complete');
            assert.ok(respawns >= 2 || configurations >= 3, 'Must receive transfer respawns or full configuration transitions');
          }
          finish();
        } catch (error) { finish(error); }
      }, 1500);
    }
    function transferArrived(world) {
      if (transferStage === 'switching-pvp') {
        transferStage = 'at-pvp';
        serverVisits.push('pvp');
        transferWorlds.push(world);
        console.log('MODERN_STAGE', 'arrived pvp');
        schedule(() => {
          assert.equal(state, 'play', 'Must be in play before /hub');
          transferStage = 'switching-lobby';
          send(0x07, str('hub')); // Unsigned command excludes leading slash.
        }, 2200);
      } else if (transferStage === 'switching-lobby') {
        transferStage = 'returned-lobby';
        serverVisits.push('lobby');
        transferWorlds.push(world);
        console.log('MODERN_STAGE', 'returned lobby');
        validateAfterSettling();
      }
    }
    function onPacket(id, data) {
      if (observed.length < 60) observed.push(state + ':0x' + id.toString(16));
      if (state === 'login') {
        if (id === 0x00) throw Error('Login rejected: ' + readStr(data).value);
        if (id === 0x01) throw Error('Probe refuses authenticated/encrypted login; use isolated offline proxy only');
        if (id === 0x03) { threshold = readVi(data).value; assert.ok(threshold >= 0); return; }
        if (id === 0x04) { const request = readVi(data); send(0x02, Buffer.concat([vi(request.value), Buffer.from([0])])); return; }
        if (id === 0x05) { cookieResponse(0x04, data); return; }
        if (id === 0x02) {
          assert.ok(data.length >= 17, 'Login success must carry UUID and username');
          assert.equal(readStr(data, 16).value, USERNAME);
          loginSuccess = true;
          send(0x03); // Login acknowledged.
          state = 'config';
          send(0x00, clientInformation());
          console.log('MODERN_STAGE', 'login -> configuration');
        }
        return;
      }
      if (state === 'config') {
        if (id === 0x00) { cookieResponse(0x01, data); return; }
        if (id === 0x02) throw Error('Configuration disconnect: ' + data.toString('utf8'));
        if (id === 0x04 || id === 0x05) { send(id, data); return; }
        if (id === 0x07) { registries++; return; }
        if (id === 0x0e) {
          knownPackRequests++;
          send(0x07, vi(0)); // No cached packs: request full registry data.
          return;
        }
        if (id === 0x09 || id === 0x13) throw Error('Probe does not accept resource packs or conduct agreements automatically');
        if (id === 0x03) {
          configured = true;
          configurations++;
          send(0x03); // Configuration finish acknowledgement.
          state = 'play';
          console.log('MODERN_STAGE', 'configuration -> play');
        }
        return;
      }
      playPackets++;
      if (id === 0x20) throw Error('Play disconnect: ' + data.toString('utf8'));
      if (id === 0x2c) { send(0x1c, data); return; }
      if (id === 0x3d) { send(0x2d, data); return; }
      if (id === 0x15) { cookieResponse(0x15, data); return; }
      if (id === 0x76) {
        // StartUpdate clientbound -> FinishedUpdate serverbound, both verified in Velocity.
        send(0x10);
        state = 'config';
        configured = false;
        send(0x00, clientInformation());
        console.log('MODERN_STAGE', 'play -> configuration');
        return;
      }
      if (id === 0x48) {
        // 1.21.2+ player-position starts with teleport VarInt, before its motion data.
        const teleport = readVi(data);
        assert.ok(teleport && data.length >= teleport.next + 60, 'Complete player-position packet required');
        send(0x00, vi(teleport.value));
        teleportAcks++;
        return;
      }
      if (id === 0x12 || id === 0x14) inventoryPackets++;
      if (id === 0x52) {
        const dimension = readVi(data);
        assert.ok(dimension, 'Respawn must specify dimension type');
        const world = readStr(data, dimension.next).value;
        respawns++;
        transferArrived(world);
      }
      if (id === 0x31) {
        assert.ok(configured && data.length >= 8, 'Join game follows acknowledged configuration');
        entityId = data.readInt32BE(0);
        const worlds = readVi(data, 5);
        assert.ok(worlds && worlds.value > 0 && worlds.value < 100, 'Join game must declare world names');
        assert.ok(readStr(data, worlds.next).value.length > 0, 'World name must not be empty');
        const world = readStr(data, worlds.next).value;
        if (!joined) {
          joined = true;
          serverVisits.push('lobby');
          if (ROUND_TRIP) {
            schedule(() => {
              assert.equal(state, 'play', 'Must be in play before /pvp');
              transferStage = 'switching-pvp';
              send(0x07, str('pvp'));
            }, 2200);
          } else {
            validateAfterSettling();
          }
        } else {
          transferArrived(world);
        }
      }
    }
    socket.on('connect', () => {
      const port = Buffer.alloc(2); port.writeUInt16BE(PORT);
      send(0x00, Buffer.concat([vi(PROTOCOL), str('localhost'), port, vi(2)]));
      send(0x00, Buffer.concat([str(USERNAME), offlineUuid(USERNAME)]));
    });
    socket.on('data', chunk => {
      incoming = Buffer.concat([incoming, chunk]);
      try {
        while (!done) {
          const length = readVi(incoming);
          if (!length) break;
          assert.ok(length.value > 0 && length.value <= MAX_PACKET, 'Bounded positive packet length required');
          if (incoming.length < length.next + length.value) break;
          let body = incoming.subarray(length.next, length.next + length.value);
          incoming = incoming.subarray(length.next + length.value);
          if (threshold >= 0) {
            const plain = readVi(body);
            assert.ok(plain && plain.value >= 0 && plain.value <= MAX_PACKET, 'Bounded uncompressed length required');
            body = plain.value ? zlib.inflateSync(body.subarray(plain.next), { maxOutputLength: MAX_PACKET }) : body.subarray(plain.next);
            if (plain.value) assert.equal(body.length, plain.value);
          }
          const id = readVi(body);
          assert.ok(id, 'Packet id required');
          onPacket(id.value, body.subarray(id.next));
        }
      } catch (error) { finish(error); }
    });
    socket.on('error', error => finish(error));
    socket.on('close', () => { if (!done) finish(Error('Connection closed before validation in ' + state + ': ' + observed.join(', '))); });
  });
}

if (process.argv.slice(2).some(argument => argument !== '--roundtrip')) {
  console.error('Only --roundtrip is accepted; target is restricted to 127.0.0.1:25569.');
  process.exitCode = 1;
} else {
  modernLogin().then(result => console.log('MODERN_LOGIN_PASS', JSON.stringify(result)))
    .catch(error => { console.error(error); process.exitCode = 1; });
}
