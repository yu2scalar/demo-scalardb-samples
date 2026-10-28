package com.example.demo_scalardb_samples.util;

import com.scalar.db.sql.Record;
import com.scalar.db.sql.*;

import java.util.*;

/**
 * Generic SQL execution utility (SQL-console-like).
 *
 * WARNING: SAMPLE ONLY — do NOT copy this pattern to production.
 * This path executes client-supplied SQL verbatim, with none of the
 * injection countermeasures the typed repositories (SqlRepository) apply.
 * Real applications should expose only fixed statements via the typed path.
 */
public class GenericSqlUtil {

    private final SqlSession sqlSession;

    public GenericSqlUtil(SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    public List<Map<String, Object>> executeQuery(String sql) {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSet resultSet = sqlSession.execute(sql);
        List<Record> records = resultSet.all();
        ColumnDefinitions columnDefinitions = resultSet.getColumnDefinitions();
        for (Record record : records) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < columnDefinitions.size(); i++) {
                String columnName = columnDefinitions.getColumnDefinition(i).getColumnName();
                com.scalar.db.sql.DataType type = columnDefinitions.getColumnDefinition(i).getDataType();
                row.put(columnName, getColumnValue(record, columnName, type));
            }
            results.add(row);
        }
        return results;
    }

    private Object getColumnValue(Record resultSet, String columnName, com.scalar.db.sql.DataType type) {
        return switch (type) {
            case BOOLEAN -> resultSet.getBoolean(columnName);
            case INT -> resultSet.getInt(columnName);
            case BIGINT -> resultSet.getBigInt(columnName);
            case FLOAT -> resultSet.getFloat(columnName);
            case DOUBLE -> resultSet.getDouble(columnName);
            case TEXT -> resultSet.getText(columnName);
            case BLOB -> resultSet.getBlobAsBytes(columnName);
            case DATE -> resultSet.getDate(columnName);
            case TIME -> resultSet.getTime(columnName);
            case TIMESTAMP -> resultSet.getTimestamp(columnName);
            case TIMESTAMPTZ -> resultSet.getTimestampTZ(columnName);
            default -> null; // Handle unknown types safely
        };
    }
}
