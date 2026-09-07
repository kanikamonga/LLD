package LLD.JdbcQuery.service;

import LLD.JdbcQuery.connection.ConnectionProvider;
import LLD.JdbcQuery.model.QueryInput;
import LLD.JdbcQuery.output.RowPrinter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** Executes one validated SELECT query and closes every JDBC resource. */
public final class JdbcQueryExecutor {
    private final ConnectionProvider connectionProvider;
    private final RowPrinter rowPrinter;

    public JdbcQueryExecutor(ConnectionProvider connectionProvider, RowPrinter rowPrinter) {
        if (connectionProvider == null || rowPrinter == null) {
            throw new IllegalArgumentException("Executor dependencies are required");
        }
        this.connectionProvider = connectionProvider;
        this.rowPrinter = rowPrinter;
    }

    public void execute(QueryInput input) throws Exception {
        try (Connection connection = connectionProvider.open(input);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(input.query())) {
            rowPrinter.print(resultSet);
        }
    }
}
