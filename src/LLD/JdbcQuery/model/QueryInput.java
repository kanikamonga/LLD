package LLD.JdbcQuery.model;

/** Immutable command containing JDBC credentials and a read-only query. */
public final class QueryInput {
    private final String url;
    private final String username;
    private final String password;
    private final String query;

    public QueryInput(String url, String username, String password, String query) {
        this.url = required(url, "JDBC URL");
        this.username = required(username, "username");
        this.password = required(password, "password");
        this.query = required(query, "query");
        if (!query.trim().toLowerCase().startsWith("select")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed");
        }
    }

    public String url() { return url; }
    public String username() { return username; }
    public String password() { return password; }
    public String query() { return query; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
