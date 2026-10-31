plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp") // para Room
}

android {
    namespace = "com.example.jarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.jarvis"
        minSdk = 28          // Android 9 — exigido pelo módulo de atualizações (assinatura/longVersionCode)
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"

        val localProps = java.util.Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) load(file.inputStream())
        }
        buildConfigField(
            "String", "PROXY_BASE_URL",
            "\"${localProps.getProperty("PROXY_BASE_URL", "")}\""
        )
        // Precisa bater com PROXY_SHARED_SECRET configurado no Worker
        // (wrangler secret put PROXY_SHARED_SECRET) — sem isso o proxy
        // responde 401. Só é usado quando PROXY_BASE_URL está setado.
        buildConfigField(
            "String", "PROXY_SHARED_SECRET",
            "\"${localProps.getProperty("PROXY_SHARED_SECRET", "")}\""
        )
    }

    // Assinatura de release: a MESMA keystore em todas as versões (senão o Android recusa a atualização).
    // Lê de variáveis de ambiente (CI) ou de local.properties (máquina local).
    val ksProps = java.util.Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.inputStream())
    }
    fun ks(key: String): String? = System.getenv(key) ?: ksProps.getProperty(key)
    val ksPath = ks("KEYSTORE_PATH")
    signingConfigs {
        if (ksPath != null && file(ksPath).exists()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = ks("KEYSTORE_PASSWORD")
                keyAlias = ks("KEY_ALIAS")
                keyPassword = ks("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true      // android.util.Log etc. não crasham em testes JVM
            isIncludeAndroidResources = true  // Robolectric
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core Android + Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0") // tema XML Theme.Material3
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // Rede (proxy de IA)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Serialização JSON (contrato estruturado do LLM)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Persistência local (memória de longo prazo)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines (orquestração assíncrona)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Voz offline (wake word + STT)
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47")
    // LLM local (opcional, carregado por reflexão): descomenta e põe local-llm.task no app.
    // implementation("com.google.mediapipe:tasks-genai:0.10.14")

    // Testes
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // org.json real (o do android.jar é stub e quebra ClaudeClientTest)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
