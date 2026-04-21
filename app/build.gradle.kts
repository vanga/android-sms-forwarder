plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "in.dattam.smsforwarder"
    compileSdk = 36

    defaultConfig {
        applicationId = "in.dattam.smsforwarder"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1"
    }

    // Pull release-signing creds from ~/.gradle/gradle.properties (never committed).
    // If the props are absent (e.g. CI / fresh clone without signing setup), release
    // builds fall through unsigned and the developer gets a clear "signing not configured"
    // error rather than a broken key.
    val keystoreFile = findProperty("SMS_FORWARDER_KEYSTORE") as String?
    val keyAlias0 = findProperty("SMS_FORWARDER_KEY_ALIAS") as String?
    val storePw = findProperty("SMS_FORWARDER_STORE_PASSWORD") as String?
    val keyPw = findProperty("SMS_FORWARDER_KEY_PASSWORD") as String?
    val canSignRelease = keystoreFile != null && keyAlias0 != null && storePw != null && keyPw != null

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(keystoreFile!!)
                keyAlias = keyAlias0
                storePassword = storePw
                keyPassword = keyPw
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    // Room schema export location — committed under app/schemas/ so migrations
    // can be validated in MigrationTestHelper tests later.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.okhttp.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
