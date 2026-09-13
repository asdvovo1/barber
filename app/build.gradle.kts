plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

android {
	namespace = "com.barber.rush"
	compileSdk = 34

	defaultConfig {
		applicationId = "com.barber.rush"
		minSdk = 24
		targetSdk = 34
		versionCode = 18
		versionName = "18.0"
	}

	buildTypes {
		release {
			isMinifyEnabled = false
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}
}

dependencies {
}
