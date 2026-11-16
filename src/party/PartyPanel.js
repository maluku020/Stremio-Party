// De Party-knop en het paneel: party maken, meedoen, wie kijkt er mee.

const React = require('react');
const { useParty } = require('./PartyContext');
const styles = require('./styles');

const PartyPanel = () => {
    const party = useParty();
    const [open, setOpen] = React.useState(false);
    const [code, setCode] = React.useState('');
    const [chat, setChat] = React.useState('');
    const [copied, setCopied] = React.useState(false);

    if (!party) return null;
    const { room, me, name, setName, connected } = party;

    const shareLink = room ? `${window.location.origin}${window.location.pathname}?party=${room.code}` : '';

    const copyLink = () => {
        const done = () => { setCopied(true); setTimeout(() => setCopied(false), 1500); };
        if (navigator.share && /Android|iPhone|iPad/i.test(navigator.userAgent)) {
            navigator.share({ title: 'Kijk mee!', text: `Doe mee met mijn party: ${room.code}`, url: shareLink }).catch(() => {});
        } else if (navigator.clipboard) {
            navigator.clipboard.writeText(shareLink).then(done, () => {});
        }
    };

    const onJoin = (e) => {
        e.preventDefault();
        if (code.trim()) party.join(code.trim());
    };

    const onChat = (e) => {
        e.preventDefault();
        if (chat.trim()) { party.sendChat(chat.trim()); setChat(''); }
    };

    return (
        <div className={styles['party-root']}>
            <button
                className={styles['party-button'] + (room ? ' ' + styles['active'] : '')}
                onClick={() => setOpen(!open)}
                tabIndex={0}
                title={'Samen kijken'}
            >
                🎉 {room ? `${room.members.length}` : 'Party'}
                {room && !connected ? ' ⚠️' : ''}
            </button>
            {
                open &&
                <div className={styles['party-panel']}>
                    <div className={styles['party-header']}>
                        <span>Samen kijken</span>
                        <button className={styles['close']} onClick={() => setOpen(false)}>✕</button>
                    </div>
                    {
                        !room ?
                            <div className={styles['party-body']}>
                                <label className={styles['label']}>Jouw naam</label>
                                <input
                                    className={styles['input']}
                                    value={name}
                                    placeholder={'Bijv. Maluku'}
                                    maxLength={24}
                                    onChange={(e) => setName(e.target.value)}
                                />
                                <button className={styles['primary']} onClick={party.create} disabled={!name.trim()}>
                                    Party maken
                                </button>
                                <div className={styles['divider']}>of meedoen met een code</div>
                                <form className={styles['row']} onSubmit={onJoin}>
                                    <input
                                        className={styles['input']}
                                        value={code}
                                        placeholder={'MLK-AB12'}
                                        maxLength={8}
                                        onChange={(e) => setCode(e.target.value.toUpperCase())}
                                    />
                                    <button className={styles['secondary']} type={'submit'} disabled={!name.trim() || !code.trim()}>
                                        Meedoen
                                    </button>
                                </form>
                            </div>
                            :
                            <div className={styles['party-body']}>
                                <div className={styles['code-box']}>
                                    <div className={styles['code-label']}>Party-code</div>
                                    <div className={styles['code']}>{room.code}</div>
                                    <button className={styles['secondary']} onClick={copyLink}>
                                        {copied ? 'Link gekopieerd ✓' : 'Deel link'}
                                    </button>
                                </div>
                                {!connected && <div className={styles['warning']}>Verbinding kwijt, opnieuw verbinden…</div>}
                                <div className={styles['label']}>Wie kijkt er mee</div>
                                <ul className={styles['members']}>
                                    {room.members.map((m) => (
                                        <li key={m.id}>
                                            <span className={styles['dot']} />
                                            {m.name}{m.id === room.you ? ' (jij)' : ''}{m.host ? ' 👑' : ''}
                                        </li>
                                    ))}
                                </ul>
                                <div className={styles['hint']}>
                                    {room.hostOnly ? 'Alleen de host 👑 bedient.' : 'Iedereen bladert en bedient mee.'}
                                </div>
                                {
                                    me && me.host &&
                                    <label className={styles['toggle']}>
                                        <input type={'checkbox'} checked={room.hostOnly} onChange={(e) => party.setHostOnly(e.target.checked)} />
                                        Alleen ik bedien (host-slot)
                                    </label>
                                }
                                <form className={styles['row']} onSubmit={onChat}>
                                    <input className={styles['input']} value={chat} placeholder={'Bericht of emoji…'} maxLength={300} onChange={(e) => setChat(e.target.value)} />
                                    <button className={styles['secondary']} type={'submit'}>Stuur</button>
                                </form>
                                <button className={styles['danger']} onClick={party.leave}>Party verlaten</button>
                            </div>
                    }
                </div>
            }
        </div>
    );
};

module.exports = PartyPanel;
