// Combinación de versiones probada en papel contra las tablas oficiales de compatibilidad:
//   Gradle 8.10.2  +  Android Gradle Plugin 8.7.3  +  Kotlin 2.0.21  +  JDK 17..21
// IMPORTANTE: Gradle 8.10 NO funciona con JDK 25/26 (los que trae Fedora 44).
// Por eso scripts/instalar-sdk-fedora.sh descarga un JDK 21 portable.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
