import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// ---------- Versionado (se define una sola vez en gradle.properties) ----------
// versionCode = MAJOR*10000 + MINOR*100 + PATCH*... + candidata; ver gradle.properties.
val appVersionCode = (project.property("APP_VERSION_CODE") as String).toInt()
val appVersionName = project.property("APP_VERSION_NAME") as String

// Identifica el codigo exacto con el que se genero cada artefacto (para relacionar reportes de testers).
fun gitDescribe(): String = try {
    val commit = providers.exec {
        commandLine("git", "describe", "--always")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { "sin-git" }
    // Cambios sin commitear en archivos versionados (se ignora la configuracion del IDE).
    val cambios = providers.exec {
        commandLine("git", "status", "--porcelain", "--untracked-files=no", "--", ".", ":!.idea")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
    if (cambios.isEmpty()) commit else "$commit-dirty"
} catch (e: Exception) {
    "sin-git"
}

// ---------- Firma release: las credenciales NO viven en el repositorio ----------
// Se leen de keystore.properties (ignorado por git). Ver keystore.properties.example.
val propiedadesFirma = Properties().apply {
    val archivo = rootProject.file("keystore.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}
val hayFirmaRelease = propiedadesFirma.containsKey("storeFile")

android {
    namespace = "com.mansilla.recordatoriosapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.mansilla.recordatoriosapp"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GIT_DESCRIBE", "\"${gitDescribe()}\"")
        buildConfigField("String", "BUILD_DATE", "\"${SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())}\"")
    }

    signingConfigs {
        if (hayFirmaRelease) {
            create("release") {
                storeFile = file(propiedadesFirma.getProperty("storeFile"))
                storePassword = propiedadesFirma.getProperty("storePassword")
                keyAlias = propiedadesFirma.getProperty("keyAlias")
                keyPassword = propiedadesFirma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Codigo y recursos reducidos y ofuscados en la version candidata.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hayFirmaRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Ambientes: "dev" usa el Firebase Emulator Suite local; "prod" usa el proyecto Firebase real.
    flavorDimensions += "ambiente"
    productFlavors {
        create("dev") {
            dimension = "ambiente"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Recordatorios (dev)")
            buildConfigField("boolean", "USAR_EMULADOR_FIREBASE", "true")
            // 10.0.2.2 es la PC desde el emulador de Android.
            buildConfigField("String", "EMULADOR_HOST", "\"10.0.2.2\"")
            buildConfigField("String", "AMBIENTE", "\"dev\"")
        }
        create("prod") {
            dimension = "ambiente"
            resValue("string", "app_name", "Recordatorios")
            buildConfigField("boolean", "USAR_EMULADOR_FIREBASE", "false")
            buildConfigField("String", "EMULADOR_HOST", "\"\"")
            buildConfigField("String", "AMBIENTE", "\"prod\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        // Esquemas de Room exportados, para probar las migraciones.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Persistencia local
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Firebase (Authentication y Cloud Firestore)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
