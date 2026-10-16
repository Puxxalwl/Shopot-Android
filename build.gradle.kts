// Root build file for Shopot Android client
// Shares shopot-core's MIT license and "no third-party services" invariant.
@Suppress("GradleDependency")
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.1.0" apply false
    id("dagger.hilt.android.plugin") version "2.52" apply false
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
