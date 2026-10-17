# SnipSSH

Client SSH per Android con gestione di snippet di comandi riutilizzabili. Nessun account, nessuna pubblicità, nessun server intermedio: la connessione va dal telefono direttamente al tuo server.

## Screenshot

<table>
  <tr>
    <td><img src="docs/store/screenshots/01-hosts.png" width="220" alt="Lista host"></td>
    <td><img src="docs/store/screenshots/02-snippet.png" width="220" alt="Snippet"></td>
    <td><img src="docs/store/screenshots/03-terminale.png" width="220" alt="Terminale"></td>
  </tr>
  <tr>
    <td><img src="docs/store/screenshots/04-sftp.png" width="220" alt="SFTP"></td>
    <td><img src="docs/store/screenshots/05-impostazioni.png" width="220" alt="Impostazioni"></td>
    <td><img src="docs/store/screenshots/06-terminale-comandi.png" width="220" alt="Terminale con comandi"></td>
  </tr>
  <tr>
    <td><img src="docs/store/screenshots/07-nuovo-host.png" width="220" alt="Nuovo host"></td>
    <td><img src="docs/store/screenshots/08-verifica-chiave-host.png" width="220" alt="Verifica chiave host"></td>
    <td><img src="docs/store/screenshots/09-sftp.png" width="220" alt="SFTP con file"></td>
  </tr>
</table>

## Funzionalità

- **Terminale SSH** con emulazione ANSI (colori, cursore, scroll) e tasti speciali (Esc, Tab, Ctrl, frecce).
- **Lingue**: inglese (predefinito), italiano, spagnolo, francese, tedesco, cinese semplificato, russo e coreano. Si scelgono in Impostazioni e l'app si aggiorna subito.
- **Aspetto del terminale** in Impostazioni: dimensione del carattere (8–24 sp), font di sistema o JetBrains Mono, tema colori Scuro, Chiaro o Solarized. Con due dita sul terminale si ingrandisce o riduce il testo, e la dimensione viene salvata.
- **Sessioni multiple**: puoi tenere aperte più connessioni contemporaneamente. Ogni sessione è un chip in alto nel terminale: tocca il nome per passare da una all'altra senza disconnettere le altre. `+ Host` apre un'altra connessione, la × chiude la sessione.
- **Uscita dalla sessione**: digitando `exit` si chiude solo la sessione corrente. Le altre restano aperte, e chiudendo l'ultima si torna alla lista host.
- **Orizzontale**: con il telefono in orizzontale la colonna destra mostra host, snippet e tasti rapidi, scorrevole; il terminale occupa la parte sinistra, con la riga di input compatta.
- **Logo della distribuzione** accanto al nome dell'host (Debian, Ubuntu, Linux Mint, Fedora, Red Hat, Arch, Alpine, Windows, macOS, FreeBSD o generico), scelto nel form dell'host.
- **Cronologia dei comandi** per host: mentre digiti (es. `cd`) compaiono i comandi già usati che iniziano così, dal più usato al meno usato.
- **Snippet** con nome e variabili tipo `{{servizio}}`, lanciabili sul server con un tocco e conferma.
- **Host** con autenticazione a password oppure chiave privata (PEM), con passphrase opzionale.
- **Verifica delle chiavi host** alla prima connessione, con avviso se la chiave del server cambia.
- **SFTP**: sfoglia, scarica, carica, elimina file e cartelle vuote.
- **Sicurezza**: blocco biometrico opzionale; dati locali cifrati con `EncryptedSharedPreferences`.
- **Backup** esportabile in un file cifrato con una password scelta dall'utente.

## Requisiti

- Android 8.0 (API 26) o successivo.
- Per compilare: JDK 17 e Android SDK con platform 36.

## Build

```powershell
.\gradlew.bat :app:assembleDebug        # Windows
./gradlew :app:assembleDebug            # Linux / macOS
```

Per installare sul dispositivo o sull'emulatore collegato:

```powershell
.\gradlew.bat :app:installDebug
```

### Release firmata

La firma è configurata in `app/build.gradle.kts` e legge queste proprietà da `gradle.properties` globale (`~/.gradle/gradle.properties`) oppure da una riga di comando, mai dal repository:

```properties
SSHSNIPPETS_UPLOAD_STORE_FILE=C:/percorso/sshsnippets-upload.jks
SSHSNIPPETS_UPLOAD_STORE_PASSWORD=...
SSHSNIPPETS_UPLOAD_KEY_ALIAS=upload
SSHSNIPPETS_UPLOAD_KEY_PASSWORD=...
```

Poi:

```powershell
.\gradlew.bat :app:bundleRelease        # app/build/outputs/bundle/release/app-release.aab
```

Le chiavi di firma (`*.jks`, `*.keystore`) e le password non devono mai finire nel repository: il `.gitignore` le esclude.

## Limiti noti

- Il terminale non supporta applicazioni a schermo intero come `vim` o `htop`.
- Server che offrono solo chiavi host `ssh-ed25519` non si connettono. RSA ed ECDSA funzionano.
- Le variabili degli snippet accettano lettere di qualsiasi alfabeto, cifre e `_` (`{{servizio}}`, `{{città}}` o `{{имя}}` sì; spazi e simboli no).
- Le chiavi sono associate al singolo host, non condivise.
- Nessuna sincronizzazione cloud: il backup è un file che gestisci tu.
- SFTP non cancella le cartelle non vuote.
- Importare un backup sostituisce i dati presenti nell'app.

## Documentazione

- [docs/play-store.md](docs/play-store.md): scheda Google Play, privacy policy, modulo Data Safety, checklist di pubblicazione.
- [Privacy policy](https://joescaramucci.github.io/snipssh/privacy.html) (EN e IT).
- [docs/store/](docs/store/): icona, feature graphic e screenshot per lo store.

## Struttura

```
app/src/main/java/com/example/sshsnippets/
  MainActivity.kt      schede Host / Snippet / Impostazioni
  Locales.kt           lingue disponibili e lingua predefinita
  SshSession.kt        connessione SSH, verifica chiave host
  TerminalScreen.kt    terminale e tasti rapidi
  TerminalThemes.kt    temi, font e dimensione del terminale
  AnsiTerminal.kt      emulatore ANSI
  Sftp.kt              client e schermata SFTP
  Store.kt, Models.kt  archiviazione cifrata e modelli
  HostKeys.kt          chiavi host note (TOFU)
  AppLock.kt           blocco biometrico
  Backup.kt            export / import cifrato

app/src/main/res/values*/strings.xml   testi (inglese predefinito, poi it, es, fr, de, zh-rCN, ru, ko)
```

## Licenza

[MIT](LICENSE)

Il font JetBrains Mono (`app/src/main/res/font/`) è distribuito con licenza SIL Open Font License 1.1, il cui testo è in [docs/fonts/JetBrainsMono-OFL.txt](docs/fonts/JetBrainsMono-OFL.txt).

I loghi delle distribuzioni (`app/src/main/res/drawable/ic_distro_*.xml`) provengono da [Simple Icons](https://simpleicons.org) (licenza CC0 1.0). I marchi (Debian, Ubuntu, Linux Mint, Fedora, Red Hat, Arch Linux, Alpine Linux, Apple/macOS, FreeBSD, Tux/Linux) restano di proprietà dei rispettivi titolari e sono usati solo per identificare il sistema operativo. Il logo Microsoft Windows non è disponibile in Simple Icons, quindi viene usata un'icona generica di monitor creata per questo progetto.