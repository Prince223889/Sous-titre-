plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "ml.sidibe.voixsoustitres"
    compileSdk = 35

    defaultConfig {
        applicationId = "ml.sidibe.voixsoustitres"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Clé de signature fixe : chaque nouvelle version s'installe par-dessus l'ancienne.
    signingConfigs {
        create("perso") {
            storeFile = file("perso.keystore")
            storePassword = "voixsoustitres"
            keyAlias = "perso"
            keyPassword = "voixsoustitres"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("perso")
        }
        debug {
            signingConfig = signingConfigs.getByName("perso")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Reconnaissance de texte sur l'appareil (hors ligne)
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
