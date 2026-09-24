import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Where the "local" build variant sends API calls. Default: the Docker stack
// from docker-compose.local.yml as seen from the Android emulator (10.0.2.2 is
// the host machine). For a physical phone set local.api.url in local.properties
// — see LOCAL_SETUP.md.
val localApiUrl: String = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { props.load(it) }
    val url = props.getProperty("local.api.url")?.trim().takeUnless { it.isNullOrEmpty() }
        ?: "http://10.0.2.2:8080/"
    if (url.endsWith("/")) url else "$url/"
}

// On a physical phone "localhost" is the phone itself, so a localhost local.api.url only works
// after `adb reverse tcp:<port> tcp:<port>` -- which is lost every time the phone reconnects, and
// forgetting it shows up in the app as "Failed to connect to localhost/127.0.0.1:8080". Building
// the local variant (Android Studio's Run included) therefore sets it up for every connected
// device. It is best-effort: with no device, or no adb, the build carries on.
val adbReverseLocalApi = tasks.register("adbReverseLocalApi") {
    val uri = java.net.URI(localApiUrl)
    val isLoopback = uri.host == "localhost" || uri.host == "127.0.0.1"
    val port = if (uri.port != -1) uri.port else 80
    val adb = androidComponents.sdkComponents.adb
    onlyIf { isLoopback }
    doLast {
        fun adbCall(vararg args: String): String? = try {
            val process = ProcessBuilder(adb.get().asFile.absolutePath, *args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() == 0) output else null
        } catch (e: Exception) {
            null
        }

        val serials = adbCall("devices")
            ?.lines()
            ?.drop(1)
            ?.map { it.split(Regex("\\s+")) }
            ?.filter { it.size >= 2 && it[1] == "device" }
            ?.map { it[0] }
            .orEmpty()
        if (serials.isEmpty()) {
            logger.warn("adbReverseLocalApi: no device connected; run `adb reverse tcp:$port tcp:$port` once it is.")
        }
        serials.forEach { serial ->
            if (adbCall("-s", serial, "reverse", "tcp:$port", "tcp:$port") != null) {
                logger.lifecycle("adbReverseLocalApi: $serial -> localhost:$port forwarded to this PC")
            } else {
                logger.warn("adbReverseLocalApi: `adb reverse` failed for $serial")
            }
        }
    }
}
tasks.matching { it.name == "preLocalBuild" }.configureEach { dependsOn(adbReverseLocalApi) }

android {
    namespace = "com.maths.teacher.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maths.teacher.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 18
        versionName = "5.1.0"
        manifestPlaceholders["appLabel"] = "Singh Sir"
    }

    // CI signs with the upload key passed in through env vars (see
    // .github/workflows/android-release.yml). Locally none are set, so release
    // builds stay unsigned here and Android Studio's "Generate Signed Bundle"
    // works exactly as before.
    val ciKeystore = System.getenv("ANDROID_KEYSTORE_PATH")
    if (ciKeystore != null) {
        signingConfigs {
            create("ci") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://13.205.19.207:8080/\"")
        }
        release {
            isMinifyEnabled = false
            if (ciKeystore != null) signingConfig = signingConfigs.getByName("ci")
            buildConfigField("String", "BASE_URL", "\"http://13.205.19.207:8080/\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Debug build against the local Docker backend (docker-compose.local.yml).
        // Installs next to the normal app (".local" id, "Singh Sir (Local)" label),
        // so the production app on the same device is untouched.
        create("local") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            matchingFallbacks += listOf("debug")
            manifestPlaceholders["appLabel"] = "Singh Sir (Local)"
            buildConfigField("String", "BASE_URL", "\"$localApiUrl\"")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
