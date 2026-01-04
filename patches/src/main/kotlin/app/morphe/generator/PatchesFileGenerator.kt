package app.morphe.generator

import app.morphe.patcher.patch.Patch

internal interface PatchesFileGenerator {
    fun generate(patches: Set<Patch<*>>)
}
