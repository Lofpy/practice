'use strict';
// Offline diagnostic ONLY for the isolated test proxy at 127.0.0.1:25665.
// No configurable host/port, no authentication changes, no world/admin commands.
// 777 = Java 26.3; --old uses 776 (26.2) to check both admission routes are denied.
// Packet IDs and the new teleport-confirmation position fields verified against:
// https://github.com/ViaVersion/ViaVersion/tree/master/common/src/main/java/com/viaversion/viaversion/protocols/v26_2to26_3
// Configuration enum comments are stale after POST_EFFECTS; IDs are ordinals.
const net = require('node:net');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');

const HOST = '127.0.0.1';
const PORT = 25665;
const OLD = process.argv.includes('--old');
const PROTOCOL = OLD ? 776 : 777;
const USERNAME = OLD ? 'SurvivalOld776' : 'SurvivalProbe777';
const MAX_PACKET = 8 * 1024 * 1024;
const IDS = OLD
  ? { login: 0x31, respawn: 0x52, keepAlive: 0x2c, ping: 0x3d, position: 0x48,
      startConfiguration: 0x76, knownPacks: 0x0e, conduct: 0x13, objective: 0x6a, score: 0x6e, chat: 0x79 }
  : { login: 0x32, respawn: 0x54, keepAlive: 0x2d, ping: 0x3e, position: 0x49,
      startConfiguration: 0x78, knownPacks: 0x0f, conduct: 0x14, objective: 0x6c, score: 0x70, chat: 0x7c };

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
  return Buffer.concat([str('en_us'), Buffer.from([4]), vi(0), Buffer.from([1, 127]),
    vi(1), Buffer.from([0, 1]), vi(0)]);
}

