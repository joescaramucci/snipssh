plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.sshsnippets"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.joescaramucci.snipssh"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
    implementation("com.github.mwiede:jsch:0.2.18")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
}
