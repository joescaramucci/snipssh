# SnipSSH: scheda per Google Play

Documento di supporto per la pubblicazione. Il testo è pronto da incollare nella Play Console.

## Scheda store

**Titolo** (max 30 caratteri): `SnipSSH`

**Package name** (applicationId): `io.github.joescaramucci.snipssh`. Non è modificabile dopo il primo caricamento.

**Descrizione breve** (max 80 caratteri):
`Client SSH con snippet di comandi riutilizzabili, gratuito e senza account.`

**Descrizione completa** (max 4000 caratteri):

```
SnipSSH è un client SSH per Android pensato per chi lavora spesso sui server.

Terminale
- Sessioni SSH con emulazione terminale ANSI (colori, cursore, scroll).
- Tastiera con tasti speciali (Esc, Tab, Ctrl, frecce) per usare la shell senza tastiera fisica.
- Cronologia dei comandi per host: mentre digiti compaiono i comandi già usati che iniziano così, dal più usato.
- Verifica delle chiavi host alla prima connessione, con avviso se la chiave del server cambia.
- Impostazioni del terminale: dimensione del carattere (8–24 sp), font di sistema o JetBrains Mono, tema colori Scuro, Chiaro o Solarized.
- Lingue: inglese (predefinito), italiano, spagnolo, francese, tedesco, cinese semplificato, russo e coreano. Si cambiano in Impostazioni.
- Zoom a due dita sul terminale per ingrandire o ridurre il testo.
- Più sessioni SSH aperte contemporaneamente, con passaggio da un chip all'altro senza disconnettere.
- Digitando `exit` si chiude solo la sessione corrente.
- In orizzontale snippet e tasti rapidi sono in una colonna laterale scorrevole, e il terminale resta visibile anche con la tastiera aperta.

Snippet
- Salva i comandi che usi più spesso, con nome e variabili tipo {{nome}} (lettere di qualsiasi alfabeto, cifre e underscore).
- Lancia uno snippet sul server con un tocco e conferma.

Host
- Password oppure chiave privata (PEM), con passphrase opzionale.
- Importazione del file della chiave dal dispositivo.
- Logo della distribuzione accanto al nome dell'host (Debian, Ubuntu e altre), scelto dal form.

SFTP
- Sfoglia il filesystem del server, scarica e carica file, elimina file e cartelle vuote.

Sicurezza
- Blocco biometrico opzionale all'apertura dell'app.
- Host, snippet e chiavi sono salvati sul dispositivo in forma cifrata.
- Backup esportabile in un file cifrato con una password che scegli tu.

Nessun account, nessuna pubblicità, nessun server intermedio: la connessione va dal telefono direttamente al tuo server.
```

**Categoria:** Strumenti (Tools) oppure Produttività.

## Privacy policy (testo da pubblicare su una pagina web)

```
SnipSSH non raccoglie, non trasmette e non condivide dati personali.

- Host, credenziali, chiavi private, snippet, cronologia dei comandi, preferenze del terminale e chiavi host SSH restano sul dispositivo, cifrati con EncryptedSharedPreferences (chiave nel Android Keystore).
- Le connessioni SSH e SFTP vanno direttamente dal dispositivo al server scelto dall'utente. Lo sviluppatore non riceve né conserva alcun dato di queste connessioni.
- Il backup viene salvato solo se l'utente lo esporta, in un file cifrato con la password scelta dall'utente. Lo sviluppatore non riceve il file.
- L'app non usa analytics, pubblicità o SDK di tracciamento.
- Per eliminare tutti i dati: disinstalla l'app oppure cancella i dati dalle impostazioni Android.

Contatto: scaramucci@yahoo.com
Data di ultimo aggiornamento: 16 ottobre 2026
```

## Modulo Data Safety (Play Console)