function runProbe() {
  return new Promise((resolve, reject) => {
    let incoming = Buffer.alloc(0), threshold = -1, state = 'login', done = false;
    let joined = false, stage = 'initial', configurations = 0, respawns = 0, teleportAcks = 0;
    let denials = 0, commandBaseline = 0, playPackets = 0;
    let position = { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 };
    const observed = [], visits = [], worlds = [], timers = [], scoreRows = new Set();
    let survivalTitle = false, lobbyTitle = false;
    const socket = net.createConnection({ host: HOST, port: PORT });
    const timeout = setTimeout(() => finish(Error('Probe timed out: ' + state + '/' + stage + ' ' + observed.join(', '))), 40000);

    function finish(error) {
      if (done) return;
      done = true;
      clearTimeout(timeout);
      timers.forEach(clearTimeout);
      socket.destroy();
      if (error) reject(error);
      else resolve({ protocol: PROTOCOL, username: USERNAME, visits, worlds, configurations,
        respawns, teleportAcks, playPackets, denials, survivalTitle, lobbyTitle, scoreRows: [...scoreRows] });
    }
    function schedule(fn, milliseconds) {
      timers.push(setTimeout(() => {
        if (!done) try { fn(); } catch (error) { finish(error); }
      }, milliseconds));
    }
    function send(id, payload = Buffer.alloc(0)) {
      let body = Buffer.concat([vi(id), payload]);
      if (threshold >= 0) body = body.length >= threshold
        ? Buffer.concat([vi(body.length), zlib.deflateSync(body)]) : Buffer.concat([vi(0), body]);
      socket.write(Buffer.concat([vi(body.length), body]));
    }
    function command(value) {
      assert.equal(state, 'play', 'Commands require completed configuration');
      console.log('SURVIVAL_STAGE', '/' + value);
      send(0x07, str(value));
    }
    function cookieResponse(id, payload) {
      send(id, Buffer.concat([str(readStr(payload).value), Buffer.from([0])]));
    }
    function verifyFinal() {
      assert.ok(joined && lobbyTitle && teleportAcks > 0 && playPackets > 1, 'Actual lobby login and play traffic required');
      if (OLD) {
        assert.deepEqual(visits, ['lobby']);
        assert.equal(denials, 2, 'Both /survival and /server survival must be refused by the exact-version gate');
        assert.equal(respawns, commandBaseline, 'Wrong-version client must remain on the same backend');
        assert.equal(survivalTitle, false);
      } else {
        assert.deepEqual(visits, ['lobby', 'survival', 'lobby']);
        assert.ok(survivalTitle, 'Survival sidebar title must be delivered');
        for (const prefix of ['Online: ', 'Ping: ', 'X: ', 'Y: ', 'Z: ']) {
          assert.ok([...scoreRows].some(row => row.startsWith(prefix)), 'Missing Survival sidebar row: ' + prefix);
        }
      }
      finish();
    }
    function beginTransfers() {
      if (stage !== 'initial') return;
      stage = OLD ? 'denying-menu-command' : 'switching-survival';
      commandBaseline = respawns;
      command('survival');
      if (OLD) schedule(() => {
        assert.equal(denials, 1, '/survival must produce the version-gate message');
        stage = 'denying-direct-command';
        command('server survival');
        schedule(verifyFinal, 2200);
      }, 2200);
    }
    function observeObjective(data) {
      const objective = readStr(data);
      const mode = data[objective.next];
      if (mode !== 0 && mode !== 2) return;
      const text = data.subarray(objective.next + 1).toString('utf8');
      if (objective.value === 'survival' && text.includes('Survival')) {
        survivalTitle = true;
        assert.equal(OLD, false, 'Older client unexpectedly received the Survival scoreboard');
        if (stage === 'switching-survival') {
          stage = 'at-survival';
          visits.push('survival');
          console.log('SURVIVAL_STAGE', 'Survival sidebar received');
          schedule(() => { stage = 'switching-lobby'; command('hub'); }, 2600);
        }
      } else if (objective.value === 'poppy_lobby' && text.includes('AscendingMC')) {
        lobbyTitle = true;
        if (stage === 'switching-lobby') {
          stage = 'returned-lobby';
          visits.push('lobby');
          schedule(verifyFinal, 1500);
        }
      }
    }
    function acknowledgePosition(data) {
      const teleport = readVi(data);
      assert.ok(teleport && data.length >= teleport.next + 60, 'Complete position packet required');
      const offset = teleport.next, flags = data.readInt32BE(offset + 56);
      const next = {
        x: data.readDoubleBE(offset) + ((flags & 1) ? position.x : 0),
        y: data.readDoubleBE(offset + 8) + ((flags & 2) ? position.y : 0),
        z: data.readDoubleBE(offset + 16) + ((flags & 4) ? position.z : 0),
        yaw: data.readFloatBE(offset + 48) + ((flags & 8) ? position.yaw : 0),
        pitch: data.readFloatBE(offset + 52) + ((flags & 16) ? position.pitch : 0)
      };
      for (const value of Object.values(next)) assert.ok(Number.isFinite(value), 'Finite position required');
      position = next;
      const transform = Buffer.alloc(32);
      transform.writeDoubleBE(next.x, 0); transform.writeDoubleBE(next.y, 8); transform.writeDoubleBE(next.z, 16);
      transform.writeFloatBE(next.yaw, 24); transform.writeFloatBE(next.pitch, 28);
      if (OLD) {
        send(0x00, vi(teleport.value));
        // Pre-26.3 clients report position separately immediately after accepting the teleport.
        send(0x1f, Buffer.concat([transform, Buffer.from([0])]));
      } else {
        send(0x00, Buffer.concat([vi(teleport.value), transform]));
      }
      teleportAcks++;
    }
    function onPacket(id, data) {
      if (observed.length < 90) observed.push(state + ':0x' + id.toString(16));
      if (state === 'login') {
        if (id === 0x00) throw Error('Login rejected: ' + readStr(data).value);
        if (id === 0x01) throw Error('Refusing encrypted login: isolated offline proxy only');
        if (id === 0x03) { threshold = readVi(data).value; assert.ok(threshold >= 0); return; }
        if (id === 0x04) { send(0x02, Buffer.concat([vi(readVi(data).value), Buffer.from([0])])); return; }
        if (id === 0x05) { cookieResponse(0x04, data); return; }
        if (id === 0x02) {
          assert.equal(readStr(data, 16).value, USERNAME);
          send(0x03); state = 'config'; send(0x00, clientInformation());
        }
        return;
      }
      if (state === 'config') {
        if (id === 0x00) { cookieResponse(0x01, data); return; }
        if (id === 0x02) throw Error('Configuration disconnect: ' + data.toString('utf8'));
        if (id === 0x04 || id === 0x05) { send(id, data); return; }
        if (id === IDS.knownPacks) { send(0x07, vi(0)); return; }
        if (id === 0x09 || id === IDS.conduct) throw Error('Probe does not accept packs or conduct agreements');
        if (id === 0x03) { configurations++; send(0x03); state = 'play'; }
        return;
      }
      playPackets++;
      if (id === 0x20) throw Error('Play disconnect: ' + data.toString('utf8'));
      if (id === IDS.keepAlive) { send(0x1c, data); return; }
      if (id === IDS.ping) { send(0x2d, data); return; }
      if (id === 0x15) { cookieResponse(0x15, data); return; }
      if (id === IDS.startConfiguration) {
        send(0x10); state = 'config'; send(0x00, clientInformation()); return;
      }
      if (id === IDS.position) { acknowledgePosition(data); return; }
      if (id === 0x0b) { const rate = Buffer.alloc(4); rate.writeFloatBE(16); send(0x0b, rate); }
      if (id === IDS.objective) observeObjective(data);
      if (id === IDS.score) {
        const holder = readStr(data), objective = readStr(data, holder.next);
        if (objective.value === 'survival') scoreRows.add(holder.value.replace(/§./g, ''));
      }
      if (id === IDS.chat && data.toString('utf8').includes('Survival は Minecraft Java 26.3 専用')) denials++;
      if (id === IDS.respawn) {
        const dimension = readVi(data);
        worlds.push(readStr(data, dimension.next).value);
        respawns++;
      }
      if (id === IDS.login) {
        assert.ok(configurations > 0 && data.length >= 8, 'Join must follow configuration');
        if (!joined) {
          joined = true; visits.push('lobby');
          schedule(beginTransfers, 2400);
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
          assert.ok(length.value > 0 && length.value <= MAX_PACKET, 'Bounded packet length required');
          if (incoming.length < length.next + length.value) break;
          let body = incoming.subarray(length.next, length.next + length.value);
          incoming = incoming.subarray(length.next + length.value);
          if (threshold >= 0) {
            const plain = readVi(body);
            assert.ok(plain && plain.value >= 0 && plain.value <= MAX_PACKET, 'Bounded decompression required');
            body = plain.value ? zlib.inflateSync(body.subarray(plain.next), { maxOutputLength: MAX_PACKET }) : body.subarray(plain.next);
            if (plain.value) assert.equal(body.length, plain.value);
          }
          const id = readVi(body);
          assert.ok(id, 'Packet ID required');
          onPacket(id.value, body.subarray(id.next));
        }
      } catch (error) { finish(error); }
    });
    socket.on('error', error => finish(error));
    socket.on('close', () => { if (!done) finish(Error('Connection closed in ' + state + '/' + stage + ': ' + observed.join(', '))); });
  });
}

if (process.argv.slice(2).some(argument => argument !== '--old')) {
  console.error('Only --old is accepted. Target is always 127.0.0.1:25665.');
  process.exitCode = 1;
} else {
  runProbe().then(result => console.log('SURVIVAL_NETWORK_PASS', JSON.stringify(result)))
    .catch(error => { console.error(error); process.exitCode = 1; });
}
