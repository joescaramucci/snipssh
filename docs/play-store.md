# SnipSSH: scheda per Google Play

Documento di supporto per la pubblicazione. Il testo è pronto da incollare nella Play Console.

## Scheda store

**Titolo** (max 30 caratteri): `SnipSSH`

**Package name** (applicationId): `io.github.joescaramucci.snipssh`. Non è modificabile dopo il primo caricamento.

**Descrizione breve** (max 80 caratteri):
`Terminale SSH e SFTP con snippet, per Android e Windows. Gratis, senza account.`

**Descrizione completa** (max 4000 caratteri):

```
SnipSSH è un terminale SSH e un file manager SFTP in un'unica app, con snippet di comandi e senza abbonamento. È pensato per chi lavora spesso sui server. La versione per Windows ha le stesse funzioni e si scarica dal repository GitHub.

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

URL pubblico (EN e IT): https://joescaramucci.github.io/snipssh/privacy.html

Pubblicata con GitHub Pages dal branch `gh-pages`, che contiene solo la pagina della privacy. Il resto di `docs/` non viene pubblicato. Per aggiornare il testo, modifica `privacy.html` nel branch `gh-pages`.

```
SnipSSH non raccoglie, non trasmette e non condivide dati personali.

- Host, credenziali, chiavi private, snippet, cronologia dei comandi, preferenze del terminale e chiavi host SSH restano sul dispositivo. Su Android sono cifrati con EncryptedSharedPreferences (chiave nel Android Keystore). Su Windows sono salvati nella cartella %APPDATA%\SnipSSH, cifrati con AES-256-GCM, con la chiave protetta da DPAPI di Windows.
- Le connessioni SSH e SFTP vanno direttamente dal dispositivo al server scelto dall'utente. Lo sviluppatore non riceve né conserva alcun dato di queste connessioni.
- Il backup viene salvato solo se l'utente lo esporta, in un file cifrato con la password scelta dall'utente. Lo sviluppatore non riceve il file.
- L'app non usa analytics, pubblicità o SDK di tracciamento.
- Per eliminare tutti i dati: disinstalla l'app oppure cancella i dati dalle impostazioni Android. Su Windows, elimina anche la cartella %APPDATA%\SnipSSH.

Titolare: flipall s.r.l.
Contatto: info@flipall.it
Data di ultimo aggiornamento: 17 ottobre 2026
```

## Scheda store in inglese (en-US, lingua predefinita)

**Title:** `SnipSSH`

**Short description** (max 80 characters):
`SSH terminal and SFTP with snippets, for Android and Windows. Free, no account.`

**Full description** (max 4000 characters):

```
SnipSSH is an SSH terminal and an SFTP file manager in one app, with command snippets and no subscription. It is made for people who work on servers every day. The Windows version has the same features and is available from the GitHub repository.

Terminal
- SSH sessions with ANSI terminal emulation (colours, cursor, scrollback).
- Extra keys (Esc, Tab, Ctrl, arrows) so you can use the shell without a hardware keyboard.
- Per-host command history: as you type, previously used commands that start the same way appear, most used first.
- Host key verification on first connection, with a warning if the server key changes.
- Terminal settings: font size (8–24 sp), system font or JetBrains Mono, colour theme Dark, Light or Solarized.
- Languages: English (default), Italian, Spanish, French, German, Simplified Chinese, Russian and Korean. Change them in Settings.
- Pinch to zoom on the terminal to enlarge or shrink the text.
- Several SSH sessions open at once, switching between them with a tap, without disconnecting.
- Typing `exit` closes only the current session.
- In landscape, snippets and quick keys are in a scrollable side column, and the terminal stays visible with the keyboard open.

Snippets
- Save the commands you use most, with a name and variables such as {{name}} (letters of any alphabet, digits and underscores).
- Run a snippet on the server with one tap and a confirmation.

Hosts
- Password or private key (PEM), with an optional passphrase.
- Import a key file from the device.
- Distribution logo next to the host name (Debian, Ubuntu and others), chosen in the form.

SFTP
- Browse the server filesystem, download and upload files, delete files and empty folders.

Security
- Optional biometric lock when the app opens.
- Hosts, snippets and keys are stored on the device in encrypted form.
- Backup you can export to an encrypted file protected by a password you choose.

No account, no ads, no middle server: the connection goes from your phone directly to your server.
```

## Privacy policy in inglese (per la scheda en-US)

```
SnipSSH does not collect, transmit or share personal data.

