package com.suppprith.dms.update

/** Semantic version as used in release tags: `v1.2.3` or `1.2.3`. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString() = "$major.$minor.$patch"

    /** Same formula as app/build.gradle.kts, so comparing names equals comparing versionCodes. */
    val code get() = major * 10_000 + minor * 100 + patch

    companion object {
        private val pattern = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

        fun parse(text: String?): Version? {
            val m = pattern.find(text?.trim() ?: return null) ?: return null
            return Version(
                m.groupValues[1].toInt(),
                m.groupValues[2].toIntOrNull() ?: 0,
                m.groupValues[3].toIntOrNull() ?: 0,
            )
        }
    }
}
