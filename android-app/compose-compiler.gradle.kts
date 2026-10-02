plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

composeCompiler {
    enableStrongSkippingMode.set(true)
}
