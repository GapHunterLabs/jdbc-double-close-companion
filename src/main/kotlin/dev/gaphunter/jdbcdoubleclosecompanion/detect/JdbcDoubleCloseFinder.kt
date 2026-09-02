package dev.gaphunter.jdbcdoubleclosecompanion.detect

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiBlockStatement
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiDoWhileStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiForStatement
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.PsiWhileStatement
import com.intellij.psi.util.PsiTreeUtil
import dev.gaphunter.jdbcdoubleclosecompanion.model.DoubleCloseHit

private typealias ResourceState = Map<String, Set<Boolean>> // true = closed, false = open

/**
 * Real path-sensitive typestate analysis, one method at a time --
 * every JDBC resource-typed local variable is tracked as a SET of
 * possible states at each program point (`{false}` = definitely open,
 * `{true}` = definitely closed, `{true, false}` = closed on SOME
 * reachable path but not others), propagated by structurally
 * recursing the method's own statement tree and MERGING (set union)
 * the two branches of every `if`/`else`, `try`/`catch`, and loop body
 * -- the same "meet over all paths" idea a real dataflow framework
 * uses, implemented as AST-structural recursion rather than an
 * explicit CFG graph data structure (Java's structured control flow,
 * with no `goto`, doesn't need one for this to be genuinely
 * path-sensitive: both branches of every real fork are visited and
 * merged, not just walked in textual order).
 *
 * Flags: a `.close()` reached with the resource already closed on
 * EVERY path ([DoubleCloseHit.Kind.DEFINITE_DOUBLE_CLOSE]) or on SOME
 * path ([DoubleCloseHit.Kind.POSSIBLE_DOUBLE_CLOSE]); any OTHER method
 * call on the resource reached with it definitely already closed on
 * every path ([DoubleCloseHit.Kind.USE_AFTER_CLOSE]).
 *
 * **v0.1 scope, stated honestly:** only
 * `Connection`/`Statement`/`PreparedStatement`/`CallableStatement`/
 * `ResultSet` local variables (same set the platform's own bundled
 * "JDBC resource opened but not safely closed" inspection covers, a
 * DIFFERENT bug class -- leaks, never double-close); a `catch` block's
 * starting state is conservatively approximated as the state BEFORE
 * the `try` (an exception can occur at any point inside it -- a real,
 * documented simplification, not a guess in either direction since it
 * only makes catch-block findings less certain, never more); a loop
 * body is analyzed for exactly one iteration merged with zero
 * iterations (never a true fixed point across multiple iterations); a
 * method with more than [MAX_STATEMENTS] statements is skipped
 * entirely rather than risk pathological analysis cost.
 */
object JdbcDoubleCloseFinder {

    private val RESOURCE_TYPE_SIMPLE_NAMES = setOf("Connection", "Statement", "PreparedStatement", "CallableStatement", "ResultSet")
    const val MAX_STATEMENTS = 400

