import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.json:json:20240303")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

// Le risorse dell'app Android (traduzioni, icone, font) sono la fonte unica: qui vengono copiate e usate per generare R.
val androidRes = rootProject.layout.projectDirectory.dir("app/src/main/res")
val generatedRes = layout.buildDirectory.dir("generated/androidRes/resources")
val generatedSrc = layout.buildDirectory.dir("generated/androidRes/kotlin")

val generateAndroidResources by tasks.registering {
    inputs.dir(androidRes)
    outputs.dir(generatedRes)
    outputs.dir(generatedSrc)
    doLast {
        val res = androidRes.asFile
        val outRes = generatedRes.get().asFile.apply { deleteRecursively(); mkdirs() }
        val outSrc = generatedSrc.get().asFile.apply { deleteRecursively(); mkdirs() }

        val stringsDir = outRes.resolve("strings").apply { mkdirs() }
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!.forEach { dir ->
            val file = dir.resolve("strings.xml")
            if (file.exists()) file.copyTo(stringsDir.resolve("${dir.name}.xml"), overwrite = true)
        }
        val drawables = res.resolve("drawable").listFiles { f -> f.name.startsWith("ic_distro_") }!!.sortedBy { it.name }
        val drawableDir = outRes.resolve("drawable").apply { mkdirs() }
        drawables.forEach { it.copyTo(drawableDir.resolve(it.name), overwrite = true) }
        val fontDir = outRes.resolve("font").apply { mkdirs() }
        res.resolve("font").listFiles()!!.forEach { it.copyTo(fontDir.resolve(it.name), overwrite = true) }

        val names = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(res.resolve("values/strings.xml")).getElementsByTagName("string")
            .let { list -> (0 until list.length).map { list.item(it).attributes.getNamedItem("name").nodeValue } }
            .sorted()
        val kt = buildString {
            appendLine("package com.example.sshsnippets")
            appendLine()
            appendLine("/** Generato da build.gradle.kts: ogni costante è il nome della stringa in strings.xml. */")
            appendLine("object R {")
            appendLine("    object string {")
            names.forEach { appendLine("        const val $it = \"$it\"") }
            appendLine("    }")
            appendLine("    object drawable {")
            drawables.forEach { appendLine("        const val ${it.nameWithoutExtension} = \"drawable/${it.name}\"") }
            appendLine("    }")
            appendLine("    object font {")
            res.resolve("font").listFiles()!!.sortedBy { it.name }
                .forEach { appendLine("        const val ${it.nameWithoutExtension} = \"font/${it.name}\"") }
            appendLine("    }")
            appendLine("}")
        }
        outSrc.resolve("R.kt").writeText(kt)
    }
}

// File Compose senza dipendenze Android, condivisi con l'app: si copiano qui così restano identici sulle due piattaforme.
val sharedFromApp = listOf(
    "AppThemes.kt", "TerminalThemes.kt", "Distros.kt", "OpenSessions.kt", "SessionBar.kt", "HostKeyDialog.kt", "ChmodDialog.kt",     "HostColor.kt", "Credits.kt", "SftpWidgets.kt",
)
val sharedSrc = layout.buildDirectory.dir("generated/sharedFromApp/kotlin")

val syncSharedSources by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("app/src/main/java/com/example/sshsnippets")) {
        include(sharedFromApp)
    }
    into(sharedSrc)
}

// L'icona dell'app è quella dello store: la finestra usa il PNG, l'exe e l'installer un .ico con più risoluzioni.
val storeIcon = rootProject.layout.projectDirectory.file("docs/store/icon-512.png")
val iconRes = layout.buildDirectory.dir("generated/icon/resources")
val windowsIcon = layout.buildDirectory.file("generated/icon/snipssh.ico")

val generateWindowsIcon by tasks.registering {
    inputs.file(storeIcon)
    outputs.dir(iconRes)
    outputs.file(windowsIcon)
    doLast {
        val source = ImageIO.read(storeIcon.asFile)
        iconRes.get().asFile.apply { mkdirs() }.resolve("icon.png").also { storeIcon.asFile.copyTo(it, overwrite = true) }

        val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
        val pngs = sizes.map { size ->
            val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = scaled.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(source, 0, 0, size, size, null)
            g.dispose()
            ByteArrayOutputStream().also { ImageIO.write(scaled, "png", it) }.toByteArray()
        }
        val header = ByteBuffer.allocate(6 + 16 * sizes.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(sizes.size.toShort())
        var offset = header.capacity()
        sizes.forEachIndexed { i, size ->
            header.put((if (size >= 256) 0 else size).toByte()).put((if (size >= 256) 0 else size).toByte())
            header.put(0).put(0).putShort(1).putShort(32).putInt(pngs[i].size).putInt(offset)
            offset += pngs[i].size
        }
        val out = windowsIcon.get().asFile.apply { parentFile.mkdirs() }
        out.outputStream().use { stream ->
            stream.write(header.array())
            pngs.forEach { stream.write(it) }
        }
    }
}

sourceSets.main {
    resources.srcDir(generatedRes)
    resources.srcDir(iconRes)
    kotlin.srcDir(generatedSrc)
    kotlin.srcDir(sharedSrc)
}

tasks.named("processResources") { dependsOn(generateAndroidResources, generateWindowsIcon) }
tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn(generateAndroidResources, syncSharedSources)
}
tasks.matching {
    it.name.startsWith("package") || it.name.startsWith("createDistributable") ||
        it.name.startsWith("createRuntime") || it.name.startsWith("prepareApp")
}.configureEach { dependsOn(generateWindowsIcon) }

