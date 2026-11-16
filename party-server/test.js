// Test met 3 kijkers: maken, joinen, navigeren, pauze, host-slot.
const assert = require('assert');
const WebSocket = require('ws');
const { server } = require('./server');

const client = (port) => new Promise((resolve) => {
    const ws = new WebSocket(`ws://127.0.0.1:${port}`);
    ws.inbox = [];
    ws.on('message', (d) => ws.inbox.push(JSON.parse(d)));
    ws.on('open', () => resolve(ws));
});
const sendMsg = (ws, m) => ws.send(JSON.stringify(m));
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const last = (ws, type) => [...ws.inbox].reverse().find((m) => m.type === type);

(async () => {
    await new Promise((r) => server.listen(0, r));
    const port = server.address().port;
    const [a, b, c] = await Promise.all([client(port), client(port), client(port)]);

    sendMsg(a, { type: 'create', name: 'Maluku' });
    await wait(100);
    const code = last(a, 'joined').state.code;
    assert.match(code, /^MLK-[A-Z0-9]{4}$/);

    sendMsg(b, { type: 'join', code, name: 'Henri' });
    sendMsg(c, { type: 'join', code: code.slice(4).toLowerCase(), name: 'Yoshi' }); // zonder MLK- en kleine letters
    await wait(100);
    assert.equal(last(c, 'joined').state.members.length, 3);

    // Henri bladert -> anderen volgen
    sendMsg(b, { type: 'nav', path: '/detail/movie/tt0111161' });
    await wait(100);
    assert.equal(last(a, 'nav').path, '/detail/movie/tt0111161');
    assert.equal(last(c, 'nav').by, 'Henri');
    assert.equal(last(b, 'nav'), undefined, 'afzender krijgt eigen nav niet terug');

    // Yoshi pauzeert -> iedereen pauzeert
    sendMsg(c, { type: 'nav', path: '/player/abc' });
    sendMsg(c, { type: 'playback', paused: true, time: 120.5, action: 'pause' });
    await wait(100);
    const p = last(a, 'playback');
    assert.equal(p.paused, true);
    assert.equal(p.time, 120.5);
    assert.equal(p.by, 'Yoshi');

    // Nieuwe kijker krijgt meteen de goede plek
    const d = await client(port);
    sendMsg(d, { type: 'join', code, name: 'Laatkomer' });
    await wait(100);
    const st = last(d, 'joined').state;
    assert.equal(st.path, '/player/abc');
    assert.equal(st.playback.paused, true);
    assert.equal(st.playback.time, 120.5);

    // Host-slot: alleen Maluku mag bedienen
    sendMsg(a, { type: 'hostOnly', value: true });
    await wait(50);
    b.inbox = []; a.inbox = [];
    sendMsg(b, { type: 'playback', paused: false, time: 130, action: 'play' });
    await wait(100);
    assert.equal(last(a, 'playback'), undefined, 'gast mag niet bedienen bij host-slot');
    assert.equal(last(b, 'playback').time, 120.5, 'gast wordt teruggezet');

    // Host vertrekt -> nieuwe host
    a.close();
    await wait(100);
    const members = last(b, 'members').members;
    assert.equal(members.length, 3);
    assert.equal(members.filter((m) => m.host).length, 1);

    // Foute code
    const e = await client(port);
    sendMsg(e, { type: 'join', code: 'MLK-XXXX', name: 'x' });
    await wait(50);
    assert.ok(last(e, 'error'));

    console.log('Alle server-tests geslaagd ✅');
    process.exit(0);
})().catch((err) => { console.error(err); process.exit(1); });
