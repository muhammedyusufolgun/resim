plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }; namespace = "com.olgun.resim"; compileSdk = 35
 defaultConfig { applicationId = "com.olgun.resim"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" } }