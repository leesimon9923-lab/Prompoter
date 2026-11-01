plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.simongolf.prompter"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.simongolf.prompter"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"
    }
    signingConfigs {
        create("fixed") {
            storeFile = file("simongolf.keystore")
            storePassword = "simongolf"
            keyAlias = "simongolf"
            keyPassword = "simongolf"
        }
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val camerax = "1.3.4"
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
}
