package dev.gaphunter.jdbcdoubleclosecompanion.model

import com.intellij.psi.PsiElement

/**
 * One finding from the path-sensitive resource-state analysis --
 * [kind] distinguishes a call reached with the resource DEFINITELY
 * already closed on every path (a certain bug) from one reached with
 * it closed on only SOME paths (a real, but path-dependent, risk).
 */
data class DoubleCloseHit(val anchor: PsiElement, val variableName: String, val kind: Kind) {
    enum class Kind { DEFINITE_DOUBLE_CLOSE, POSSIBLE_DOUBLE_CLOSE, USE_AFTER_CLOSE }
}
