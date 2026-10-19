# SnipSSH: pacchetto MSIX per il Microsoft Store

Editore: flipall s.r.l. (account Partner Center registrato il 19/10/2026 con dev@flipall.it, approvato). In Partner Center il prodotto è di tipo *MSIX or PWA app*: il pacchetto viene caricato e lo firma Microsoft. *EXE or MSI app* richiederebbe un installer ospitato da noi e firmato con un certificato di firma del codice.

## Come si costruisce

Il pacchetto parte dall'app image di Compose Desktop (`createDistributable`). Il task aggiunge `AppxManifest.xml`, le icone e `resources.pri`, poi impacchetta con `makeappx` del Windows SDK.

```powershell
.\gradlew.bat :desktop:packageMsix
```

Risultato: `desktop/build/compose/binaries/main/msix/SnipSSH-<versione>.msix`, non firmato. È il file da caricare in Partner Center: lo Store lo firma con il proprio certificato.

Requisiti: JDK 17 e il Windows SDK 10 (servono `makeappx.exe`, `makepri.exe` e `signtool.exe`). Il task usa la versione più recente in `C:\Program Files (x86)\Windows Kits\10\bin`; per sceglierne un'altra, imposta `SNIPSSH_WINDOWS_SDK_BIN` con la cartella `...\bin\<versione>\x64`.

File coinvolti:

- `desktop/packaging/msix/AppxManifest.xml`: modello del manifest, con i segnaposto `@IDENTITY_NAME@`, `@PUBLISHER@`, `@PUBLISHER_DISPLAY_NAME@` e `@VERSION@`.
- `desktop/build.gradle.kts`: task `generateMsixAssets`, `generateMsixPri`, `stageMsixLayout`, `packageMsix` e `signMsixForTest`.
- Icone: generate da `docs/store/icon-512.png` in scala 100, 200 e 400, più le dimensioni fisse 16, 24, 32, 48 e 256 per la barra delle applicazioni.

## Identità del pacchetto

Valori assegnati da Partner Center (*Product management > Product identity*), già scritti come predefiniti in `desktop/build.gradle.kts`:

| Campo | Valore |
|---|---|
| `Package/Identity/Name` | `flipalls.r.l.SnipSSH` |
| `Package/Identity/Publisher` | `CN=4C7E3FE8-5D0B-4551-979A-D40C248BFFF3` |
| `Package/Properties/PublisherDisplayName` | `flipall s.r.l.` |
| Package Family Name | `flipalls.r.l.SnipSSH_eg7thp12x3t44` |
| Store ID | `9N8SQQV503LP` |
| Link | https://apps.microsoft.com/detail/9N8SQQV503LP |

Non vanno cambiati: un pacchetto con un'identità diversa viene rifiutato al caricamento. Le proprietà `SNIPSSH_MSIX_IDENTITY_NAME` e `SNIPSSH_MSIX_PUBLISHER` servono solo a sostituirli per prove locali.

La versione del pacchetto è `appPackageVersion` con `.0` in fondo (es. `0.3.5.0`). Lo Store richiede che l'ultimo numero sia 0 e che ogni invio abbia una versione più alta del precedente.

## Prova in locale

Un MSIX si installa solo se è firmato con un certificato attendibile, il cui soggetto sia uguale al Publisher del manifest.

1. Crea un certificato di prova (una volta sola):

   ```powershell
   $cert = New-SelfSignedCertificate -Type Custom -Subject "CN=4C7E3FE8-5D0B-4551-979A-D40C248BFFF3" -KeyUsage DigitalSignature `
     -FriendlyName "SnipSSH MSIX test" -CertStoreLocation "Cert:\CurrentUser\My" `
     -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3", "2.5.29.19={text}")
   Export-PfxCertificate -Cert $cert -FilePath C:\percorso\snipssh-msix-test.pfx -Password (Read-Host -AsSecureString)
   Export-Certificate -Cert $cert -FilePath C:\percorso\snipssh-msix-test.cer
   ```

2. Rendilo attendibile su questo PC, da PowerShell come amministratore:

   ```powershell
   Import-Certificate -FilePath C:\percorso\snipssh-msix-test.cer -CertStoreLocation Cert:\LocalMachine\TrustedPeople
   ```

