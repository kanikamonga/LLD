package LLD.JdbcQuery.output;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/** Prints each result row as comma-separated values. */
public final class ConsoleRowPrinter implements RowPrinter {
    @Override
    public void print(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();

        while (resultSet.next()) {
            StringBuilder row = new StringBuilder();
            for (int column = 1; column <= columnCount; column++) {
                if (column > 1) {
                    row.append(", ");
                }
                row.append(String.valueOf(resultSet.getObject(column)));
            }
            System.out.println(row);
        }
    }
}
