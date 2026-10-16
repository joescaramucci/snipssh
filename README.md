# SnipSSH

Client SSH per Android con gestione di snippet di comandi riutilizzabili. Nessun account, nessuna pubblicità, nessun server intermedio: la connessione va dal telefono direttamente al tuo server.

## Funzionalità

- **Terminale SSH** con emulazione ANSI (colori, cursore, scroll) e tasti speciali (Esc, Tab, Ctrl, frecce).
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
- Le chiavi sono associate al singolo host, non condivise.
- Nessuna sincronizzazione cloud: il backup è un file che gestisci tu.
- SFTP non cancella le cartelle non vuote.
- Importare un backup sostituisce i dati presenti nell'app.

## Documentazione

- [docs/play-store.md](docs/play-store.md): scheda Google Play, privacy policy, modulo Data Safety, checklist di pubblicazione.
- [docs/store/](docs/store/): icona, feature graphic e screenshot per lo store.

## Struttura

```
app/src/main/java/com/example/sshsnippets/
  MainActivity.kt      schede Host / Snippet / Impostazioni
  SshSession.kt        connessione SSH, verifica chiave host
  TerminalScreen.kt    terminale e tasti rapidi
  AnsiTerminal.kt      emulatore ANSI
  Sftp.kt              client e schermata SFTP
  Store.kt, Models.kt  archiviazione cifrata e modelli
  HostKeys.kt          chiavi host note (TOFU)
  AppLock.kt           blocco biometrico
  Backup.kt            export / import cifrato
```

## Licenza

Da definire.
