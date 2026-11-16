// Party-status voor de hele app + gedeeld bladeren (navigatie).

const React = require('react');
const { createPath, useLocation, useNavigate } = require('react-router');
const { useToast } = require('stremio/common');
const { PartyClient } = require('./PartyClient');

const PartyContext = React.createContext(null);

// Pagina's die we delen met de groep. Instellingen, add-ons en bibliotheek blijven persoonlijk.
const SHARED_PATHS = [/^\/$/, /^\/discover/, /^\/metadetails/, /^\/detail/, /^\/search/, /^\/player/];
const isSharedPath = (path) => SHARED_PATHS.some((re) => re.test(path.split('?')[0]));

const getStoredName = () => {
    try { return window.localStorage.getItem('party_name') || ''; } catch { return ''; }
};

const PartyProvider = ({ children }) => {
    const location = useLocation();
    const navigate = useNavigate();
    const toast = useToast();
    const client = React.useMemo(() => new PartyClient(), []);
    const [room, setRoom] = React.useState(null); // { code, members, hostOnly, you }
    const [connected, setConnected] = React.useState(false);
    const [name, setNameState] = React.useState(getStoredName);
    const lastRemotePathRef = React.useRef(null);
    const playbackRef = React.useRef(null); // laatste bekende afspeelstatus van de room
    const locationPath = createPath(location);
    const locationPathRef = React.useRef(locationPath);
    locationPathRef.current = locationPath;

    const setName = React.useCallback((value) => {
        setNameState(value);
        try { window.localStorage.setItem('party_name', value); } catch { /* */ }
    }, []);

    const notify = React.useCallback((text) => {
        toast.show({ type: 'info', title: text, timeout: 2500 });
    }, [toast]);

    const applyRemotePath = React.useCallback((path) => {
        if (typeof path !== 'string' || path === locationPathRef.current) return;
        lastRemotePathRef.current = path;
        navigate(path);
    }, [navigate]);

    React.useEffect(() => {
        const onConnection = (value) => setConnected(value);
        const onMessage = (msg) => {
            switch (msg.type) {
                case 'joined':
                    setRoom({ code: msg.state.code, members: msg.state.members, hostOnly: msg.state.hostOnly, you: msg.you });
                    playbackRef.current = msg.state.playback ? { ...msg.state.playback, receivedAt: Date.now() } : null;
                    if (msg.state.path) applyRemotePath(msg.state.path);
                    else if (isSharedPath(locationPathRef.current)) client.send({ type: 'nav', path: locationPathRef.current });
                    break;
                case 'state':
                    playbackRef.current = msg.state.playback ? { ...msg.state.playback, receivedAt: Date.now() } : null;
                    client.emit('playback', { ...playbackRef.current, action: 'sync', by: null });
                    break;
                case 'members':
                    setRoom((prev) => prev && { ...prev, members: msg.members, hostOnly: msg.hostOnly });
                    break;
                case 'left':
                    setRoom(null);
                    playbackRef.current = null;
                    break;
                case 'nav':
                    applyRemotePath(msg.path);
                    if (msg.path.startsWith('/player')) notify(`${msg.by} start de film 🎬`);
                    break;
                case 'playback':
                    playbackRef.current = { paused: msg.paused, time: msg.time, receivedAt: Date.now() };
                    client.emit('playback', msg);
                    break;
                case 'notice':
                    notify(msg.text);
                    break;
                case 'chat':
                    notify(`${msg.by}: ${msg.text}`);
                    break;
                case 'error':
                    toast.show({ type: 'error', title: msg.text, timeout: 3500 });
                    break;
            }
        };
        const onError = (err) => toast.show({ type: 'error', title: err.text, timeout: 3500 });
        client.on('connection', onConnection);
        client.on('message', onMessage);
        client.on('error', onError);
        return () => {
            client.off('connection', onConnection);
            client.off('message', onMessage);
            client.off('error', onError);
        };
    }, [client, applyRemotePath, notify, toast]);

    // Eigen navigatie doorgeven aan de groep
    React.useEffect(() => {
        if (!room) return;
        if (locationPath === lastRemotePathRef.current) return;
        lastRemotePathRef.current = null;
        if (!isSharedPath(locationPath)) return;
        if (room.hostOnly && !room.members.find((m) => m.id === room.you)?.host) return;
        client.send({ type: 'nav', path: locationPath });
    }, [locationPath, room, client]);

    // Meedoen via link: https://.../?party=MLK-AB12
    React.useEffect(() => {
        const code = new URLSearchParams(window.location.search).get('party');
        if (code) {
            client.join(code, getStoredName() || 'Gast');
            const url = new URL(window.location.href);
            url.searchParams.delete('party');
            window.history.replaceState(null, '', url.toString());
        }
    }, [client]);

    const me = room ? room.members.find((m) => m.id === room.you) : null;
    const value = React.useMemo(() => ({
        client,
        room,
        me,
        connected,
        name,
        setName,
        canControl: !!room && (!room.hostOnly || !!me?.host),
        getPlayback: () => {
            const pb = playbackRef.current;
            if (!pb) return null;
            return { paused: pb.paused, time: pb.paused ? pb.time : pb.time + (Date.now() - pb.receivedAt) / 1000 };
        },
        create: () => client.create(name || 'Gast'),
        join: (code) => client.join(code, name || 'Gast'),
        leave: () => client.leave(),
        setHostOnly: (v) => client.send({ type: 'hostOnly', value: v }),
        sendChat: (text) => client.send({ type: 'chat', text }),
    }), [client, room, me, connected, name, setName]);

    return <PartyContext.Provider value={value}>{children}</PartyContext.Provider>;
};

const useParty = () => React.useContext(PartyContext);

module.exports = { PartyProvider, useParty, isSharedPath };
