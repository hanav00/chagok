import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 릴리스 서명 키는 git 에 올리지 않는 local.properties 에서 읽는다. 없으면 서명 없는 릴리스 APK 가 나온다.
// [설정 필요] release.storeFile · release.storePassword · release.keyAlias · release.keyPassword
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val releaseStoreFile: String? = localProperties.getProperty("release.storeFile")

android {
    namespace = "kr.chagok"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.chagok"
        minSdk = 28 // Pleos Connect 요구사항
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProperties.getProperty("release.storePassword")
                keyAlias = localProperties.getProperty("release.keyAlias")
                keyPassword = localProperties.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    // 기어는 AAOS 표준 Car API(CarPropertyManager)로 읽는다. 차량 OS 에 들어 있어 APK 에 넣지 않는다
    useLibrary("android.car")
    testOptions {
        unitTests {
            // Room·리소스를 쓰는 테스트는 Robolectric 으로 JVM 에서 돌린다 (src/test/resources/robolectric.properties)
            isIncludeAndroidResources = true
            // 순수 JVM 테스트에서 android.util.Log 를 불러도 죽지 않게
            isReturnDefaultValues = true
        }
    }
}

ksp {
    // DB 구조가 바뀔 때 마이그레이션을 비교할 수 있게 스키마를 저장소에 남긴다
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
