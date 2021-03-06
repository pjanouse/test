package org.jboss.qa.hornetq.apps.servlets;

import org.jboss.qa.hornetq.HornetQTestCaseConstants;

import javax.annotation.Resource;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet is used to work with oracle db.
 *
 * @author mnovak
 */
public class DbUtilServlet extends HttpServlet {

    // Logger
    private static final Logger log = Logger.getLogger(DbUtilServlet.class.getName());
    private DataSource dataSource;

    public void initializeDatasource() {

        Context context = null;
        try {
            context = new InitialContext();
            this.dataSource = (DataSource) context.lookup("java:/jdbc/lodhDS");
        } catch (NamingException ex) {
            ex.printStackTrace();
            log.log(Level.SEVERE, ex.getMessage(), ex);
        } finally {
            try {
                if (context != null)
                    context.close();
            } catch (NamingException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @see {@link HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @see {@link HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }


    /**
     * Process requests
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        initializeDatasource();
        PrintWriter out = response.getWriter();
        String op = request.getParameter("op");
        try {

            if (op != null) {
                if (op.equals("deleteRecords")) {
                    deleteAll(out);
                } else if (op.equals("rollbackPreparedTransactions")) {
                    rollbackPreparedTransactions(out, request.getParameter("database"), request.getParameter("owner"));
                } else if (op.equals("countAll")) {
                    countAll(out);
                } else if (op.equals("insertRecord")) {
                    insertRecord(out);
                } else if (op.equals("printAll")) {
                    printAll(out);
                } else {
                    out.println("Operation: " + op + " is not supoported.");
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            throw new IOException(e);
        } finally {
            out.close();
        }
    }

    public void deleteAll(PrintWriter out) {

        Connection connection = null;

        try {
            connection = getConnection();
//            PreparedStatement ps = (PreparedStatement) connection.prepareStatement("DELETE FROM MessageInfo");
//            String sql = "DROP TABLE MESSAGE_INFO2 PURGE";
            String sql = "delete from MESSAGE_INFO2";
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.executeUpdate();
            ps.close();

            String deleteTableSql = "drop table MESSAGE_INFO2";
            PreparedStatement deleteTable = connection.prepareStatement(deleteTableSql);
            deleteTable.execute();
            deleteTable.close();

            log.info("Table deleted - sql command: " + sql);
        } catch (java.sql.SQLSyntaxErrorException ex) {
            out.println(ex.getMessage());
            ex.printStackTrace();
        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println(ex.getMessage());
        }

        try {

            String sql = "create table MESSAGE_INFO2 (MESSAGE_ID VARCHAR(50) primary key not null, MESSAGE_NAME VARCHAR(50), MESSAGE_ADDRESS VARCHAR(50))";
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.executeUpdate();
            ps.close();
            log.info("SQL command: " + sql + " executed.");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                }
            }
        }
    }

    public void insertRecord(PrintWriter out) {

        Connection connection = null;

        try {
            connection = getConnection();
            int counter = 5;

            PreparedStatement ps = connection.prepareStatement("INSERT INTO MESSAGE_INFO2"
                    + "(MESSAGE_ID, MESSAGE_NAME, MESSAGE_ADDRESS) VALUES  (?, ?, ?)");
            ps.setString(1, "myid");
            ps.setString(2, "name");
            ps.setString(3, "address");
            out.println("sql: " + ps.toString());
            ps.executeUpdate();

            ps.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                }
            }
        }
    }

    private Connection getConnection() throws SQLException {

        return dataSource.getConnection();

    }

    public void rollbackPreparedTransactions(PrintWriter out, String database, String owner) {

        out.println("Calling rollback for prepared transactions for owner " + owner + " and database " + database + ":");

        int count = 0;
        if (HornetQTestCaseConstants.POSTGRESQLPLUS92.equals(database)) {
            Connection connection = null;
            try {

                connection = getConnection();
                String selectPreparedTransactions = "select gid from pg_prepared_xacts where owner = '" + owner + "'; ";
                out.println("Select: " + selectPreparedTransactions);
                PreparedStatement ps = connection.prepareStatement(selectPreparedTransactions);
                ResultSet rs = ps.executeQuery();

                PreparedStatement ps1;
                while (rs.next()) {
                    out.println(rs.getString(1));
                    ps1 = connection.prepareStatement("ROLLBACK PREPARED '" + rs.getString(1) + "';"); // rollback transaction with gid xyz
                    ps1.execute();
                    ps1.close();
                    count++;
                }
                rs.close();
                ps.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException ex) {
                    }
                }
            }
        } else {
            out.println("There are no SQL commands for rollbacking prepared transactions from DB " + database);
        }
        out.println("Number of prepared transactions:" + count);
    }

    public long countAll(PrintWriter out) {

        long result = 0;
        Connection connection = null;
        try {

            connection = getConnection();
            PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM MESSAGE_INFO2");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result = rs.getLong(1);
            }
            rs.close();
            out.println("Records in DB :" + result);
            ps.close();
//            connection.commit();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                }
            }
        }

        return result;
    }

    public void printAll(PrintWriter out) {

        String result = null;
        Connection connection = null;
        try {

            connection = getConnection();
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM MESSAGE_INFO2");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result = rs.getString(1);
                out.print(result + ",");
            }
            rs.close();
            ps.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                }
            }
        }
    }
}