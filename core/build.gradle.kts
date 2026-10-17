plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    api("com.github.mwiede:jsch:2.28.8")
    api("org.bouncycastle:bcprov-jdk18on:1.86")
    // Su Android org.json è nella piattaforma; sul desktop lo aggiunge il modulo che usa core.
    compileOnly("org.json:json:20240303")
}
