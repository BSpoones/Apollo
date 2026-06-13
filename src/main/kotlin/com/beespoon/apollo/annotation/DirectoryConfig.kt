package com.beespoon.apollo.annotation


/**
 * Marks a [Collection] or [Map] as a directory config.
 *
 * Any values in either of the above will become separate config files, instead of appearing
 * alongside the rest of the config.
 * - [Collection] - File name does not matter, if defaulted, it'll be a UUID
 * - [Map] - File name acts as map key (without extension), no duplicates
 *
 * Directory config files follow the [com.beespoon.apollo.util.NaturalSortComparator] (1,2,..,10,11 instead of 1,10,11,2,...)
 *
 * Element files are written in the parent [FileConfig]'s format.
 *
 * @param name [String] Override directory name, defaults to parameter name
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class DirectoryConfig(public val name: String = "")
