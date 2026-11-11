plugins {
    alias(libs.plugins.android.application)
}

android {
    useLibrary("android.car")
    namespace = "app.eodigaseyo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.eodigaseyo"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 공모전 제출용: 따로 키를 만들지 않아도 설치·실행되도록 디버그 키로 서명해요.
            // Pleos App Market에 올릴 때는 Build > Generate Signed App Bundle / APK로 만든 출시 키로 바꿔요.
            signingConfig = signingConfigs.getByName("debug")
            // 코드 축소(R8)는 끄고 그대로 빌드해요: Pleos SDK·자동차 API가 지워지지 않게 (디버그와 같은 코드로 동작)
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
}