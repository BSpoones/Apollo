package com.beespoon.apollo.annotation

/**
 * Marks a class as a file config.
 *
 * The annotated class becomes a live, delegatable config file on disk, written with its
 * defaults on first access.
 *
 * @param name [String] Override file name, defaults to class name (lowercased, "config" dropped)
 * @param format [String] File type override, sets the file extension and codec, defaults to json
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class FileConfig(public val name: String = "", public val format: String = "json")
