package de.diddiz.util;

import java.io.Closeable;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.*;

public class MySQLConnectionPool implements Closeable {
    private class ConnectionReaper extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(300000);
                } catch (final InterruptedException e) {
                }
                reapConnections();
            }
        }
    }

    private class JDCConnection implements Connection {
        private final Connection conn;
        private boolean inuse;
        private long timestamp;
        private int networkTimeout;
        private String schema;

        JDCConnection(final Connection conn) {
            this.conn = conn;
            this.inuse = false;
            this.timestamp = 0;
            this.networkTimeout = 25;
            this.schema = "default";
        }

        @SuppressWarnings("unused")
        public void abort(final Executor exec) throws SQLException {
            // Not implemented really...
        }

        @Override
        public void clearWarnings() throws SQLException {
            this.conn.clearWarnings();
        }

        @Override
        public void close() {
            this.inuse = false;
            try {
                if (!this.conn.getAutoCommit()) this.conn.setAutoCommit(true);
            } catch (final SQLException ex) {
                MySQLConnectionPool.this.connections.remove(this.conn);
                terminate();
            }
        }

        public void commit() throws SQLException {
            this.conn.commit();
        }

        public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
            return this.conn.createArrayOf(typeName, elements);
        }

        public Blob createBlob() throws SQLException {
            return this.conn.createBlob();
        }

        public Clob createClob() throws SQLException {
            return this.conn.createClob();
        }

        @Override
        public NClob createNClob() throws SQLException {
            return this.conn.createNClob();
        }

        @Override
        public SQLXML createSQLXML() throws SQLException {
            return this.conn.createSQLXML();
        }

        @Override
        public Statement createStatement() throws SQLException {
            return this.conn.createStatement();
        }

        @Override
        public Statement createStatement(final int resultSetType, final int resultSetConcurrency)
                throws SQLException {
            return this.conn.createStatement(resultSetType, resultSetConcurrency);
        }

        @Override
        public Statement createStatement(final int resultSetType, final int resultSetConcurrency,
                final int resultSetHoldability) throws SQLException {
            return this.conn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public Struct createStruct(final String typeName, final Object[] attributes) throws SQLException {
            return this.conn.createStruct(typeName, attributes);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            return this.conn.getAutoCommit();
        }

        @Override
        public String getCatalog() throws SQLException {
            return this.conn.getCatalog();
        }

        @Override
        public Properties getClientInfo() throws SQLException {
            return this.conn.getClientInfo();
        }

        @Override
        public String getClientInfo(final String name) throws SQLException {
            return this.conn.getClientInfo(name);
        }

        @Override
        public int getHoldability() throws SQLException {
            return this.conn.getHoldability();
        }

        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            return this.conn.getMetaData();
        }

        @SuppressWarnings("unused")
        public int getNetworkTimeout() throws SQLException {
            return this.networkTimeout;
        }

        @SuppressWarnings("unused")
        public String getSchema() throws SQLException {
            return this.schema;
        }

        @Override
        public int getTransactionIsolation() throws SQLException {
            return this.conn.getTransactionIsolation();
        }

        @Override
        public Map<String, Class<?>> getTypeMap() throws SQLException {
            return this.conn.getTypeMap();
        }

        @Override
        public SQLWarning getWarnings() throws SQLException {
            return this.conn.getWarnings();
        }

        @Override
        public boolean isClosed() throws SQLException {
            return this.conn.isClosed();
        }

        @Override
        public boolean isReadOnly() throws SQLException {
            return this.conn.isReadOnly();
        }

        @Override
        public boolean isValid(final int timeout) throws SQLException {
            return this.conn.isValid(timeout);
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) throws SQLException {
            return this.conn.isWrapperFor(iface);
        }

        @Override
        public String nativeSQL(final String sql) throws SQLException {
            return this.conn.nativeSQL(sql);
        }

        @Override
        public CallableStatement prepareCall(final String sql) throws SQLException {
            return this.conn.prepareCall(sql);
        }

        @Override
        public CallableStatement prepareCall(final String sql, final int resultSetType,
                final int resultSetConcurrency) throws SQLException {
            return this.conn.prepareCall(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public CallableStatement prepareCall(final String sql, final int resultSetType,
                final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
            return this.conn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql) throws SQLException {
            return this.conn.prepareStatement(sql);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys)
                throws SQLException {
            return this.conn.prepareStatement(sql, autoGeneratedKeys);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                final int resultSetConcurrency) throws SQLException {
            return this.conn.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
            return this.conn
                    .prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes)
                throws SQLException {
            return this.conn.prepareStatement(sql, columnIndexes);
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final String[] columnNames)
                throws SQLException {
            return this.conn.prepareStatement(sql, columnNames);
        }

        @Override
        public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
            this.conn.releaseSavepoint(savepoint);
        }

        @Override
        public void rollback() throws SQLException {
            this.conn.rollback();
        }

        @Override
        public void rollback(final Savepoint savepoint) throws SQLException {
            this.conn.rollback(savepoint);
        }

        @Override
        public void setAutoCommit(final boolean autoCommit) throws SQLException {
            this.conn.setAutoCommit(autoCommit);
        }

        @Override
        public void setCatalog(final String catalog) throws SQLException {
            this.conn.setCatalog(catalog);
        }

        @Override
        public void setClientInfo(final Properties properties) throws SQLClientInfoException {
            this.conn.setClientInfo(properties);
        }

        @Override
        public void setClientInfo(final String name, final String value) throws SQLClientInfoException {
            this.conn.setClientInfo(name, value);
        }

        @Override
        public void setHoldability(final int holdability) throws SQLException {
            this.conn.setHoldability(holdability);
        }

        @SuppressWarnings("unused")
        public void setNetworkTimeout(final Executor exec, final int timeout) throws SQLException {
            this.networkTimeout = timeout;
        }

        @Override
        public void setReadOnly(final boolean readOnly) throws SQLException {
            this.conn.setReadOnly(readOnly);
        }

        @Override
        public Savepoint setSavepoint() throws SQLException {
            return this.conn.setSavepoint();
        }

        @Override
        public Savepoint setSavepoint(final String name) throws SQLException {
            return this.conn.setSavepoint(name);
        }

        @SuppressWarnings("unused")
        public void setSchema(final String str) throws SQLException {
            this.schema = str;
        }

        @Override
        public void setTransactionIsolation(final int level) throws SQLException {
            this.conn.setTransactionIsolation(level);
        }

        @Override
        public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
            this.conn.setTypeMap(map);
        }

        @Override
        public <T> T unwrap(final Class<T> iface) throws SQLException {
            return this.conn.unwrap(iface);
        }

        long getLastUse() {
            return this.timestamp;
        }

        boolean inUse() {
            return this.inuse;
        }

        boolean isValid() {
            try {
                return this.conn.isValid(1);
            } catch (final SQLException ex) {
                return false;
            }
        }

        synchronized boolean lease() {
            if (this.inuse) return false;
            this.inuse = true;
            this.timestamp = System.currentTimeMillis();
            return true;
        }

        void terminate() {
            try {
                this.conn.close();
            } catch (final SQLException ex) {
            }
        }
    }

    private final static int poolsize = 10;
    private final static long timeToLive = 300000;
    final Vector<JDCConnection> connections;
    private final ConnectionReaper reaper;

    private final String url, user, password;

    private final Lock lock = new ReentrantLock();

    @SuppressWarnings("synthetic-access")
    public MySQLConnectionPool(final String url, final String user, final String password)
            throws ClassNotFoundException {
        Class.forName("com.mysql.jdbc.Driver");
        this.url = url;
        this.user = user;
        this.password = password;
        this.connections = new Vector<JDCConnection>(poolsize);
        this.reaper = new ConnectionReaper();
        this.reaper.start();
    }

    public void close() {
        this.lock.lock();
        final Enumeration<JDCConnection> conns = this.connections.elements();
        while (conns.hasMoreElements()) {
            final JDCConnection conn = conns.nextElement();
            this.connections.remove(conn);
            conn.terminate();
        }
        this.lock.unlock();
    }

    public Connection getConnection() throws SQLException {
        this.lock.lock();
        try {
            final Enumeration<JDCConnection> conns = this.connections.elements();
            while (conns.hasMoreElements()) {
                final JDCConnection conn = conns.nextElement();
                if (conn.lease()) {
                    if (conn.isValid()) return conn;
                    this.connections.remove(conn);
                    conn.terminate();
                }
            }
            final JDCConnection conn = new JDCConnection(DriverManager.getConnection(this.url, this.user,
                    this.password));
            conn.lease();
            if (!conn.isValid()) {
                conn.terminate();
                throw new SQLException("Failed to validate a brand new connection");
            }
            this.connections.add(conn);
            return conn;
        } finally {
            this.lock.unlock();
        }
    }

    void reapConnections() {
        this.lock.lock();
        final long stale = System.currentTimeMillis() - timeToLive;
        final Iterator<JDCConnection> itr = this.connections.iterator();
        while (itr.hasNext()) {
            final JDCConnection conn = itr.next();
            if (conn.inUse() && stale > conn.getLastUse() && !conn.isValid()) itr.remove();
        }
        if(connections.size() > 50) System.out.println("Warning: " + connections.size() + " connections are open.");
        this.lock.unlock();
    }
}
