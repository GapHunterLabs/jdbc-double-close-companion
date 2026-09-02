package dev.gaphunter.jdbcdoubleclosecompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import dev.gaphunter.jdbcdoubleclosecompanion.detect.JdbcDoubleCloseFinder
import dev.gaphunter.jdbcdoubleclosecompanion.model.DoubleCloseHit
import dev.gaphunter.jdbcdoubleclosecompanion.review.ReviewPrompt

/** Flags a JDBC resource `.close()`/other call reached with the resource already closed on some or every path -- see [JdbcDoubleCloseFinder]. */
class JdbcDoubleCloseInspection : LocalInspectionTool() {

    companion object {
        const val MAX_FILE_LENGTH = 500_000
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file.text.length > MAX_FILE_LENGTH) return null

        val hits = JdbcDoubleCloseFinder.findAll(file)
        if (hits.isEmpty()) return null

        val problems = hits.map { hit ->
            manager.createProblemDescriptor(
                hit.anchor,
                messageFor(hit),
                isOnTheFly,
                emptyArray(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }

        val path = file.virtualFile?.path
        if (path != null) {
            for (hit in hits) {
                val lineNumber = file.viewProvider.document?.getLineNumber(hit.anchor.textRange.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber:${hit.variableName}:${hit.kind}")
            }
        }

        return problems.toTypedArray()
    }

    private fun messageFor(hit: DoubleCloseHit): String = when (hit.kind) {
        DoubleCloseHit.Kind.DEFINITE_DOUBLE_CLOSE ->
            "'${hit.variableName}' is closed here, but it is ALREADY closed on every path reaching this point -- a real double-close (CWE-675)"
        DoubleCloseHit.Kind.POSSIBLE_DOUBLE_CLOSE ->
            "'${hit.variableName}' is closed here, but it may already be closed on at least one path reaching this point -- a path-dependent double-close (CWE-675)"
        DoubleCloseHit.Kind.USE_AFTER_CLOSE ->
            "'${hit.variableName}' is used here, but it is already closed on every path reaching this point -- use-after-close"
    }
}
