import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
val appPackageVersion = "0.3.4"
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
        "--vendor", "flipall srl",
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
            vendor = "flipall srl"
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
