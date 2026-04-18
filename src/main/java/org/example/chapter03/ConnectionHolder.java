package org.example.chapter03;

/**
 * Listing 3.10 - Using ThreadLocal to Ensure Thread Confinement.
 *
 * Each thread gets its own Connection. The first call to get() on a
 * given thread triggers initialValue(); subsequent calls return that
 * thread's stored value. Since JDBC Connections are not required to
 * be thread-safe, storing one per thread avoids both sharing and
 * the need for locking.
 *
 * A faux Connection class is used here to avoid depending on a real
 * JDBC driver.
 */
public class ConnectionHolder {

    public static final class Connection {
        private final String url;

        public Connection(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }

    private static final String DB_URL = "jdbc:example://localhost/testdb";

    private static final ThreadLocal<Connection> connectionHolder =
            ThreadLocal.withInitial(() -> new Connection(DB_URL));

    public static Connection getConnection() {
        return connectionHolder.get();
    }
}
