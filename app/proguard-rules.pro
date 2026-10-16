-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
# Tink (da security-crypto) referenzia annotazioni solo a tempo di compilazione
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
