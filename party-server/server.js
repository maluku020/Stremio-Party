// Party sync server: rooms, gedeelde navigatie en gedeelde afspeelbediening.
// Start: node server.js   (poort via PORT, standaard 8787)

const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = Number(process.env.PORT) || 8787;
const MAX_MEMBERS = 12;
const EMPTY_ROOM_MS = Number(process.env.EMPTY_ROOM_MS) || 30 * 60 * 1000; // lege party 30 min bewaren
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

const rooms = new Map(); // code -> room

const now = () => Date.now();

const makeCode = () => {
    let code;
    do {
        code = 'MLK-' + Array.from({ length: 4 }, () => CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]).join('');
    } while (rooms.has(code));
    return code;
};

const cleanName = (name) => String(name || '').trim().slice(0, 24) || 'Gast';

const send = (ws, msg) => {
    if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(msg));
};

const memberList = (room) => [...room.members.values()].map((m) => ({ id: m.id, name: m.name, host: m.id === room.hostId }));

const broadcast = (room, msg, exceptWs = null) => {
    for (const m of room.members.values()) {
        if (m.ws !== exceptWs) send(m.ws, msg);
    }
};

// Verwachte tijd nu, rekening houdend met hoe lang er al afgespeeld wordt.
const expectedTime = (pb) => {
    if (!pb || typeof pb.time !== 'number') return null;
    if (pb.paused) return pb.time;
    return pb.time + (now() - pb.updatedAt) / 1000;
};

const roomState = (room) => ({
    code: room.code,
    members: memberList(room),
    hostOnly: room.hostOnly,
    path: room.path,
    playback: room.playback ? { ...room.playback, time: expectedTime(room.playback), updatedAt: now() } : null,
});

const canControl = (room, member) => !room.hostOnly || member.id === room.hostId;

const leave = (ws) => {
    const member = ws.member;
    const room = ws.room;
    if (!member || !room) return;
    room.members.delete(member.id);
    ws.room = null;
    if (room.members.size === 0) {
        // Even bewaren: valt de verbinding kort weg (scherm uit, andere app), dan kan iedereen terug in dezelfde party
        clearTimeout(room.emptyTimer);
        room.emptyTimer = setTimeout(() => {
            if (room.members.size === 0 && rooms.get(room.code) === room) rooms.delete(room.code);
        }, EMPTY_ROOM_MS);
        return;
    }
    if (room.hostId === member.id) {
        room.hostId = room.members.keys().next().value;
    }
    broadcast(room, { type: 'members', members: memberList(room), hostOnly: room.hostOnly });
    broadcast(room, { type: 'notice', text: `${member.name} heeft de party verlaten` });
};

const join = (ws, room, name) => {
    if (room.members.size >= MAX_MEMBERS) {
        send(ws, { type: 'error', text: 'Deze party is vol' });
        return;
    }
    leave(ws);
    const member = { id: ws.id, name: cleanName(name), ws };
    clearTimeout(room.emptyTimer);
    room.members.set(member.id, member);
    if (!room.hostId || !room.members.has(room.hostId)) room.hostId = member.id;
    ws.member = member;
    ws.room = room;
    send(ws, { type: 'joined', you: member.id, state: roomState(room) });
    broadcast(room, { type: 'members', members: memberList(room), hostOnly: room.hostOnly }, ws);
    broadcast(room, { type: 'notice', text: `${member.name} doet mee` }, ws);
};

let nextId = 1;

