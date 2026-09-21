package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.HEADWIND_PACKAGE
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadwindPackageTest {

    private fun manifest(): String {
        val root =
            generateSequence(File("").absoluteFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").exists() }
        return File(root, "app/src/main/AndroidManifest.xml").readText()
    }

    @Test
    fun manifest_queries_the_headwind_package_the_activity_looks_up() {
        assertTrue(manifest().contains("<package android:name=\"$HEADWIND_PACKAGE\" />"))
    }
}
