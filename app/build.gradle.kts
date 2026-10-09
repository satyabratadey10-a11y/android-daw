plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.android.daw"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.android.daw"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf(
                    "-std=c++20",
                    "-O3",
                    "-Wall",
                    "-Wextra",
                    "-Wpedantic",
                    "-fexceptions",
                    "-frtti",
                    "-ffast-math",
                    "-fvisibility=hidden"
                )
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-26",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            jniDebuggable = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Jetpack Compose BOM 2024.04.01
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Activity & Lifecycle Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // AndroidX Core & Media (Audio Focus / System Media)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.media:media:1.7.0")

    // Media3 (ExoPlayer, Common)
    implementation("androidx.media3:media3-common:1.3.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")

    // Coroutines & Immutable Collections
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// ==============================================================================
// Section 9: Verification Tasks (Compose-Only UI Architecture Enforcement)
// ==============================================================================
tasks.register("verifyComposeOnlyUi") {
    group = "verification"
    description = "Enforces Compose-only UI rules: forbidden XML absence, Palette-only hex colors, and Layer-only zIndex."

    doLast {
        val violations = mutableListOf<String>()

        // 1. Verify absence of forbidden XML files under src/main/res/
        val resDir = file("src/main/res")
        if (resDir.exists()) {
            val forbiddenDirPrefixes = setOf("layout", "values", "menu", "anim", "color", "xml")
            resDir.walkTopDown().forEach { resFile ->
                if (resFile.isFile) {
                    val relPath = resFile.relativeTo(resDir).path.replace('\\', '/')
                    val topDir = relPath.substringBefore('/')
                    val dirPrefix = topDir.substringBefore('-')
                    if (dirPrefix in forbiddenDirPrefixes) {
                        violations.add("Forbidden XML/resource file under res/$topDir: $relPath")
                    } else if (dirPrefix.startsWith("drawable") || dirPrefix.startsWith("mipmap")) {
                        if (resFile.name.endsWith(".xml") && !resFile.name.startsWith("ic_launcher")) {
                            violations.add("Forbidden XML drawable (only launcher icons allowed): $relPath")
                        }
                    }
                }
            }
        }

        // 2 & 3. Scan Kotlin source files under src/main/java
        val javaDir = file("src/main/java")
        if (javaDir.exists()) {
            val colorHexPattern = Regex("""(?:\bandroidx\.compose\.ui\.graphics\.)?Color\s*\(\s*0x[0-9a-fA-F]+""")
            val zIndexPattern = Regex("""(?:\bModifier\.)?zIndex\s*\(\s*([^)]*)\)""")
            val layerConstPattern = Regex("""^Layer\.[A-Za-z0-9_]+$""")

            javaDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { ktFile ->
                val relPath = ktFile.relativeTo(javaDir).path.replace('\\', '/')
                val isPalette = relPath.endsWith("ui/theme/Palette.kt") || relPath.endsWith("Palette.kt")

                val lines = ktFile.readLines()
                lines.forEachIndexed { index, line ->
                    val lineNum = index + 1
                    val trimmed = line.trim()

                    // Skip comment lines
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        return@forEachIndexed
                    }

                    // Check 2: Color(0x...) must only appear in Palette.kt
                    if (!isPalette && colorHexPattern.containsMatchIn(line)) {
                        violations.add("Forbidden hex Color literal outside Palette.kt at $relPath:$lineNum: $trimmed")
                    }

                    // Check 3: Modifier.zIndex(...) must use Layer.* constants
                    zIndexPattern.findAll(line).forEach { match ->
                        val arg = match.groupValues[1].trim()
                        if (!layerConstPattern.matches(arg)) {
                            violations.add("Modifier.zIndex called without Layer.* at $relPath:$lineNum: $trimmed (argument: '$arg')")
                        }
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("verifyComposeOnlyUi FAILED with ${violations.size} rule violation(s):")
                violations.forEach { appendLine("  [VIOLATION] $it") }
            }
            throw GradleException(message)
        } else {
            logger.lifecycle("SUCCESS: verifyComposeOnlyUi passed. Zero forbidden XMLs, Palette-only hex colors, Layer.*-only zIndex.")
        }
    }
}

