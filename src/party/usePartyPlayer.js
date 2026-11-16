// Gedeelde afstandsbediening in de speler:
// pauze / play / spoelen bij één persoon = bij iedereen.

const React = require('react');
const { useParty } = require('./PartyContext');

const SUPPRESS_MS = 1500;        // na een opdracht van een ander: eigen veranderingen niet terugsturen
const HEARTBEAT_MS = 5000;       // host stuurt elke 5 sec. waar hij is
const DRIFT_LIMIT_S = 3;         // meer dan 3 sec. verschil = gelijktrekken
const CORRECTION_COOLDOWN_MS = 8000;

const usePartyPlayer = (video) => {
    const party = useParty();
    const room = party ? party.room : null;
    const stateRef = React.useRef(video.state);
    stateRef.current = video.state;
    const suppressUntilRef = React.useRef(0);
    const lastRef = React.useRef({ time: null, wall: 0, paused: null });
    const lastCorrectionRef = React.useRef(0);
    const loadedRef = React.useRef(false);

    const nowS = () => (stateRef.current.time === null ? null : stateRef.current.time / 1000);

    const send = React.useCallback((action) => {
        if (!party || !party.room) return;
        const time = nowS();
        if (time === null || stateRef.current.paused === null) return;
        party.client.send({ type: 'playback', paused: !!stateRef.current.paused, time, action });
    }, [party]);

    const apply = React.useCallback((msg) => {
        if (!msg || typeof msg.time !== 'number') return;
        const state = stateRef.current;
        if (state.paused === null) return; // video nog niet geladen
        suppressUntilRef.current = Date.now() + SUPPRESS_MS;
        if (!!state.paused !== !!msg.paused) {
            video.setPaused(!!msg.paused);
        }
        const current = nowS();
        const diff = current === null ? Infinity : Math.abs(current - msg.time);
        const isSync = msg.action === 'sync';
        if (isSync) {
            if (diff > DRIFT_LIMIT_S && Date.now() - lastCorrectionRef.current > CORRECTION_COOLDOWN_MS) {
                lastCorrectionRef.current = Date.now();
                video.setTime(msg.time * 1000);
            }
        } else if (diff > 1.5) {
            video.setTime(msg.time * 1000);
        }
        if (msg.by) {
            const label = msg.action === 'pause' ? 'heeft gepauzeerd ⏸' : msg.action === 'play' ? 'drukte op play ▶️' : msg.action === 'seek' ? 'heeft gespoeld ⏩' : null;
            if (label) party.client.emit('message', { type: 'notice', text: `${msg.by} ${label}` });
        }
    }, [video, party]);

    // Opdrachten van anderen uitvoeren
    React.useEffect(() => {
        if (!party || !room) return;
        party.client.on('playback', apply);
        return () => party.client.off('playback', apply);
    }, [party, room, apply]);

    // Zodra de video geladen is: meteen naar de plek van de groep
    React.useEffect(() => {
        if (!party || !room) return;
        if (video.state.duration === null || video.state.paused === null) {
            loadedRef.current = false;
            return;
        }
        if (loadedRef.current) return;
        loadedRef.current = true;
        const pb = party.getPlayback();
        if (pb) apply({ ...pb, action: 'join' });
        else party.client.send({ type: 'stateRequest' });
    }, [party, room, video.state.duration, video.state.paused, apply]);

    // Eigen pauze / play doorgeven
    React.useEffect(() => {
        const paused = video.state.paused;
        const prev = lastRef.current.paused;
        lastRef.current.paused = paused;
        if (!room || paused === null || prev === null || prev === paused) return;
        if (Date.now() < suppressUntilRef.current) return;
        if (!party.canControl) {
            // geen bediening: terugzetten naar groepsstatus
            const pb = party.getPlayback();
            if (pb) apply({ ...pb, action: 'join' });
            return;
        }
        send(paused ? 'pause' : 'play');
    }, [video.state.paused]);

    // Eigen spoelen herkennen: tijd springt meer dan normaal
    React.useEffect(() => {
        const time = nowS();
        const wall = Date.now();
        const last = lastRef.current;
        const prevTime = last.time;
        const elapsed = (wall - last.wall) / 1000;
        last.time = time;
        last.wall = wall;
        if (!room || time === null || prevTime === null) return;
        if (Date.now() < suppressUntilRef.current) return;
        const forwardJump = time - prevTime > elapsed + 3;
        const backwardJump = time < prevTime - 1.5;
        if (forwardJump || backwardJump) {
            if (!party.canControl) {
                const pb = party.getPlayback();
                if (pb) apply({ ...pb, action: 'join' });
                return;
            }
            send('seek');
        }
    }, [video.state.time]);

    // Host stuurt regelmatig waar hij is, zodat iedereen gelijk blijft lopen
    React.useEffect(() => {
        if (!room || !party.me || !party.me.host) return;
        const id = setInterval(() => {
            if (Date.now() < suppressUntilRef.current) return;
            send('sync');
        }, HEARTBEAT_MS);
        return () => clearInterval(id);
    }, [room, party && party.me && party.me.host, send]);
};

module.exports = usePartyPlayer;
