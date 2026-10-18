plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("app.cash.paparazzi")
}

android {
    namespace = "fr.joursecoules.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.joursecoules.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Clé de signature fixe, versionnée avec le projet : chaque APK compilé
    // (ici ou sur GitHub) a la même signature, donc les mises à jour
    // s'installent par-dessus l'ancienne version sans la désinstaller.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/dayelapsed.jks")
            storePassword = "dayelapsed"
            keyAlias = "dayelapsed"
            keyPassword = "dayelapsed"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