3. Firma una copia del pacchetto e installala, dalla cartella del repository e da una PowerShell normale (non da amministratore, altrimenti l'app può finire installata per un altro utente):

   ```powershell
   .\gradlew.bat :desktop:signMsixForTest -PSNIPSSH_MSIX_TEST_CERT_FILE=C:\percorso\snipssh-msix-test.pfx -PSNIPSSH_MSIX_TEST_CERT_PASSWORD=...
   Add-AppxPackage desktop\build\compose\binaries\main\msix\SnipSSH-0.3.5-test.msix
   ```

Prova del 19/10/2026, con identità provvisoria e versione 0.3.5: il pacchetto si installa, l'app si avvia dal menu Start e legge i dati esistenti. Dall'interno del contenitore MSIX, il codice di `:core` si collega a `test.rebex.net` (verifica della chiave host, login, elenco SFTP, download di `readme.txt`, shell con `ls` ed `exit`).

Per disinstallare: `Get-AppxPackage *SnipSSH* | Remove-AppxPackage`. Al termine delle prove, togli il certificato da `Cert:\LocalMachine\TrustedPeople` e da `Cert:\CurrentUser\My`. Il file `.pfx` non va nel repository (lo esclude il `.gitignore`).

## Differenze rispetto a MSI ed EXE

- **Dati**: l'app usa sempre `%APPDATA%\SnipSSH`, ma da pacchetto MSIX Windows decide dove scrivere in base a cosa c'è già (verificato su Windows 10 22H2, 19/10/2026):
  - se la cartella esiste già, perché sul PC c'è o c'è stata la versione MSI o EXE, il pacchetto legge e scrive proprio lì. Le due versioni condividono host e snippet, e disinstallare il pacchetto non cancella niente;
  - se la cartella non esiste, come per un utente nuovo dello Store, Windows la crea nello spazio privato del pacchetto (`%LOCALAPPDATA%\Packages\<nome pacchetto>\LocalCache\Roaming\SnipSSH`). Disinstallando il pacchetto, i dati vengono cancellati: per conservarli serve il backup `.ssb`.
- **Aggiornamenti**: li distribuisce lo Store. Il `wixUpgradeUuid` e il modello WiX servono solo a MSI ed EXE.
- **Capability**: `runFullTrust`, come per tutte le app desktop Win32, e `internetClient`. In Partner Center `runFullTrust` va motivata: "Desktop app (Compose for Desktop / JVM) packaged as MSIX; it needs full trust to open SSH and SFTP connections and to read and write user-selected files".
- **Lingue**: il manifest dichiara inglese e italiano, quindi lo Store chiede una scheda per ciascuna. I testi si possono prendere da [play-store.md](play-store.md). Per pubblicare altre lingue, aggiungile in `<Resources>` nel manifest.

## Scheda store

Testi pronti da incollare in Partner Center (*Store listings*). La lingua predefinita è l'inglese; l'italiano è la seconda scheda.

**Product name:** `SnipSSH`

### Description (en-us)

```
SnipSSH is PuTTY + FileZilla for Windows, with command snippets: SSH terminal and SFTP in one app, with no subscription and no account. It is made for people who work on servers every day. The same app is available for Android, and backups move between the two.

Terminal
- SSH sessions with ANSI terminal emulation (colours, cursor, scrollback).
- Full keyboard support: arrows, F1–F12, Ctrl+letter, AltGr.
- Copy with Ctrl+Shift+C (or Ctrl+C with a selection), paste with Ctrl+Shift+V or Shift+Insert; mouse selection and wheel scrolling.
- Per-host command history: as you type, previously used commands that start the same way appear, most used first.
- Host key verification on first connection, with a warning if the server key changes.
- Font size, system font or JetBrains Mono, colour theme Dark, Light or Solarized. Ctrl+wheel to zoom.

Tabs and split view
- Shells and SFTP sessions open in tabs.
- Split view shows up to 4 tabs side by side, for example an SSH shell next to an SFTP browser, with resizable panes.

Snippets
- Save the commands you use most, with a name and variables such as {{service}}.
- Run a snippet on the server with one click and a confirmation.

Hosts
- Password or private key (OpenSSH or PEM; RSA, ECDSA, Ed25519), with an optional passphrase.
- Distribution logo and a colour for each host in the list.

SFTP
- ls -l style listing with permissions, owner, size and date.
- Double-click to open folders, upload several files at once, download with Save as, change permissions, delete files and empty folders.

Privacy and security
- Hosts, snippets and keys are stored only on this PC, encrypted with AES-256-GCM; the key is protected by Windows DPAPI.
- Encrypted backup protected by a password you choose, compatible with the Android app.
- No account, no ads, no analytics, no middle server: the connection goes from your PC directly to your server.

Languages: English, Italian, Spanish, French, German, Simplified Chinese, Russian and Korean.

Known limits: full-screen terminal programs such as vim or htop are not supported yet.
```

### Description (it-it)

```
SnipSSH è PuTTY + FileZilla per Windows, con snippet di comandi: terminale SSH e SFTP in un'unica app, senza abbonamento e senza account. È pensato per chi lavora ogni giorno sui server. La stessa app esiste per Android, e i backup passano dall'una all'altra.

Terminale
- Sessioni SSH con emulazione ANSI (colori, cursore, scroll).
- Tastiera completa: frecce, F1–F12, Ctrl+lettera, AltGr.
- Copia con Ctrl+Maiusc+C (o Ctrl+C con una selezione), incolla con Ctrl+Maiusc+V o Maiusc+Ins; selezione col mouse e rotella per scorrere.
- Cronologia dei comandi per host: mentre digiti compaiono i comandi già usati che iniziano così, dal più usato.
- Verifica delle chiavi host alla prima connessione, con avviso se la chiave del server cambia.
- Dimensione del carattere, font di sistema o JetBrains Mono, tema Scuro, Chiaro o Solarized. Ctrl+rotella per lo zoom.

Schede e vista affiancata
- Shell e sessioni SFTP si aprono in schede.
- La vista affiancata mostra fino a 4 schede una accanto all'altra, per esempio una shell SSH e una cartella SFTP, con pannelli ridimensionabili.

Snippet
- Salva i comandi che usi più spesso, con nome e variabili tipo {{servizio}}.
- Lancia uno snippet sul server con un click e conferma.

Host
- Password oppure chiave privata (OpenSSH o PEM; RSA, ECDSA, Ed25519), con passphrase opzionale.
- Logo della distribuzione e un colore per ogni host nell'elenco.

SFTP
- Elenco in stile ls -l con permessi, proprietario, dimensione e data.
- Doppio click per entrare nelle cartelle, caricamento di più file insieme, download con "Salva con nome", modifica dei permessi, eliminazione di file e cartelle vuote.

Privacy e sicurezza
- Host, snippet e chiavi restano solo su questo PC, cifrati con AES-256-GCM; la chiave è protetta da DPAPI di Windows.
- Backup cifrato con una password che scegli tu, compatibile con l'app Android.
- Nessun account, nessuna pubblicità, nessun analytics, nessun server intermedio: la connessione va dal PC direttamente al tuo server.

Lingue: inglese, italiano, spagnolo, francese, tedesco, cinese semplificato, russo e coreano.

Limiti noti: i programmi a schermo intero come vim o htop non sono ancora supportati.
```

### Product features (max 20, 200 caratteri ciascuna)

| en-us | it-it |
|---|---|
| SSH terminal with ANSI colours and full keyboard support | Terminale SSH con colori ANSI e tastiera completa |
| SFTP browser: upload, download, permissions | SFTP: caricamento, download, permessi |
| Command snippets with variables, run with one click | Snippet di comandi con variabili, lanciati con un click |
| Tabs and split view with up to 4 panes | Schede e vista affiancata fino a 4 pannelli |
| Password or private key login (RSA, ECDSA, Ed25519) | Accesso con password o chiave privata (RSA, ECDSA, Ed25519) |
| Host key verification on first connection | Verifica della chiave host alla prima connessione |
| Data encrypted on this PC, no account, no ads | Dati cifrati su questo PC, nessun account, nessuna pubblicità |
| Encrypted backup, compatible with the Android app | Backup cifrato, compatibile con l'app Android |

### Search terms (max 7)

`SSH`, `SFTP`, `terminal`, `PuTTY`, `FileZilla`, `snippets`, `server`

### Altri campi

| Campo | Valore |
|---|---|
| Copyright and trademark info | `© 2026 flipall s.r.l.` |
| Screenshots | almeno 1, PNG, 1366 x 768 o più grande, con dati fittizi come quelli del Play Store (host `db-01 staging`, `web-01 production`); da fare |
| Privacy policy URL | https://joescaramucci.github.io/snipssh/privacy.html |
| Website | https://github.com/joescaramucci/snipssh |
| Support contact info | info@flipall.it |
| Category | Developer tools |
| Pricing | Free, tutti i mercati |
| System requirements | Windows 10 versione 1809 o successiva, x64; tastiera e mouse consigliati |

## Note per la certificazione

**Notes for certification** (*Submission options*):

```
SnipSSH is an SSH and SFTP client. To test it you can use the public test server test.rebex.net:
- Add a host: hostname test.rebex.net, port 22, username demo, password password.
- Click Connect on the host row: accept the host key, the shell opens in a tab; try "ls".
- Click SFTP on the host row: the folder lists pub and readme.txt; download readme.txt with Save as.
The server is read-only, so uploads and deletes are refused by the server, not by the app.
No account or sign-in is needed. The app collects no data.
```

**Restricted capabilities, motivazione per `runFullTrust`:**

```
SnipSSH is a desktop app built with Compose for Desktop (JVM) and packaged as MSIX. It runs as a full-trust Win32 process: it opens SSH and SFTP connections to servers chosen by the user, reads and writes files the user picks for upload and download, and keeps its encrypted settings in the user's AppData folder protected with Windows DPAPI.
```

## Checklist prima dell'invio

- [x] Account Partner Center approvato.
- [x] Nome SnipSSH prenotato (Store ID `9N8SQQV503LP`), identità scritta in `desktop/build.gradle.kts`.
- [x] `packageMsix` con l'identità definitiva (`SnipSSH-0.3.5.msix`, 0.3.5.0).
- [ ] Pricing and availability: gratis, mercati.
- [ ] Properties: categoria, privacy policy, sito, contatto di supporto.
- [ ] Age ratings (questionario IARC).
- [ ] Packages: caricare `SnipSSH-0.3.5.msix`.
- [ ] Store listings in inglese e italiano, con gli screenshot.
- [ ] Submission options: note per la certificazione e motivazione per `runFullTrust`.
