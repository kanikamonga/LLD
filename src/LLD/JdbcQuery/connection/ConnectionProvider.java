package LLD.JdbcQuery.connection;

import LLD.JdbcQuery.model.QueryInput;
import java.sql.Connection;
import java.sql.SQLException;

/** Abstraction for creating relational database connections. */
public interface ConnectionProvider {
    Connection open(QueryInput input) throws SQLException;
}