// Compose clears jpackage's --resource-dir during packageMsi/packageExe, so the custom WiX templates
// in packaging/wix never reach jpackage that way. Instead, jpackage is run again on Compose's app image
// with our own resource folder, and the result overwrites Compose's installer in the same output folder.
val appPackageVersion = "0.3.5"
val wixUpgradeUuid = "6f1d5a52-3c1e-4c3e-9b9a-6d3b8f4a7e21"
val wixResourcesDir = layout.buildDirectory.dir("wix-resources")
val stageWixResources by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("packaging/wix"))
    include("main.wxs")
    into(wixResourcesDir)
}

fun registerJpackageTask(taskName: String, type: String) = tasks.register<Exec>(taskName) {
    dependsOn("createDistributable", stageWixResources)
    commandLine(
        File(System.getProperty("java.home"), "bin/jpackage.exe").absolutePath,
        "--type", type,
        "--app-image", layout.buildDirectory.dir("compose/binaries/main/app/SnipSSH").get().asFile.absolutePath,
        "--dest", layout.buildDirectory.dir("compose/binaries/main/$type").get().asFile.absolutePath,
        "--name", "SnipSSH",
        "--app-version", appPackageVersion,
        "--vendor", "flipall s.r.l.",
        "--description", "SSH client with SFTP and command snippets",
        "--resource-dir", wixResourcesDir.get().asFile.absolutePath,
        "--win-dir-chooser",
        "--win-per-user-install",
        "--win-menu",
        "--win-menu-group", "SnipSSH",
        "--win-upgrade-uuid", wixUpgradeUuid,
    )
}
val packageMsiWix = registerJpackageTask("packageMsiWix", "msi")
val packageExeWix = registerJpackageTask("packageExeWix", "exe")
tasks.matching { it.name == "packageMsi" }.configureEach { finalizedBy(packageMsiWix) }
tasks.matching { it.name == "packageExe" }.configureEach { finalizedBy(packageExeWix) }

// Pacchetto MSIX per il Microsoft Store. Compose Desktop non lo produce: si parte dalla sua app image (createDistributable),
// si aggiungono manifest, icone e resources.pri, e si impacchetta con makeappx del Windows SDK.
// Identity Name e Publisher li assegna Partner Center: vanno in ~/.gradle/gradle.properties o con -P, finché non sono definitivi.
val msixIdentityName = findProperty("SNIPSSH_MSIX_IDENTITY_NAME") as String? ?: "flipall.SnipSSH"
val msixPublisher = findProperty("SNIPSSH_MSIX_PUBLISHER") as String? ?: "CN=flipall s.r.l."
val msixPublisherDisplayName = "flipall s.r.l."
// Lo Store vuole quattro numeri, con l'ultimo a 0.
val msixVersion = "$appPackageVersion.0"
val msixDir = layout.buildDirectory.dir("msix")
val msixPriSource = msixDir.map { it.dir("pri-src") }
val msixPri = msixDir.map { it.file("resources.pri") }
val msixLayout = msixDir.map { it.dir("layout") }
val msixFile = layout.buildDirectory.file("compose/binaries/main/msix/SnipSSH-$appPackageVersion.msix")

fun windowsSdkTool(name: String): String {
    (findProperty("SNIPSSH_WINDOWS_SDK_BIN") as String?)?.let { return File(it, name).absolutePath }
    val bin = File(System.getenv("ProgramFiles(x86)") ?: "C:/Program Files (x86)", "Windows Kits/10/bin")
    return bin.listFiles { f -> f.isDirectory && f.name.startsWith("10.") }.orEmpty()
        .sortedByDescending { dir -> dir.name.split('.').joinToString("") { it.padStart(6, '0') } }
        .map { File(it, "x64/$name") }
        .firstOrNull { it.exists() }?.absolutePath
        ?: throw GradleException("$name non trovato: installa il Windows SDK oppure imposta SNIPSSH_WINDOWS_SDK_BIN")
}

