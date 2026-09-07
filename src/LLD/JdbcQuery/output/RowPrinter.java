package LLD.JdbcQuery.output;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Formats and prints result rows while preserving database column order. */
public interface RowPrinter {
    void print(ResultSet resultSet) throws SQLException;
}
