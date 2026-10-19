# SnipSSH: pacchetto MSIX per il Microsoft Store

Editore: flipall s.r.l. (account Partner Center registrato il 19/10/2026 con dev@flipall.it, in attesa di approvazione).

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

Identity Name e Publisher devono coincidere con quelli di Partner Center, alla pagina *Gestione prodotto > Identità prodotto*, dopo aver prenotato il nome SnipSSH. Finché non ci sono, il build usa valori provvisori:

| Proprietà Gradle | Valore provvisorio | Da Partner Center |
|---|---|---|
| `SNIPSSH_MSIX_IDENTITY_NAME` | `flipall.SnipSSH` | `Package/Identity/Name` |
| `SNIPSSH_MSIX_PUBLISHER` | `CN=flipall s.r.l.` | `Package/Identity/Publisher` (un `CN=` con un GUID) |

Si passano con `-P` oppure in `~/.gradle/gradle.properties`. Quando sono definitivi, si possono scrivere come valori predefiniti in `desktop/build.gradle.kts`: non sono segreti.

La versione del pacchetto è `appPackageVersion` con `.0` in fondo (es. `0.3.5.0`). Lo Store richiede che l'ultimo numero sia 0 e che ogni invio abbia una versione più alta del precedente.

## Prova in locale

Un MSIX si installa solo se è firmato con un certificato attendibile, il cui soggetto sia uguale al Publisher del manifest.

1. Crea un certificato di prova (una volta sola):

   ```powershell
   $cert = New-SelfSignedCertificate -Type Custom -Subject "CN=flipall s.r.l." -KeyUsage DigitalSignature `
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

## Checklist prima dell'invio

- [ ] Account Partner Center approvato.
- [ ] Nome SnipSSH prenotato, Identity Name e Publisher copiati nelle proprietà Gradle.
- [ ] `packageMsix` con l'identità definitiva, e prova in locale della copia firmata: avvio, connessione SSH, SFTP con caricamento e download, backup e ripristino, cambio lingua.
- [ ] Scheda store in inglese e italiano, screenshot desktop (almeno 1366 x 768), icona.
- [ ] Privacy policy: https://joescaramucci.github.io/snipssh/privacy.html
- [ ] Classificazione età (questionario IARC).
- [ ] Motivazione per `runFullTrust`.
