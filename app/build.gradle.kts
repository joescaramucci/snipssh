import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.time.Year
import javax.imageio.ImageIO

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Shown in the splash credits image as well; keep in sync with APP_VERSION in Credits.kt and packageVersion in desktop/build.gradle.kts.
val appVersionName = "0.3.4"

android {
    namespace = "com.example.sshsnippets"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.joescaramucci.snipssh"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = appVersionName
    }

    signingConfigs {
        create("upload") {
            val file = findProperty("SSHSNIPPETS_UPLOAD_STORE_FILE") as String?
            if (file != null) {
                storeFile = file(file)
                storePassword = findProperty("SSHSNIPPETS_UPLOAD_STORE_PASSWORD") as String?
                keyAlias = findProperty("SSHSNIPPETS_UPLOAD_KEY_ALIAS") as String?
                keyPassword = findProperty("SSHSNIPPETS_UPLOAD_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
    release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (findProperty("SSHSNIPPETS_UPLOAD_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1,INDEX.LIST,versions/**}" }
}

dependencies {
    implementation(project(":core"))
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.github.mwiede:jsch:2.28.8")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
}

// Crediti sulla splash di sistema (Android 12+): l'anno viene rigenerato a ogni build.
val splashCreditsDir = layout.buildDirectory.dir("generated/splash/res")

val generateSplashCredits by tasks.registering {
    outputs.dir(splashCreditsDir)
    outputs.upToDateWhen { false }
    doLast {
        System.setProperty("java.awt.headless", "true")
        val year = Year.now().value
        val copy = "\u00a9"
        val reg = "\u00ae"
        val dot = "\u00b7"
        val color = Color(190, 190, 190)
        val frc = FontRenderContext(null, true, true)
        val baseSize = 44f
        val baseFont = Font("SansSerif", Font.PLAIN, 44)
        val smallFont = baseFont.deriveFont(37f)

        // Il simbolo ® ha un disegno più piccolo di ©: lo scalo fino a eguagliarne la dimensione.
        val copyBounds = baseFont.createGlyphVector(frc, copy).visualBounds
        val regBounds = baseFont.createGlyphVector(frc, reg).visualBounds
        val scale = ((copyBounds.width / regBounds.width) + (copyBounds.height / regBounds.height)) / 2
        val regFont = baseFont.deriveFont((baseSize * scale).toFloat())
        val regScaled = regFont.createGlyphVector(frc, reg).visualBounds
        val regDy = copyBounds.centerY - regScaled.centerY

        val width = 1500
        val height = 300
        val base = 100
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = color

        val line1 = listOf(
            Triple(copy, baseFont, 0.0),
            Triple(reg, regFont, regDy),
            Triple(" $year flipall srl $dot Italy", baseFont, 0.0),
        )
        var x = (width - line1.sumOf { (text, font, _) -> g.getFontMetrics(font).stringWidth(text) }) / 2.0
        for ((text, font, dy) in line1) {
            g.font = font
            g.drawString(text, x.toFloat(), (base + dy).toFloat())
            x += g.getFontMetrics(font).stringWidth(text)
        }

        val line2 = "Joe Scaramucci $dot info@flipall.it $dot v.$appVersionName"
        g.font = smallFont
        val w2 = g.getFontMetrics(smallFont).stringWidth(line2)
        g.drawString(line2, ((width - w2) / 2).toFloat(), (base + 58).toFloat())
        g.dispose()

        // Ritaglio al contenuto visibile, così la riga non ha margini vuoti.
        var minX = width; var minY = height; var maxX = -1; var maxY = -1
        for (py in 0 until height) for (px in 0 until width) {
            if ((img.getRGB(px, py) ushr 24) != 0) {
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
            }
        }
        val cropped = img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1)

        // La splash di Android 12 stira l'immagine in un riquadro 200x80 dp: uso lo stesso rapporto (600x240 a xxhdpi) per non deformare il testo.
        // Se il contenuto è più largo del riquadro lo riduco in proporzione, così la riga non viene tagliata.
        val fit = minOf(580.0 / cropped.width, 220.0 / cropped.height, 1.0)
        val drawW = (cropped.width * fit).toInt()
        val drawH = (cropped.height * fit).toInt()
        val out = BufferedImage(600, 240, BufferedImage.TYPE_INT_ARGB)
        val og = out.createGraphics()
        og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        og.drawImage(cropped, (600 - drawW) / 2, (240 - drawH) / 2, drawW, drawH, null)
        og.dispose()

        val outDir = splashCreditsDir.get().asFile.resolve("drawable-xxhdpi")
        outDir.mkdirs()
        ImageIO.write(out, "png", outDir.resolve("splash_credits.png"))
    }
}

android.sourceSets["main"].res.srcDir(splashCreditsDir)

tasks.configureEach {
    if (name == generateSplashCredits.name) return@configureEach
    if (name.contains("Resources") || name.contains("SourceSetPaths")) {
        dependsOn(generateSplashCredits)
    } else {
        mustRunAfter(generateSplashCredits)
    }
}
