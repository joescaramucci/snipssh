-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
# JSch usa Bouncy Castle per ssh-ed25519 su Android
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
# Tink (da security-crypto) referenzia annotazioni solo a tempo di compilazione
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