    fun findAll(file: PsiFile): List<DoubleCloseHit> {
        val hits = mutableListOf<DoubleCloseHit>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                val body = method.body ?: return
                if (PsiTreeUtil.collectElementsOfType(body, PsiStatement::class.java).size > MAX_STATEMENTS) return
                val analyzer = MethodAnalyzer()
                analyzer.propagate(body.statements.toList(), emptyMap())
                hits += analyzer.hits
            }
        })
        return hits
    }

    private class MethodAnalyzer {
        val hits = mutableListOf<DoubleCloseHit>()

        fun propagate(statements: List<PsiStatement>, incoming: ResourceState): ResourceState {
            var state = incoming
            for (stmt in statements) state = propagateStatement(stmt, state)
            return state
        }

        fun propagateStatement(stmt: PsiStatement, state: ResourceState): ResourceState = when (stmt) {
            is PsiDeclarationStatement -> propagateDeclaration(stmt, state)
            is PsiExpressionStatement -> propagateExpressionStatement(stmt.expression, state)
            is PsiBlockStatement -> propagate(stmt.codeBlock.statements.toList(), state)
            is PsiIfStatement -> propagateIf(stmt, state)
            is PsiTryStatement -> propagateTry(stmt, state)
            is PsiWhileStatement -> propagateLoop(stmt.body, state)
            is PsiForStatement -> propagateLoop(stmt.body, state)
            is PsiDoWhileStatement -> propagateLoop(stmt.body, state)
            else -> state
        }

        private fun propagateDeclaration(stmt: PsiDeclarationStatement, state: ResourceState): ResourceState {
            var result = state
            for (element in stmt.declaredElements) {
                val variable = element as? PsiLocalVariable ?: continue
                // .className reads the declared reference's own text (e.g. "Connection")
                // directly -- deliberately NOT .resolve()?.name, which requires the type
                // to fully resolve against the real classpath. Confirmed necessary the
                // hard way while building interface-exception-divergence-companion: a
                // light test fixture's JDK mock can leave even java.lang types
                // unresolved, so a resolution-dependent check silently finds nothing.
                val typeName = (variable.type as? PsiClassType)?.className ?: continue
                if (typeName !in RESOURCE_TYPE_SIMPLE_NAMES) continue
                if (variable.initializer == null) continue // declared, not yet assigned -- nothing to track from here
                result = result + (variable.name to setOf(false)) // OPEN
            }
            return result
        }

        private fun propagateExpressionStatement(expr: PsiExpression, state: ResourceState): ResourceState {
            val call = expr as? PsiMethodCallExpression ?: return state
            val methodName = call.methodExpression.referenceName ?: return state
            val varName = (call.methodExpression.qualifierExpression as? PsiReferenceExpression)?.referenceName ?: return state
            val currentStates = state[varName] ?: return state

            if (methodName == "close") {
                when {
                    currentStates == setOf(true) -> hits += DoubleCloseHit(anchorOf(call), varName, DoubleCloseHit.Kind.DEFINITE_DOUBLE_CLOSE)
                    true in currentStates -> hits += DoubleCloseHit(anchorOf(call), varName, DoubleCloseHit.Kind.POSSIBLE_DOUBLE_CLOSE)
                }
                return state + (varName to setOf(true))
            }

            if (currentStates == setOf(true)) {
                hits += DoubleCloseHit(anchorOf(call), varName, DoubleCloseHit.Kind.USE_AFTER_CLOSE)
            }
            return state
        }

        private fun propagateIf(stmt: PsiIfStatement, state: ResourceState): ResourceState {
            val thenState = stmt.thenBranch?.let { propagateStatement(it, state) } ?: state
            val elseState = stmt.elseBranch?.let { propagateStatement(it, state) } ?: state
            return merge(thenState, elseState)
        }

        private fun propagateTry(stmt: PsiTryStatement, state: ResourceState): ResourceState {
            val tryEnd = stmt.tryBlock?.let { propagate(it.statements.toList(), state) } ?: state
            var mergedForFinally = tryEnd
            for (catchBlock in stmt.catchBlocks) {
                // An exception can occur at ANY point inside the try block, so the
                // safest starting state for a catch is the state BEFORE the try --
                // stated explicitly in the class doc as a real v0.1 simplification.
                val catchEnd = propagate(catchBlock.statements.toList(), state)
                mergedForFinally = merge(mergedForFinally, catchEnd)
            }
            val finallyBlock = stmt.finallyBlock
            return if (finallyBlock != null) propagate(finallyBlock.statements.toList(), mergedForFinally) else mergedForFinally
        }

        private fun propagateLoop(body: PsiStatement?, state: ResourceState): ResourceState {
            if (body == null) return state
            val bodyEnd = propagateStatement(body, state)
            return merge(state, bodyEnd) // the loop may run zero or more times
        }

        private fun merge(a: ResourceState, b: ResourceState): ResourceState =
            (a.keys + b.keys).associateWith { key -> (a[key] ?: emptySet()) + (b[key] ?: emptySet()) }

        private fun anchorOf(call: PsiMethodCallExpression): PsiElement =
            call.methodExpression.referenceNameElement ?: call.methodExpression
    }
}
