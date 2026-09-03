import java.sql.Connection;
import java.sql.DriverManager;

class Definite {
    void run() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:x");
        conn.close();
        conn.close();
    }
}
