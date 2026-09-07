package LLD.JdbcQuery.connection;

import LLD.JdbcQuery.model.QueryInput;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** JDBC DriverManager adapter for MySQL, PostgreSQL, and compatible drivers. */
public final class DriverManagerConnectionProvider implements ConnectionProvider {
    @Override
    public Connection open(QueryInput input) throws SQLException {
        return DriverManager.getConnection(input.url(), input.username(), input.password());
    }
}
