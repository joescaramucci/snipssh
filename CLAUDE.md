# CLAUDE.md

SnipSSH: client SSH/SFTP con snippet di comandi, per Android e Windows. Kotlin, Compose (Android) e Compose Desktop (Windows), un solo progetto Gradle.

## Build

Serve JDK 17; per Android anche l'SDK con platform 36. Non ci sono test automatici: si verifica compilando ed eseguendo l'app.

```powershell
.\gradlew.bat :app:assembleDebug        # APK di debug
.\gradlew.bat :app:installDebug         # installa su dispositivo/emulatore
.\gradlew.bat :app:bundleRelease        # AAB firmato (serve la firma, vedi sotto)
.\gradlew.bat :desktop:run              # avvia l'app Windows
.\gradlew.bat :desktop:createDistributable              # cartella portabile
.\gradlew.bat :desktop:packageMsi :desktop:packageExe   # installer (servono jpackage e WiX 3.14 nel PATH)
.\gradlew.bat :desktop:packageMsix                      # MSIX per il Microsoft Store (serve il Windows SDK)
```

Dettagli sul pacchetto MSIX, l'identità di Partner Center e la prova in locale in [docs/microsoft-store.md](docs/microsoft-store.md).

## Moduli e codice condiviso

- `:core` (Kotlin/JVM puro): SSH, SFTP, emulatore ANSI, chiavi host, modelli, backup. Niente dipendenze Android.
- `:app` (Android) e `:desktop` (Windows) dipendono da `:core`.
- Alcuni file Compose stanno in `app/src/main/java/com/example/sshsnippets/` e `desktop/build.gradle.kts` li copia nel desktop a ogni build (lista `sharedFromApp`). Si modificano solo in `app/`. Devono restare senza dipendenze Android; se ne aggiungi uno, aggiornane la lista.
- Le risorse del desktop (stringhe, loghi `ic_distro_*`, font) vengono generate da `app/src/main/res/`, compreso un oggetto `R` sintetico. Non duplicarle in `desktop/`. L'icona Windows deriva da `docs/store/icon-512.png`.
- Stringhe: inglese in `values/`, poi `values-it`, `-es`, `-fr`, `-de`, `-zh-rCN`, `-ru`, `-ko`. Una stringa nuova va aggiunta in tutte e 8 le lingue.

## Cambio di versione

La versione è scritta in più punti, da tenere allineati:

- `app/build.gradle.kts`: `appVersionName` e `versionCode` (va sempre incrementato).
- `app/src/main/java/com/example/sshsnippets/Credits.kt`: `APP_VERSION`.
- `desktop/build.gradle.kts`: `appPackageVersion`. Senza un cambio di versione l'MSI non si installa sopra quello precedente (errore 1638). Non cambiare `wixUpgradeUuid`.
- `README.md`: link di download e tabella delle specifiche.
- `docs/play-store.md`: sezione "Requisiti tecnici".

## Vincoli

- `applicationId` è `io.github.joescaramucci.snipssh`: non si può cambiare, l'app è su Google Play. Il `namespace`/package Kotlin resta `com.example.sshsnippets`.
- Firma della release: le proprietà `SSHSNIPPETS_UPLOAD_*` stanno in `~/.gradle/gradle.properties`. Chiavi (`*.jks`, `*.keystore`) e password non vanno mai nel repository.
- La privacy policy pubblicata è nel branch `gh-pages` (`privacy.html`), non in `master`. Se cambia il testo in `docs/play-store.md`, va aggiornata anche quella.
- Titolare: flipall s.r.l.

## Convenzioni

- README, `docs/` e commenti nel codice sono in italiano; i messaggi di commit sono in inglese, con titolo all'imperativo.
- Le modifiche visibili all'utente vanno descritte nel README (sezioni Funzionalità, Versione Windows o Limiti noti).

## Stato

- Google Play: app pubblicata il 20/10/2026 (account flipall s.r.l.): https://play.google.com/store/apps/details?id=io.github.joescaramucci.snipssh. Il revisore usa il server di prova `test.rebex.net`. Dettagli e checklist in [docs/play-store.md](docs/play-store.md).
- Microsoft Store: account sviluppatore flipall s.r.l. (email dev@flipall.it), prodotto "MSIX or PWA app" con Store ID `9N8SQQV503LP`. Versione 0.3.5 pubblicata il 20/10/2026 (https://apps.microsoft.com/detail/9N8SQQV503LP), dopo un primo rifiuto per le parole chiave PuTTY e FileZilla (regola 10.1.3): nei testi degli store non si citano prodotti di altri. Il revisore usa `test.rebex.net`. Dettagli in [docs/microsoft-store.md](docs/microsoft-store.md).