const handle = (ws, msg) => {
    const room = ws.room;
    const member = ws.member;
    switch (msg.type) {
        case 'create': {
            let code = makeCode();
            // Eigen code (bijv. "PAPA"): alleen letters en cijfers, 3 tot 12 tekens
            if (msg.code) {
                const own = String(msg.code).toUpperCase().replace(/[^A-Z0-9]/g, '');
                if (own.length < 3 || own.length > 12) {
                    send(ws, { type: 'error', text: 'Eigen code moet 3 tot 12 letters of cijfers zijn' });
                    return;
                }
                if (rooms.has(own) || rooms.has('MLK-' + own)) {
                    send(ws, { type: 'error', text: 'Deze code is al in gebruik. Kies een andere.' });
                    return;
                }
                code = own;
            }
            const newRoom = { code, members: new Map(), hostId: null, hostOnly: false, path: null, playback: null };
            rooms.set(code, newRoom);
            join(ws, newRoom, msg.name);
            break;
        }
        case 'join': {
            const code = String(msg.code || '').trim().toUpperCase();
            const bare = code.replace(/[^A-Z0-9]/g, '');
            const target = rooms.get(code) || rooms.get(bare) || rooms.get('MLK-' + bare) ||
                (bare.startsWith('MLK') ? rooms.get('MLK-' + bare.slice(3)) : undefined);
            if (!target) {
                send(ws, { type: 'error', text: 'Party niet gevonden. Klopt de code?' });
                return;
            }
            join(ws, target, msg.name);
            break;
        }
        case 'leave':
            leave(ws);
            send(ws, { type: 'left' });
            break;
        case 'nav': {
            if (!room || typeof msg.path !== 'string' || msg.path.length > 4000) return;
            if (!canControl(room, member)) return;
            if (room.path === msg.path) return;
            room.path = msg.path;
            // Nieuwe pagina of andere video = oude afspeelstatus vervalt
            room.playback = null;
            broadcast(room, { type: 'nav', path: msg.path, by: member.name }, ws);
            break;
        }
        case 'playback': {
            if (!room) return;
            if (!canControl(room, member)) {
                send(ws, { type: 'notice', text: 'Alleen de host bedient nu' });
                // stuur juiste status terug zodat deze kijker weer gelijk loopt
                if (room.playback) send(ws, { type: 'playback', ...room.playback, time: expectedTime(room.playback), action: 'sync', by: null });
                return;
            }
            const time = Number(msg.time);
            if (!Number.isFinite(time) || time < 0) return;
            room.playback = { paused: !!msg.paused, time, updatedAt: now() };
            broadcast(room, { type: 'playback', ...room.playback, action: msg.action || 'sync', by: msg.action === 'sync' ? null : member.name }, ws);
            break;
        }
        case 'stateRequest':
            if (room) send(ws, { type: 'state', state: roomState(room) });
            break;
        case 'hostOnly':
            if (!room || member.id !== room.hostId) return;
            room.hostOnly = !!msg.value;
            broadcast(room, { type: 'members', members: memberList(room), hostOnly: room.hostOnly });
            broadcast(room, { type: 'notice', text: room.hostOnly ? 'Alleen de host bedient nu' : 'Iedereen mag weer bedienen' });
            break;
        case 'chat':
            if (!room) return;
            broadcast(room, { type: 'chat', by: member.name, text: String(msg.text || '').slice(0, 300) });
            break;
        case 'ping':
            send(ws, { type: 'pong', t: msg.t, serverTime: now() });
            break;
    }
};

const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/plain', 'Access-Control-Allow-Origin': '*' });
    res.end(`party server ok — ${rooms.size} rooms\n`);
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws) => {
    ws.id = String(nextId++);
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });
    ws.on('message', (data) => {
        let msg;
        try { msg = JSON.parse(data); } catch { return; }
        if (msg && typeof msg.type === 'string') handle(ws, msg);
    });
    ws.on('close', () => leave(ws));
});

// Dode verbindingen opruimen
setInterval(() => {
    for (const ws of wss.clients) {
        if (!ws.isAlive) { ws.terminate(); continue; }
        ws.isAlive = false;
        ws.ping();
    }
}, 30000);

if (require.main === module) {
    server.listen(PORT, () => console.log(`Party server op poort ${PORT}`));
}

module.exports = { server, rooms };
