plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miguel.cardtarefas"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.miguel.cardtarefas"
        minSdk = 24
        targetSdk = 34
        versionCode = 42
        versionName = "4.9"
    }

    signingConfigs {
        // chave fixa para o debug: assim todo build tem a MESMA assinatura e o
        // celular consegue atualizar o app por cima (sem desinstalar).
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // chave de release propria do app: builds assinados com ela nao sao
        // tratados como "app de desenvolvimento" pelo Android/Play Protect.
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "nestshare2026"
            keyAlias = "nestshare"
            keyPassword = "nestshare2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.biometric:biometric:1.1.0")
    // play-services-wearable removido por ora (modulo do relogio esta pausado)
}