- Hosts, credentials, private keys, snippets, command history, terminal preferences and SSH host keys stay on the device. On Android they are encrypted with EncryptedSharedPreferences (key kept in the Android Keystore). On Windows they are stored in the %APPDATA%\SnipSSH folder, encrypted with AES-256-GCM, with the key protected by Windows DPAPI.
- SSH and SFTP connections go directly from the device to the server chosen by the user. The developer does not receive or store any data from these connections.
- A backup is saved only if the user exports it, as a file encrypted with the password the user chooses. The developer does not receive the file.
- The app uses no analytics, advertising or tracking SDKs.
- To delete all data: uninstall the app, or clear its data from Android settings. On Windows, also delete the %APPDATA%\SnipSSH folder.

Controller: flipall s.r.l.
Contact: info@flipall.it
Last updated: 17 October 2026
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
- Versione 0.3.5 (`versionCode` 8, `versionName` "0.3.5" in `app/build.gradle.kts`).
- Formato: Android App Bundle (`app/build/outputs/bundle/release/app-release.aab`), firmato con la upload key.
- Play App Signing: abilitalo al primo caricamento. Google gestisce la chiave di firma finale.
- Upload key: `C:\Users\joe\.android\release\sshsnippets-upload.jks`. Fanne una copia di sicurezza fuori dal computer. Se perdi la password, serve il supporto Google per reimpostarla.

## Grafica e asset

File in [docs/store/](./store/):

- [icon-512.png](./store/icon-512.png): icona store, 512 x 512, senza trasparenza. Stesso disegno dell'icona launcher.
- [feature-graphic-1024x500.png](./store/feature-graphic-1024x500.png): grafica in evidenza, 1024 x 500.
- [screenshots/](./store/screenshots/): screenshot telefono 1080 x 2160 (rapporto 2:1, il massimo accettato da Play). Ordine consigliato: host (`01`), snippet (`02`), terminale (`03`), SFTP (`04`), impostazioni (`05`), terminale con comandi (`06`), nuovo host (`07`), verifica chiave host (`08`), SFTP con file (`09`).

Le schermate sono state prese dall'emulatore Pixel 7 (Android 16) con un server di prova, ritagliate a 1080 x 2160 (rapporto 2:1). Tutte e 9 sono in inglese, con l'app impostata su English, tema app Ocean, tema terminale Solarized e dati fittizi (host `db-01 staging` e `web-01 production`, indirizzo dell'emulatore `10.0.2.2:2222`). Nessun hostname o IP reale.

Icona launcher: adaptive icon in `app/src/main/res/mipmap-anydpi-v26/`, con prompt `>_` su sfondo `#1C1B1F`.

## Stato della pubblicazione

Inviata in revisione il 19/10/2026 con l'account di flipall s.r.l. e pubblicata su Google Play il 20/10/2026: https://play.google.com/store/apps/details?id=io.github.joescaramucci.snipssh. Il revisore usa il server di prova `test.rebex.net`.

## Checklist prima del caricamento

- [x] Account sviluppatore verificato e quota una tantum pagata (già fatto con l'app beep; nome sviluppatore: flipall s.r.l., da verificare nella Console).
- [x] Icona, feature graphic e screenshot caricati.
- [x] Privacy policy pubblicata: https://joescaramucci.github.io/snipssh/privacy.html
- [x] Scheda store compilata nella Console con il testo in inglese sopra.
- [x] Modulo Data Safety compilato come sopra.
- [x] Questionario contenuti e classificazione età compilati.
- [x] Dichiarazione di sicurezza per le app con crittografia (export compliance): l'app usa crittografia standard (SSH, AES-GCM).
- [x] Test chiuso con 12 tester non richiesto: l'account Play è aziendale (flipall s.r.l.), non personale.
- [x] Screenshot rifatti in inglese, con dati fittizi (vedi sopra).
- [x] Test su dispositivo reale: blocco biometrico, selettore file, SFTP, backup e ripristino, cambio lingua.

## Limiti da dichiarare agli utenti

- Il terminale non supporta applicazioni a schermo intero come vim o htop.
- Le chiavi sono associate al singolo host, non condivise tra host.
- Nessuna sincronizzazione cloud: il backup è un file cifrato che l'utente gestisce.
- SFTP non cancella le cartelle non vuote.
- Importare un backup sostituisce i dati presenti nell'app.
- Il messaggio di avviso per una chiave host cambiata arriva dalla libreria SSH ed è in inglese in tutte le lingue.
- Chiavi host ed25519 supportate tramite BouncyCastle (anche su Android 8–12). Il supporto ed25519 è stato verificato con un server di prova che offre solo `ssh-ed25519`; resta da verificare su un server Linux reale.