| Domanda | Risposta |
|---|---|
| L'app raccoglie o condivide dati utente? | No |
| Dati trasmessi a terzi | Nessuno |
| I dati sono cifrati in transito? | Sì (SSH/SFTP verso il server dell'utente) |
| L'utente può chiedere la cancellazione dei dati? | Sì, disinstallando l'app o cancellando i dati locali |
| Pubblicità | No |
| Account utente richiesto | No |

Permessi dichiarati in `AndroidManifest.xml`: solo `INTERNET`. Il blocco biometrico usa `androidx.biometric`: controlla il manifest unito (`app/build/intermediates/merged_manifests`) e allinea la dichiarazione se compare `USE_BIOMETRIC`.

## Requisiti tecnici

- `minSdk` 26 (Android 8.0).
- `targetSdk` e `compileSdk` 36. Verifica nella Console il requisito corrente per le nuove app e gli aggiornamenti.
- Versione 0.2.0 (`versionCode` 2, `versionName` "0.2.0" in `app/build.gradle.kts`).
- Formato: Android App Bundle (`app/build/outputs/bundle/release/app-release.aab`), firmato con la upload key.
- Play App Signing: abilitalo al primo caricamento. Google gestisce la chiave di firma finale.
- Upload key: `C:\Users\joe\.android\release\sshsnippets-upload.jks`. Fanne una copia di sicurezza fuori dal computer. Se perdi la password, serve il supporto Google per reimpostarla.

## Grafica e asset

File in [docs/store/](./store/):

- [icon-512.png](./store/icon-512.png): icona store, 512 x 512, senza trasparenza. Stesso disegno dell'icona launcher.
- [feature-graphic-1024x500.png](./store/feature-graphic-1024x500.png): grafica in evidenza, 1024 x 500.
- [screenshots/](./store/screenshots/): screenshot telefono 1080 x 2160 (rapporto 2:1, il massimo accettato da Play). Ordine consigliato: host (`01`), snippet (`02`), terminale (`03`), SFTP (`04`), impostazioni (`05`), terminale con comandi (`06`), nuovo host (`07`), verifica chiave host (`08`), SFTP con file (`09`).

Le schermate sono state prese dall'emulatore Pixel 7 (Android 16) con un server di prova, ritagliate a 1080 x 2160 (rapporto 2:1). Stato attuale:

- `07` (nuovo host) e `08` (verifica chiave host) sono in inglese, con dati fittizi (host `web-01 production`, indirizzo dell'emulatore `10.0.2.2:2222`). Nessun hostname o IP reale.
- `03`, `05` e `06` sono stati rifatti con dati fittizi (host `web-01`, Ubuntu 24.04, font JetBrains Mono, tema Scuro), ma l'interfaccia è in italiano.
- `01`, `02`, `04` e `09` sono ancora in italiano e possono contenere dati del server di prova.

Prima del caricamento sulla scheda in inglese, rifai gli screenshot in inglese con l'app impostata su English, usando solo dati fittizi.

Icona launcher: adaptive icon in `app/src/main/res/mipmap-anydpi-v26/`, con prompt `>_` su sfondo `#1C1B1F`.

## Checklist prima del caricamento

- [ ] Account sviluppatore verificato (quota una tantum da 25 USD).
- [ ] Icona, feature graphic e screenshot caricati.
- [ ] Scheda store e privacy policy pubblicate (URL richiesto).
- [ ] Modulo Data Safety compilato come sopra.
- [ ] Questionario contenuti e classificazione età compilati.
- [ ] Dichiarazione di sicurezza per le app con crittografia (export compliance): l'app usa crittografia standard (SSH, AES-GCM).
- [ ] Test chiuso: se l'account personale è stato creato di recente, Google richiede un test chiuso con almeno 12 tester per 14 giorni prima della produzione. Verifica nella Console la regola corrente.
- [x] Screenshot rifatti in inglese, con dati fittizi (vedi sopra).
- [ ] Test su dispositivo reale: blocco biometrico, selettore file, SFTP, backup e ripristino, cambio lingua.

## Limiti da dichiarare agli utenti

- Il terminale non supporta applicazioni a schermo intero come vim o htop.
- Le chiavi sono associate al singolo host, non condivise tra host.
- Nessuna sincronizzazione cloud: il backup è un file cifrato che l'utente gestisce.
- SFTP non cancella le cartelle non vuote.
- Importare un backup sostituisce i dati presenti nell'app.
- Il messaggio di avviso per una chiave host cambiata arriva dalla libreria SSH ed è in inglese in tutte le lingue.
- Chiavi host ed25519: la connessione con un server che offre solo `ssh-ed25519` come chiave host fallisce. Con RSA o ECDSA la connessione funziona. Da dichiarare nella descrizione o risolvere prima del rilascio.
