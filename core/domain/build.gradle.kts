plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
