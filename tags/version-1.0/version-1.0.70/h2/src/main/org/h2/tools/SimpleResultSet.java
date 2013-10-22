/*
 * Copyright 2004-2008 H2 Group. Licensed under the H2 License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.tools;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;

/*## Java 1.6 begin ##
import java.sql.NClob;
import java.sql.RowId;
import java.sql.SQLXML;
## Java 1.6 end ##*/

/**
 * This class is a simple result set and meta data implementation.
 * It can be used in Java functions that return a result set.
 * Only the most basic methods are implemented, the others throw an exception.
 * This implementation is standalone, and only relies on standard classes.
 * It can be extended easily if required.
 *
 * An application can create a result set using the following code:
 *
 * <pre>
 * SimpleResultSet rs = new SimpleResultSet();
 * rs.addColumn(&quot;ID&quot;, Types.INTEGER, 10, 0);
 * rs.addColumn(&quot;NAME&quot;, Types.VARCHAR, 255, 0);
 * rs.addRow(new Object[] { new Integer(0), &quot;Hello&quot; });
 * rs.addRow(new Object[] { new Integer(1), &quot;World&quot; });
 * </pre>
 *
 */
public class SimpleResultSet implements ResultSet, ResultSetMetaData {

    private ArrayList rows;
    private Object[] currentRow;
    private int rowId = -1;
    private boolean wasNull;
    private SimpleRowSource source;
    private ArrayList columns = new ArrayList();

    private static class Column {
        String name;
        int sqlType;
        int precision;
        int scale;
    }

    /**
     * A simple array implementation,
     * backed by an object array
     */
    private static class SimpleArray implements Array {
        
        private Object[] value;
        
        private SimpleArray(Object[] value) {
            this.value = value;
        }

        /**
         * Get the object array.
         * 
         * @return the object array
         */
        public Object getArray() throws SQLException {
            return value;
        }

        /**
         * INTERNAL 
         */
        public Object getArray(Map map) throws SQLException {
            throw getUnsupportedException();
        }

        /**
         * INTERNAL 
         */
        public Object getArray(long index, int count) throws SQLException {
            throw getUnsupportedException();
        }

        /**
         * INTERNAL 
         */
        public Object getArray(long index, int count, Map map) throws SQLException {
            throw getUnsupportedException();
        }

        /**
         * Get the base type of this array.
         * 
         * @return Types.NULL
         */
        public int getBaseType() throws SQLException {
            return Types.NULL;
        }

        /**
         * Get the base type name of this array.
         * 
         * @return "NULL"
         */
        public String getBaseTypeName() throws SQLException {
            return "NULL";
        }

        /**
         * INTERNAL 
         */
        public ResultSet getResultSet() throws SQLException {
            throw getUnsupportedException();
        }

        /**
         * INTERNAL 
         */
        public ResultSet getResultSet(Map map) throws SQLException {
            throw getUnsupportedException();
        }

        /**
         * INTERNAL 
         */
        public ResultSet getResultSet(long index, int count) throws SQLException {
            throw getUnsupportedException();
        }

        /**
         * INTERNAL 
         */
        public ResultSet getResultSet(long index, int count, Map map) throws SQLException {
            throw getUnsupportedException();
        }

        /**
         * INTERNAL 
         */
        public void free() throws SQLException {
        }

    }

    /**
     * This constructor is used if the result set is later populated with addRow.
     */
    public SimpleResultSet() {
        rows = new ArrayList();
    }

    /**
     * This constructor is used if the result set should retrieve the rows using
     * the specified row source object.
     * 
     * @param source the row source
     */
    public SimpleResultSet(SimpleRowSource source) {
        this.source = source;
    }

    /**
     * Adds a column to the result set.
     *
     * @param name null is replaced with C1, C2,...
     * @param sqlType the value returned in getColumnType(..) (ignored internally)
     * @param precision the precision
     * @param scale the scale
     * @throws SQLException
     */
    public void addColumn(String name, int sqlType, int precision, int scale) throws SQLException {
        if (rows != null && rows.size() > 0) {
            throw new SQLException("Cannot add a column after adding rows", "21S02");
        }
        if (name == null) {
            name = "C" + (columns.size() + 1);
        }
        Column column = new Column();
        column.name = name;
        column.sqlType = sqlType;
        column.precision = precision;
        column.scale = scale;
        columns.add(column);
    }

