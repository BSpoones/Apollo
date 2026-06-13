package com.beespoon.apollo.util

import java.io.*
import java.nio.file.*
import java.util.UUID
import com.beespoon.apollo.reference.DirectoryPropertyDescriptor

@CoverageIgnore
internal fun File.writeTextAtomically(text: String) {
    val temp = File(parentFile, "$name.${UUID.randomUUID()}.tmp")
    try {
        temp.writeText(text)
        Files.move(temp.toPath(), toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
    } finally {
        temp.delete()
    }
}

internal fun touchTree(mainFile: File, props: List<DirectoryPropertyDescriptor>) {
    if (mainFile.exists()) touch(mainFile)
    walkEntries(props, mainFile.parentFile, mainFile.extension) { file, _, _ -> touch(file) }
}

private fun touch(file: File) {
    runCatching { FileInputStream(file).close() }
}

internal fun takeSnapshots(mainFile: File, props: List<DirectoryPropertyDescriptor>): Map<String, FileSnapshot> {
    val snapshots = mutableMapOf<String, FileSnapshot>()
    if (mainFile.exists()) snapshots[mainFile.absolutePath] = FileSnapshot(mainFile.lastModified(), mainFile.length())
    walkEntries(props, mainFile.parentFile, mainFile.extension) { file, _, _ ->
        snapshots[file.absolutePath] = FileSnapshot(file.lastModified(), file.length())
    }
    return snapshots
}

internal fun walkEntries(
    children: List<DirectoryPropertyDescriptor>,
    dir: File,
    extension: String,
    childrenFirst: Boolean = false,
    onEntry: (file: File, child: DirectoryPropertyDescriptor, entryDir: File) -> Unit
) {
    for (child in children) {
        val childDir = File(dir, child.dirName)
        if (!childDir.exists()) continue
        if (child.children.isNotEmpty()) {
            for (subDir in childDir.subdirectories()) {
                val file = child.entryFile(subDir, extension)
                if (!file.exists()) continue
                if (childrenFirst) {
                    walkEntries(child.children, subDir, extension, childrenFirst, onEntry)
                    onEntry(file, child, subDir)
                } else {
                    onEntry(file, child, subDir)
                    walkEntries(child.children, subDir, extension, childrenFirst, onEntry)
                }
            }
        } else {
            for (file in childDir.filesWithExtension(extension)) onEntry(file, child, childDir)
        }
    }
}

internal fun File.filesWithExtension(extension: String): List<File> =
    listFiles { file -> file.isFile && file.extension.equals(extension, ignoreCase = true) }?.sortedWith(
        NaturalSortComparator
    ).orEmpty()

internal fun File.subdirectories(): List<File> =
    listFiles { file -> file.isDirectory }?.sortedWith(NaturalSortComparator).orEmpty()
