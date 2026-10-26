package com.rou39.bentohome.monitor

/** `dumpsys thermalservice` の温度（℃）。HAL の現在値を優先し、無いものはキャッシュから補う。 */
data class Temperatures(val skin: Float?, val battery: Float?, val cpu: Float?, val gpu: Float?) {
    companion object {
        private val PATTERN = Regex("""Temperature\{mValue=(-?[0-9.]+), mType=(-?\d+), mName=([^,]+),""")

        // android.os.Temperature の種類
        private const val TYPE_CPU = 0
        private const val TYPE_GPU = 1
        private const val TYPE_BATTERY = 2
        private const val TYPE_SKIN = 3

        fun parse(text: String): Temperatures? {
            val merged = LinkedHashMap<String, Pair<Int, Float>>()
            (section(text, "Cached temperatures:") + section(text, "Current temperatures from HAL:"))
                .forEach { (name, type, value) -> merged[name] = type to value }
            if (merged.isEmpty()) return null
            fun maxOf(type: Int) = merged.values.filter { it.first == type }.maxOfOrNull { it.second }
            return Temperatures(
                skin = merged["VIRTUAL-SKIN"]?.second ?: maxOf(TYPE_SKIN),
                battery = maxOf(TYPE_BATTERY),
                cpu = maxOf(TYPE_CPU),
                gpu = maxOf(TYPE_GPU),
            )
        }

        private fun section(text: String, header: String): List<Triple<String, Int, Float>> {
            val start = text.indexOf(header)
            if (start < 0) return emptyList()
            return text.substring(start + header.length).lineSequence().drop(1)
                .takeWhile { it.startsWith("\t") || it.startsWith(" ") }
                .mapNotNull { PATTERN.find(it) }
                .map { m -> Triple(m.groupValues[3], m.groupValues[2].toInt(), m.groupValues[1].toFloat()) }
                // 使っていないセンサーは 0 を返す
                .filter { it.third != 0f }
                .toList()
        }
    }
}
