package br.com.mykytadu.translation.domain.model

class NormalizedText private constructor(val value: String) {

    val length: Int get() = value.length

    override fun equals(other: Any?): Boolean = other is NormalizedText && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "NormalizedText(length=$length)"

    companion object {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 10_000

        fun of(value: String): NormalizedText {
            require(value.length in MIN_LENGTH..MAX_LENGTH) {
                "Text length must be between $MIN_LENGTH and $MAX_LENGTH characters"
            }
            return NormalizedText(value)
        }
    }
}
