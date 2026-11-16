// Verbinding met de party-server. Maakt automatisch opnieuw verbinding
// en komt dan weer in dezelfde room.

const EventEmitter = require('eventemitter3');

const DEFAULT_SERVER_URL = process.env.PARTY_SERVER_URL || 'wss://party-server-cvcz.onrender.com';

const getServerUrl = () => {
    try {
        return window.localStorage.getItem('party_server_url') || DEFAULT_SERVER_URL;
    } catch {
        return DEFAULT_SERVER_URL;
    }
};

class PartyClient extends EventEmitter {
    constructor() {
        super();
        this.ws = null;
        this.code = null;
        this.name = null;
        this.wantRoom = false;
        this.connected = false;
        this.retry = 0;
        this.retryTimer = null;
        this.queue = [];
    }

    connect() {
        if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return;
        let ws;
        try {
            ws = new WebSocket(getServerUrl());
        } catch (error) {
            this.emit('error', { text: 'Kan party-server niet bereiken' });
            return;
        }
        this.ws = ws;
        ws.onopen = () => {
            this.connected = true;
            this.retry = 0;
            this.emit('connection', true);
            // Na herverbinden: terug in dezelfde room
            if (this.wantRoom && this.code) {
                this.rawSend({ type: 'join', code: this.code, name: this.name });
            }
            const queue = this.queue;
            this.queue = [];
            queue.forEach((msg) => this.rawSend(msg));
        };
        ws.onmessage = (event) => {
            let msg;
            try { msg = JSON.parse(event.data); } catch { return; }
            if (msg.type === 'joined') this.code = msg.state.code;
            if (msg.type === 'left') { this.code = null; this.wantRoom = false; }
            this.emit('message', msg);
        };
        ws.onclose = () => {
            this.connected = false;
            this.emit('connection', false);
            if (this.wantRoom) this.scheduleReconnect();
        };
        ws.onerror = () => {};
    }

    scheduleReconnect() {
        clearTimeout(this.retryTimer);
        const delay = Math.min(10000, 500 * Math.pow(2, this.retry++));
        this.retryTimer = setTimeout(() => this.connect(), delay);
    }

    rawSend(msg) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(msg));
        }
    }

    send(msg) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.rawSend(msg);
        } else {
            this.queue.push(msg);
            this.connect();
        }
    }

    create(name) {
        this.name = name;
        this.wantRoom = true;
        this.send({ type: 'create', name });
    }

    join(code, name) {
        this.name = name;
        this.code = code;
        this.wantRoom = true;
        this.send({ type: 'join', code, name });
    }

    leave() {
        this.wantRoom = false;
        this.code = null;
        clearTimeout(this.retryTimer);
        this.rawSend({ type: 'leave' });
        this.emit('message', { type: 'left' });
    }
}

module.exports = { PartyClient, getServerUrl };
