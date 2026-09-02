package dev.gaphunter.jdbcdoubleclosecompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Assertions match on this plugin's OWN distinctive message text
 * ("CWE-675", "use-after-close"), never on the bare word "close" --
 * confirmed the hard way: the light test fixture's mock JDK can leave
 * `java.sql.Connection`/`DriverManager` unresolved, and Java's own
 * semantic highlighting then reports "Cannot resolve method 'close()'"
 * on the exact same call sites -- an UNRELATED diagnostic that a
 * looser assertion (matching just the word "close") would misread as
 * this plugin's own warning.
 */
class JdbcDoubleCloseInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(JdbcDoubleCloseInspection::class.java)
    }

    fun `test an unconditional double close is flagged as definite`() {
        myFixture.configureByText(
            "Definite.java",
            """
            import java.sql.Connection;
            import java.sql.DriverManager;

            class Definite {
                void run() throws Exception {
                    Connection conn = DriverManager.getConnection("jdbc:x");
                    conn.close();
                    conn.close();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("CWE-675") == true && it.description?.contains("every path") == true })
    }

    fun `test a close after a conditionally-closing branch is flagged as possible`() {
        myFixture.configureByText(
            "Possible.java",
            """
            import java.sql.Connection;
            import java.sql.DriverManager;

            class Possible {
                void run(boolean flag) throws Exception {
                    Connection conn = DriverManager.getConnection("jdbc:x");
                    if (flag) {
                        conn.close();
                    }
                    conn.close();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("CWE-675") == true && it.description?.contains("at least one path") == true })
    }

    fun `test a use after close is flagged`() {
        myFixture.configureByText(
            "UseAfterClose.java",
            """
            import java.sql.Connection;
            import java.sql.DriverManager;

            class UseAfterClose {
                void run() throws Exception {
                    Connection conn = DriverManager.getConnection("jdbc:x");
                    conn.close();
                    conn.createStatement();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("use-after-close") == true })
    }

    fun `test a single close with no reuse is not flagged`() {
        myFixture.configureByText(
            "Safe.java",
            """
            import java.sql.Connection;
            import java.sql.DriverManager;

            class Safe {
                void run() throws Exception {
                    Connection conn = DriverManager.getConnection("jdbc:x");
                    conn.createStatement();
                    conn.close();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-675") == true || it.description?.contains("use-after-close") == true })
    }

    fun `test each branch of an if-else closing the resource exactly once is not flagged`() {
        myFixture.configureByText(
            "BranchSafe.java",
            """
            import java.sql.Connection;
            import java.sql.DriverManager;

            class BranchSafe {
                void run(boolean flag) throws Exception {
                    Connection conn = DriverManager.getConnection("jdbc:x");
                    if (flag) {
                        conn.close();
                    } else {
                        conn.close();
                    }
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-675") == true || it.description?.contains("use-after-close") == true })
    }

    fun `test a non-JDBC type with a coincidental close method is not flagged`() {
        myFixture.configureByText(
            "NotJdbc.java",
            """
            class MyCloseable {
                void close() {}
            }

            class NotJdbc {
                void run() {
                    MyCloseable c = new MyCloseable();
                    c.close();
                    c.close();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-675") == true || it.description?.contains("use-after-close") == true })
    }
}