    /**
     * Add a new row to the result set.
     *
     * @param row the row as an array of objects
     */
    public void addRow(Object[] row) throws SQLException {
        if (rows == null) {
            throw new SQLException("Cannot add a row when using RowSource", "21S02");
        }
        rows.add(row);
    }

    /**
     * Returns ResultSet.CONCUR_READ_ONLY.
     *
     * @return CONCUR_READ_ONLY
     */
    public int getConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }

    /**
     * Returns ResultSet.FETCH_FORWARD.
     *
     * @return FETCH_FORWARD
     */
    public int getFetchDirection() throws SQLException {
        return ResultSet.FETCH_FORWARD;
    }

    /**
     * Returns 0.
     *
     * @return 0
     */
    public int getFetchSize() throws SQLException {
        return 0;
    }

    /**
     * Returns the row number (1, 2,...) or 0 for no row.
     *
     * @return 0
     */
    public int getRow() throws SQLException {
        return rowId + 1;
    }

    /**
     * Returns ResultSet.TYPE_FORWARD_ONLY.
     *
     * @return TYPE_FORWARD_ONLY
     */
    public int getType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    /**
     * Closes the result set and releases the resources.
     */
    public void close() throws SQLException {
        currentRow = null;
        rows = null;
        columns = null;
        rowId = -1;
        if (source != null) {
            source.close();
            source = null;
        }
    }

    /**
     * Moves the cursor to the next row of the result set.
     *
     * @return true if successful, false if there are no more rows
     */
    public boolean next() throws SQLException {
        if (source != null) {
            rowId++;
            currentRow = source.readRow();
            if (currentRow != null) {
                return true;
            }
        } else if (rows != null && rowId < rows.size()) {
            rowId++;
            if (rowId < rows.size()) {
                currentRow = (Object[]) rows.get(rowId);
                return true;
            }
        }
        close();
        return false;
    }

    /**
     * Moves the current position to before the first row, that means resets the
     * result set.
     * 
     * @throws SQLException is this method is not supported
     */
    public void beforeFirst() throws SQLException {
        rowId = -1;
        if (source != null) {
            source.reset();
        }
    }

    /**
     * Returns whether the last column accessed was null.
     *
     * @return true if the last column accessed was null
     */
    public boolean wasNull() throws SQLException {
        return wasNull;
    }

    /**
     * Returns the value as a byte.
     *
     * @return the value
     */
    public byte getByte(int columnIndex) throws SQLException {
        Object o = get(columnIndex);
        if (o != null && !(o instanceof Number)) {
            o = Byte.decode(o.toString());
        }
        return o == null ? 0 : ((Number) o).byteValue();
    }

    /**
     * Returns the value as an double.
     *
     * @return the value
     */
    public double getDouble(int columnIndex) throws SQLException {
        Object o = get(columnIndex);
        if (o != null && !(o instanceof Number)) {
            return Double.parseDouble(o.toString());
        }
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    /**
     * Returns the value as a float.
     *
     * @return the value
     */
    public float getFloat(int columnIndex) throws SQLException {
        Object o = get(columnIndex);
        if (o != null && !(o instanceof Number)) {
            return Float.parseFloat(o.toString());
        }
        return o == null ? 0 : ((Number) o).floatValue();
    }

    /**
     * Returns the value as an int.
     *
     * @return the value
     */
    public int getInt(int columnIndex) throws SQLException {
        Object o = get(columnIndex);
        if (o != null && !(o instanceof Number)) {
            o = Integer.decode(o.toString());
        }
        return o == null ? 0 : ((Number) o).intValue();
    }

    /**
     * Returns the value as a long.
     *
     * @return the value
     */
    public long getLong(int columnIndex) throws SQLException {
        Object o = get(columnIndex);
        if (o != null && !(o instanceof Number)) {
            o = Long.decode(o.toString());
        }
        return o == null ? 0 : ((Number) o).longValue();
    }

    /**
     * Returns the value as a short.
     *
     * @return the value
     */
    public short getShort(int columnIndex) throws SQLException {
        Object o = get(columnIndex);
        if (o != null && !(o instanceof Number)) {
            o = Short.decode(o.toString());
        }
        return o == null ? 0 : ((Number) o).shortValue();
    }

    /**
     * Returns the value as a boolean.
     *
     * @return the value
     */
    public boolean getBoolean(int columnIndex) throws SQLException {
        Object o = get(columnIndex);
        if (o != null && !(o instanceof Boolean)) {
            o = Boolean.valueOf(o.toString());
        }
        return o == null ? false : ((Boolean) o).booleanValue();
    }

    /**
     * Returns the value as a byte array.
     *
     * @return the value
     */
    public byte[] getBytes(int columnIndex) throws SQLException {
        return (byte[]) get(columnIndex);
    }

    /**
     * Returns the value as an Object.
     *
     * @return the value
     */
    public Object getObject(int columnIndex) throws SQLException {
        return get(columnIndex);
    }

    /**
     * Returns the value as a String.
     *
     * @return the value
     */
    public String getString(int columnIndex) throws SQLException {
        Object o = get(columnIndex);
        return o == null ? null : o.toString();
    }

    /**
     * Returns the value as a byte.
     *
     * @return the value
     */
    public byte getByte(String columnName) throws SQLException {
        return getByte(findColumn(columnName));
    }

    /**
     * Returns the value as a double.
     *
     * @return the value
     */
    public double getDouble(String columnName) throws SQLException {
        return getDouble(findColumn(columnName));
    }

    /**
     * Returns the value as a float.
     *
     * @return the value
     */
    public float getFloat(String columnName) throws SQLException {
        return getFloat(findColumn(columnName));
    }

    /**
     * Searches for a specific column in the result set. A case-insensitive
     * search is made.
     * 
     * @param columnName the name of the column label
     * @return the column index (1,2,...)
     * @throws SQLException if the column is not found or if the result set is
     *             closed
     */
    public int findColumn(String columnName) throws SQLException {
        for (int i = 0; columnName != null && columns != null && i < columns.size(); i++) {
            if (columnName.equalsIgnoreCase(getColumn(i).name)) {
                return i + 1;
            }
        }
        throw new SQLException("Column not found: " + columnName, "42S22");
    }

    /**
     * Returns the value as an int.
     *
     * @return the value
     */
    public int getInt(String columnName) throws SQLException {
        return getInt(findColumn(columnName));
    }

    /**
     * Returns the value as a long.
     *
     * @return the value
     */
    public long getLong(String columnName) throws SQLException {
        return getLong(findColumn(columnName));
    }

    /**
     * Returns the value as a short.
     *
     * @return the value
     */
    public short getShort(String columnName) throws SQLException {
        return getShort(findColumn(columnName));
    }

    /**
     * Returns the value as a boolean.
     *
     * @return the value
     */
    public boolean getBoolean(String columnName) throws SQLException {
        return getBoolean(findColumn(columnName));
    }

    /**
     * Returns the value as a byte array.
     *
     * @return the value
     */
    public byte[] getBytes(String columnName) throws SQLException {
        return getBytes(findColumn(columnName));
    }

    /**
     * Returns the value as a java.math.BigDecimal.
     *
     * @return the value
     */
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        Object o = get(columnIndex);
        if (o != null && !(o instanceof BigDecimal)) {
            o = new BigDecimal(o.toString());
        }
        return (BigDecimal) o;
    }

    /**
     * Returns the value as an java.sql.Date.
     *
     * @return the value
     */
    public Date getDate(int columnIndex) throws SQLException {
        return (Date) get(columnIndex);
    }

    /**
     * Returns a reference to itself.
     *
     * @return this
     */
    public ResultSetMetaData getMetaData() throws SQLException {
        return this;
    }

    /**
     * Returns null.
     *
     * @return null
     */
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    /**
     * Returns null.
     *
     * @return null
     */
    public Statement getStatement() throws SQLException {
        return null;
    }

    /**
     * Returns the value as an java.sql.Time.
     *
     * @return the value
     */
    public Time getTime(int columnIndex) throws SQLException {
        return (Time) get(columnIndex);
    }

    /**
     * Returns the value as an java.sql.Timestamp.
     *
     * @return the value
     */
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        return (Timestamp) get(columnIndex);
    }
    
    /**
     * Returns the value as a java.sql.Array.
     *
     * @return the value
     */
    public Array getArray(int columnIndex) throws SQLException {
        return new SimpleArray((Object[]) get(columnIndex));
    }

    /**
     * Returns the value as an Object.
     *
     * @return the value
     */
    public Object getObject(String columnName) throws SQLException {
        return getObject(findColumn(columnName));
    }

    /**
     * Returns the value as a String.
     *
     * @return the value
     */
    public String getString(String columnName) throws SQLException {
        return getString(findColumn(columnName));
    }

    /**
     * Returns the value as a java.math.BigDecimal.
     *
     * @return the value
     */
    public BigDecimal getBigDecimal(String columnName) throws SQLException {
        return getBigDecimal(findColumn(columnName));
    }

    /**
     * Returns the value as a java.sql.Date.
     *
     * @return the value
     */
    public Date getDate(String columnName) throws SQLException {
        return getDate(findColumn(columnName));
    }

    /**
     * Returns the value as a java.sql.Time.
     *
     * @return the value
     */
    public Time getTime(String columnName) throws SQLException {
        return getTime(findColumn(columnName));
    }

    /**
     * Returns the value as a java.sql.Timestamp.
     *
     * @return the value
     */
    public Timestamp getTimestamp(String columnName) throws SQLException {
        return getTimestamp(findColumn(columnName));
    }
    
    /**
     * Returns the value as a java.sql.Array.
     *
     * @return the value
     */
    public Array getArray(String columnName) throws SQLException {
        return getArray(findColumn(columnName));
    }
    
    
    // ---- result set meta data ---------------------------------------------

    /**
     * Returns the column count.
     *
     * @return the column count
     */
    public int getColumnCount() throws SQLException {
        return columns.size();
    }

    /**
     * Returns 15.
     *
     * @return 15
     */
    public int getColumnDisplaySize(int columnIndex) throws SQLException {
        return 15;
    }

    /**
     * Returns the SQL type.
     *
     * @return the SQL type
     */
    public int getColumnType(int columnIndex) throws SQLException {
        return getColumn(columnIndex - 1).sqlType;
    }

    /**
     * Returns the precision.
     *
     * @return the precision
     */
    public int getPrecision(int columnIndex) throws SQLException {
        return getColumn(columnIndex - 1).precision;
    }

    /**
     * Returns the scale.
     *
     * @return the scale
     */
    public int getScale(int columnIndex) throws SQLException {
        return getColumn(columnIndex - 1).scale;
    }

    /**
     * Returns ResultSetMetaData.columnNullableUnknown.
     *
     * @return columnNullableUnknown
     */
    public int isNullable(int columnIndex) throws SQLException {
        return ResultSetMetaData.columnNullableUnknown;
    }

    /**
     * Returns false.
     *
     * @return false
     */
    public boolean isAutoIncrement(int columnIndex) throws SQLException {
        return false;
    }

    /**
     * Returns true.
     *
     * @return true
     */
    public boolean isCaseSensitive(int columnIndex) throws SQLException {
        return true;
    }

    /**
     * Returns false.
     *
     * @return false
     */
    public boolean isCurrency(int columnIndex) throws SQLException {
        return false;
    }

    /**
     * Returns false.
     *
     * @return false
     */
    public boolean isDefinitelyWritable(int columnIndex) throws SQLException {
        return false;
    }

    /**
     * Returns true.
     *
     * @return true
     */
    public boolean isReadOnly(int columnIndex) throws SQLException {
        return true;
    }

    /**
     * Returns true.
     *
     * @return true
     */
    public boolean isSearchable(int columnIndex) throws SQLException {
        return true;
    }

    /**
     * Returns true.
     *
     * @return true
     */
    public boolean isSigned(int columnIndex) throws SQLException {
        return true;
    }

    /**
     * Returns false.
     *
     * @return false
     */
    public boolean isWritable(int columnIndex) throws SQLException {
        return false;
    }

    /**
     * Returns null.
     *
     * @return null
     */
    public String getCatalogName(int columnIndex) throws SQLException {
        return null;
    }

    /**
     * Returns null.
     *
     * @return null
     */
    public String getColumnClassName(int columnIndex) throws SQLException {
        return null;
    }

    /**
     * Returns the column name.
     *
     * @return the column name
     */
    public String getColumnLabel(int columnIndex) throws SQLException {
        return getColumn(columnIndex - 1).name;
    }

    /**
     * Returns the column name.
     *
     * @return the column name
     */
    public String getColumnName(int columnIndex) throws SQLException {
        return getColumnLabel(columnIndex);
    }

    /**
     * Returns null.
     *
     * @return null
     */
    public String getColumnTypeName(int columnIndex) throws SQLException {
        return null;
    }

    /**
     * Returns null.
     *
     * @return null
     */
    public String getSchemaName(int columnIndex) throws SQLException {
        return null;
    }

    /**
     * Returns null.
     *
     * @return null
     */
    public String getTableName(int columnIndex) throws SQLException {
        return null;
    }

    // ---- unsupported / result set ---------------------------------------------

    /** 
     * INTERNAL 
     */
    public void clearWarnings() throws SQLException {
    }

    /** 
     * INTERNAL 
     */
    public void afterLast() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void cancelRowUpdates() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNull(String columnName) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void deleteRow() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void insertRow() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void moveToCurrentRow() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void moveToInsertRow() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void refreshRow() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateRow() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean first() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean isAfterLast() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean isBeforeFirst() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean isFirst() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean isLast() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean last() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean previous() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean rowDeleted() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean rowInserted() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean rowUpdated() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void setFetchDirection(int direction) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void setFetchSize(int rows) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNull(int columnIndex) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean absolute(int row) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public boolean relative(int rows) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateByte(int columnIndex, byte x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateDouble(int columnIndex, double x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateFloat(int columnIndex, float x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateInt(int columnIndex, int x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateLong(int columnIndex, long x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateShort(int columnIndex, short x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        return null;
    }

    /** 
     * INTERNAL 
     */
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        return null;
    }

    /** 
     * @deprecated INTERNAL 
     */
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        return null;
    }

    /** 
     * INTERNAL 
     */
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateObject(int columnIndex, Object x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateObject(int columnIndex, Object x, int scale) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public String getCursorName() throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateString(int columnIndex, String x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateByte(String columnName, byte x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateDouble(String columnName, double x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateFloat(String columnName, float x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateInt(String columnName, int x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateLong(String columnName, long x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateShort(String columnName, short x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBoolean(String columnName, boolean x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBytes(String columnName, byte[] x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * @deprecated INTERNAL 
     */
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public URL getURL(int columnIndex) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateArray(int columnIndex, Array x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Blob getBlob(int i) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBlob(int columnIndex, Blob x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Clob getClob(int i) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateClob(int columnIndex, Clob x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateDate(int columnIndex, Date x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Ref getRef(int i) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateRef(int columnIndex, Ref x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateTime(int columnIndex, Time x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public InputStream getAsciiStream(String columnName) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public InputStream getBinaryStream(String columnName) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * @deprecated INTERNAL 
     */
    public InputStream getUnicodeStream(String columnName) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateAsciiStream(String columnName, InputStream x, int length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBinaryStream(String columnName, InputStream x, int length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Reader getCharacterStream(String columnName) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateCharacterStream(String columnName, Reader reader, int length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateObject(String columnName, Object x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateObject(String columnName, Object x, int scale) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Object getObject(int i, Map map) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateString(String columnName, String x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * @deprecated INTERNAL 
     */
    public BigDecimal getBigDecimal(String columnName, int scale) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBigDecimal(String columnName, BigDecimal x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public URL getURL(String columnName) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateArray(String columnName, Array x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Blob getBlob(String colName) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBlob(String columnName, Blob x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Clob getClob(String colName) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateClob(String columnName, Clob x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateDate(String columnName, Date x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Ref getRef(String colName) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateRef(String columnName, Ref x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateTime(String columnName, Time x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateTimestamp(String columnName, Timestamp x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Object getObject(String colName, Map map) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Date getDate(String columnName, Calendar cal) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Time getTime(String columnName, Calendar cal) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Timestamp getTimestamp(String columnName, Calendar cal) throws SQLException {
        throw getUnsupportedException();
    }

    // --- private -----------------------------

    private static SQLException getUnsupportedException() {
        return new SQLException("Feature not supported", "HYC00");
    }

    private void checkColumnIndex(int columnIndex) throws SQLException {
        if (columnIndex < 0 || columnIndex >= columns.size()) {
            throw new SQLException("Invalid column index " + (columnIndex + 1), "90009");
        }
    }

    private Object get(int columnIndex) throws SQLException {
        if (currentRow == null) {
            throw new SQLException("No data is available", "02000");
        }
        columnIndex--;
        checkColumnIndex(columnIndex);
        Object o = columnIndex < currentRow.length ? currentRow[columnIndex] : null;
        wasNull = o == null;
        return o;
    }

    private Column getColumn(int i) throws SQLException {
        checkColumnIndex(i);
        return (Column) columns.get(i);
    }

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public RowId getRowId(int columnIndex) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public RowId getRowId(String columnName) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public void updateRowId(String columnName, RowId x) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /**
     * Returns the current result set holdability.
     *
     * @return the holdability
     */
//## Java 1.4 begin ##
    public int getHoldability() {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }
//## Java 1.4 end ##

    /**
     * Returns whether this result set has been closed.
     *
     * @return true if the result set was closed
     */
    public boolean isClosed() throws SQLException {
        return rows == null;
    }

    /** 
     * INTERNAL 
     */
    public void updateNString(int columnIndex, String nString) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNString(String columnName, String nString) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public void updateNClob(String columnName, NClob nClob) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public NClob getNClob(int columnIndex) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public NClob getNClob(String columnName) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public SQLXML getSQLXML(String columnName) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) 
            throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public void updateSQLXML(String columnName, SQLXML xmlObject) 
            throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
    public String getNString(int columnIndex) throws SQLException {
        return getString(columnIndex);
    }

    /** 
     * INTERNAL 
     */
    public String getNString(String columnName) throws SQLException {
        return getString(columnName);
    }

    /** 
     * INTERNAL 
     */
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public Reader getNCharacterStream(String columnName) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNCharacterStream(String columnName, Reader x, int length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
/*## Java 1.6 begin ##
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        throw getUnsupportedException();
    }
## Java 1.6 end ##*/

    /** 
     * INTERNAL 
     */
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateAsciiStream(String columnName, InputStream x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateAsciiStream(String columnName, InputStream x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBinaryStream(int columnName, InputStream x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBinaryStream(String columnName, InputStream x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBinaryStream(String columnName, InputStream x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBlob(int columnIndex, InputStream x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBlob(String columnName, InputStream x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBlob(int columnIndex, InputStream x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateBlob(String columnName, InputStream x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateCharacterStream(String columnName, Reader x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateCharacterStream(String columnName, Reader x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateClob(int columnIndex, Reader x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateClob(String columnName, Reader x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateClob(int columnIndex, Reader x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateClob(String columnName, Reader x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNCharacterStream(String columnName, Reader x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNCharacterStream(String columnName, Reader x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNClob(int columnIndex, Reader x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNClob(String columnName, Reader x) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNClob(int columnIndex, Reader x, long length) throws SQLException {
        throw getUnsupportedException();
    }

    /** 
     * INTERNAL 
     */
    public void updateNClob(String columnName, Reader x, long length) throws SQLException {
        throw getUnsupportedException();
    }

}