# Stremio Party — samen kijken op afstand

Een eigen versie van Stremio Web met een **🎉 Party**-knop:

- **Party maken** → je krijgt een code zoals `MLK-AB12` of een deel-link
- **Samen bladeren**: opent iemand een film, zoekt hij iets of gaat hij naar Ontdekken, dan zien de anderen dat meteen
- **Gedeelde afstandsbediening**: pauze, play en spoelen bij één persoon = bij iedereen
- **Laatkomers** springen automatisch naar de juiste plek in de film
- **Gelijk blijven lopen**: loopt iemand achter (bijvoorbeeld door haperen), dan trekt de app hem vanzelf gelijk
- **Host-slot** 👑: de host kan de bediening even alleen voor zichzelf houden
- **Chat**: korte berichtjes of emoji's die bij iedereen in beeld komen

Je add-ons (zoals Torrentio) werken gewoon. Iedereen logt in op zijn eigen Stremio-account en installeert zelf de add-ons. Als de host een stream kiest, opent die bij iedereen dezelfde stream.

Instellingen, Add-ons en Bibliotheek blijven persoonlijk en worden niet gedeeld.

---

## Online zetten (eenmalig, ongeveer 15 minuten, gratis)

### Stap 1 — Code op GitHub zetten
1. Maak een gratis account aan op github.com.
2. Maak een nieuwe repository, bijvoorbeeld `stremio-party`.
3. Upload deze map naar die repository.

### Stap 2 — De party-server online zetten (Render)
1. Maak een gratis account aan op render.com en log in met GitHub.
2. Kies **New → Blueprint** en dan je repository. Render leest `render.yaml` en zet de server klaar.
3. Na een paar minuten krijg je een adres zoals `https://party-server-xxxx.onrender.com`.
4. Open dat adres. Zie je `party server ok`, dan werkt hij.

> Let op: de gratis server gaat slapen als hij 15 minuten niet gebruikt wordt. De eerste keer "Party maken" kan dan ongeveer 30 seconden duren.

### Stap 3 — De app online zetten (GitHub Pages)
1. Ga in je repository naar **Settings → Secrets and variables → Actions → Variables** en kies **New variable**.
   - Naam: `PARTY_SERVER_URL`
   - Waarde: je Render-adres, maar met `wss://` in plaats van `https://`. Bijvoorbeeld `wss://party-server-xxxx.onrender.com`
2. Ga naar **Settings → Pages** en kies bij *Source* voor **GitHub Actions**.
3. Ga naar **Actions → "Bouw en zet online" → Run workflow**.
4. Na ongeveer 3 minuten staat je app op `https://<jouw-naam>.github.io/stremio-party/`.

### Stap 4 — Kijken
- Open de link op je telefoon of pc. Op je telefoon kun je via *Toevoegen aan beginscherm* er een app van maken.
- Druk op **🎉 Party**, vul je naam in en kies **Party maken**. Deel de link of de code.
- Voor torrents (bijvoorbeeld Torrentio) heeft iedereen de **Stremio-streamingserver** nodig, net als bij web.stremio.com. Een debrid-dienst werkt zonder die server.

---

## Testen zonder internet-hosting (op je eigen pc)
```
cd party-server && npm install && npm start      # server op poort 8787
pnpm install && pnpm start                        # app op https://localhost:8080
```

## Wat is er veranderd ten opzichte van Stremio Web
- `src/party/`: de party-functie (verbinding, paneel, sync)
- `src/App/App.js`: het Party-paneel is toegevoegd
- `src/routes/Player/Player.js`: één regel om de speler te koppelen
- `webpack.config.js`: de instelling `PARTY_SERVER_URL`
- `party-server/`: de sync-server
- `.github/workflows/party-deploy.yml`: bouwen en online zetten

Gebaseerd op Stremio Web (GPLv2). Deze versie valt daarom ook onder GPLv2.
