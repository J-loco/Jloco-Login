package org.starloco.locos.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/** Small JDBC helper: pooled connections, prepared statements only, unchecked failures. */
public final class Jdbc {

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }

    private final DataSource dataSource;

    public Jdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> Optional<T> one(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = list(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public <T> List<T> list(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = prepare(connection, sql, params);
                ResultSet rows = statement.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rows.next()) {
                result.add(mapper.map(rows));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException(sql, e);
        }
    }

    public boolean exists(String sql, Object... params) {
        return one(sql, row -> Boolean.TRUE, params).isPresent();
    }

    /** Number of affected rows. */
    public int update(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = prepare(connection, sql, params)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(sql, e);
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            return statement;
        } catch (SQLException | RuntimeException e) {
            statement.close();
            throw e;
        }
    }

    /** A database error; the SQL (never the parameters) is in the message. */
    public static final class DataAccessException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        DataAccessException(String sql, SQLException cause) {
            super("SQL failed: " + sql + " (" + cause.getMessage() + ")", cause);
        }
    }
}
