plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.2.0" // Atualizado para evitar conflito
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" // KSP substitui KAPT
}

android {
    namespace = "com.example.missfirebackupapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.missfirebackupapp"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Java/Kotlin 17 required for Android Gradle Plugin 8.10.x
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Prefer explicit JVM target 17 for Kotlin compiler
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // (Optional) Enforce Java toolchain – uncomment if local JAVA_HOME points to older JDK
    // java {
    //     toolchain {
    //         languageVersion.set(JavaLanguageVersion.of(17))
    //     }
    // }
}

dependencies {
    val roomVersion = "2.7.0-alpha01" // versão mais recente compatível com KSP

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Firebase
    implementation("com.google.firebase:firebase-auth:22.3.1")
    implementation("com.google.firebase:firebase-firestore:25.0.0")
    implementation("com.google.firebase:firebase-storage:21.0.1")

    // Room com KSP
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Necessário para usar Task.await() (Firebase + coroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Testes
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    //Câmera
    implementation("com.google.android.gms:play-services-location:21.0.1")

}
