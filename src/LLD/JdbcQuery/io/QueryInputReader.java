package LLD.JdbcQuery.io;

import LLD.JdbcQuery.model.QueryInput;
import java.io.BufferedReader;
import java.io.IOException;

/** Reads the four-line JDBC query contract from an input stream. */
public final class QueryInputReader {
    public QueryInput read(BufferedReader reader) throws IOException {
        String url = readRequiredLine(reader, "JDBC URL");
        String username = readRequiredLine(reader, "username");
        String password = readRequiredLine(reader, "password");
        String query = readRequiredLine(reader, "query");
        return new QueryInput(url, username, password, query);
    }

    private String readRequiredLine(BufferedReader reader, String field) throws IOException {
        String value = reader.readLine();
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is missing");
        }
        return value;
    }
}
