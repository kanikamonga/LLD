package LLD.JdbcQuery;

import LLD.JdbcQuery.connection.DriverManagerConnectionProvider;
import LLD.JdbcQuery.io.QueryInputReader;
import LLD.JdbcQuery.model.QueryInput;
import LLD.JdbcQuery.output.ConsoleRowPrinter;
import LLD.JdbcQuery.service.JdbcQueryExecutor;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Command-line entry point for the four-line JDBC query program. */
public final class JdbcQueryApplication {
    public static void main(String[] args) {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(System.in))) {
            QueryInput input = new QueryInputReader().read(reader);
            new JdbcQueryExecutor(
                    new DriverManagerConnectionProvider(),
                    new ConsoleRowPrinter()
            ).execute(input);
        } catch (Exception exception) {
            System.err.println("Database query failed: " + exception.getMessage());
        }
    }

    private JdbcQueryApplication() {
    }
}
