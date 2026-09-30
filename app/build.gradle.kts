plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ilyaas.einkclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ilyaas.einkclock"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // English-only resources: the stock app ships ~85 locales and that is
        // most of its 3.7 MB resources.arsc.
        resourceConfigurations += listOf("en")
        vectorDrawables.useSupportLibrary = false
    }

    buildTypes {
        // The size gates (<= 600 KB, one dex, shrunk resources) apply to the
        // APK we actually install and verify, so the debug build is minified
        // too. It stays debuggable so `run-as` works for DB inspection.
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // No reflection, no kotlin-reflect: keep the stdlib as small as R8 can make it.
        freeCompilerArgs += listOf("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
    }

    buildFeatures {
        buildConfig = true
        compose = false
        viewBinding = false
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.version",
            "META-INF/*.kotlin_module",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // The single dependency allowed by the brief. Bundled into the dex by R8.
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")

    testImplementation("junit:junit:4.13.2")
}
