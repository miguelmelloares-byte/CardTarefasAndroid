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
        versionCode = 14
        versionName = "2.3"
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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // play-services-wearable removido por ora (modulo do relogio esta pausado)
}