val generateMsixAssets by tasks.registering {
    val template = layout.projectDirectory.file("packaging/msix/AppxManifest.xml")
    inputs.file(storeIcon)
    inputs.file(template)
    inputs.property("identity", listOf(msixIdentityName, msixPublisher, msixPublisherDisplayName, msixVersion))
    outputs.dir(msixPriSource)
    doLast {
        val out = msixPriSource.get().asFile.apply { deleteRecursively(); mkdirs() }
        out.resolve("AppxManifest.xml").writeText(
            template.asFile.readText()
                .replace("@IDENTITY_NAME@", msixIdentityName)
                .replace("@PUBLISHER@", msixPublisher)
                .replace("@PUBLISHER_DISPLAY_NAME@", msixPublisherDisplayName)
                .replace("@VERSION@", msixVersion)
        )

        // L'icona dello store è opaca: le tile rettangolari si riempiono con il suo colore di sfondo.
        val source = ImageIO.read(storeIcon.asFile)
        val background = Color(source.getRGB(0, 0), true)
        val assets = out.resolve("Assets").apply { mkdirs() }
        fun tile(file: String, width: Int, height: Int) {
            val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.color = background
            g.fillRect(0, 0, width, height)
            val size = minOf(width, height)
            g.drawImage(source, (width - size) / 2, (height - size) / 2, size, size, null)
            g.dispose()
            ImageIO.write(img, "png", assets.resolve(file))
        }
        for ((scale, factor) in listOf(100 to 1, 200 to 2, 400 to 4)) {
            tile("Square44x44Logo.scale-$scale.png", 44 * factor, 44 * factor)
            tile("Square150x150Logo.scale-$scale.png", 150 * factor, 150 * factor)
            tile("Wide310x150Logo.scale-$scale.png", 310 * factor, 150 * factor)
            tile("StoreLogo.scale-$scale.png", 50 * factor, 50 * factor)
        }
        // Barra delle applicazioni, menu Start ed Esplora file usano queste dimensioni fisse.
        for (size in listOf(16, 24, 32, 48, 256)) {
            tile("Square44x44Logo.targetsize-$size.png", size, size)
            tile("Square44x44Logo.targetsize-${size}_altform-unplated.png", size, size)
        }
    }
}

// resources.pri dice a Windows quale variante delle icone usare per scala e dimensione.
val generateMsixPri by tasks.registering {
    dependsOn(generateMsixAssets)
    inputs.dir(msixPriSource)
    outputs.file(msixPri)
    doLast {
        val makepri = windowsSdkTool("makepri.exe")
        val config = msixDir.get().file("priconfig.xml").asFile.absolutePath
        val source = msixPriSource.get().asFile
        exec { commandLine(makepri, "createconfig", "/cf", config, "/dq", "en-US", "/pv", "10.0.0", "/o") }
        exec {
            commandLine(
                makepri, "new", "/pr", source.absolutePath, "/cf", config,
                "/mn", source.resolve("AppxManifest.xml").absolutePath,
                "/of", msixPri.get().asFile.absolutePath, "/o",
            )
        }
    }
}

val stageMsixLayout by tasks.registering(Sync::class) {
    dependsOn("createDistributable", generateMsixPri)
    from(layout.buildDirectory.dir("compose/binaries/main/app/SnipSSH"))
    from(msixPriSource)
    from(msixPri)
    into(msixLayout)
}

// Pacchetto non firmato, da caricare in Partner Center: lo Store lo firma con il certificato di flipall s.r.l.
val packageMsix by tasks.registering {
    group = "compose desktop"
    description = "Builds the unsigned MSIX package for the Microsoft Store."
    dependsOn(stageMsixLayout)
    outputs.file(msixFile)
    doLast {
        val out = msixFile.get().asFile.apply { parentFile.mkdirs() }
        exec {
            commandLine(windowsSdkTool("makeappx.exe"), "pack", "/d", msixLayout.get().asFile.absolutePath, "/p", out.absolutePath, "/o")
        }
    }
}

// Copia firmata con un certificato di prova, per installare il pacchetto su questo PC prima dell'invio.
// Il soggetto del certificato deve essere uguale a SNIPSSH_MSIX_PUBLISHER.
val signMsixForTest by tasks.registering {
    group = "compose desktop"
    description = "Signs a copy of the MSIX package with a test certificate for local installation."
    dependsOn(packageMsix)
    doLast {
        val cert = findProperty("SNIPSSH_MSIX_TEST_CERT_FILE") as String?
            ?: throw GradleException("Imposta SNIPSSH_MSIX_TEST_CERT_FILE (file .pfx) e SNIPSSH_MSIX_TEST_CERT_PASSWORD")
        val password = findProperty("SNIPSSH_MSIX_TEST_CERT_PASSWORD") as String?
        val unsigned = msixFile.get().asFile
        val signed = unsigned.resolveSibling("SnipSSH-$appPackageVersion-test.msix")
        unsigned.copyTo(signed, overwrite = true)
        exec {
            commandLine(
                listOf(windowsSdkTool("signtool.exe"), "sign", "/fd", "SHA256", "/f", cert) +
                    (if (password != null) listOf("/p", password) else emptyList()) +
                    signed.absolutePath
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.sshsnippets.DesktopMainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            // jdk.crypto.ec fornisce le curve ellittiche usate da SSH: jlink non lo include da solo.
            modules("java.instrument", "jdk.unsupported", "jdk.crypto.ec", "java.naming")
            packageName = "SnipSSH"
            packageVersion = appPackageVersion
            description = "SSH client with SFTP and command snippets"
            vendor = "flipall s.r.l."
            windows {
                menuGroup = "SnipSSH"
                iconFile.set(windowsIcon)
                upgradeUuid = wixUpgradeUuid
                perUserInstall = true
                dirChooser = true
            }
        }
    }
}
