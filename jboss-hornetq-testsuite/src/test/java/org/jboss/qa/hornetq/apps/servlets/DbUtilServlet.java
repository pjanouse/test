package org.jboss.qa.hornetq.apps.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

/**
 * Servlet is used to work with oracle db.
 * @author mnovak
 */
public class DbUtilServlet extends HttpServlet {

    // Logger
    private static final Logger log = Logger.getLogger(DbUtilServlet.class.getName());
    private DataSource dataSource;
    
    
    @Resource(name = "lodhDb", mappedName = "java:/jdbc/lodhDS")
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @see {@link HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * @see {@link HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }


    /**
     * Process requests
     * @param request
     * @param response
     * @param queueIn
     * @param queueOut
     * @throws ServletException
     * @throws IOException
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        PrintWriter out = response.getWriter();
        String op = request.getParameter("op");
        try {
            
            if (op != null) {
                if (op.equals("deleteRecords")) {
                    deleteAll(out);
                } else if (op.equals("countAll")) {
                    countAll(out);
                } else if (op.equals("insertRecord")) {
                    insertRecord(out);
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
            PreparedStatement ps = (PreparedStatement) connection.prepareStatement("DELETE FROM MessageInfo");
            int deleted = ps.executeUpdate();
            out.println("Deleted records :" + deleted);
            ps.close();
            connection.commit();
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {}
            }
        }
    }
    
    public void insertRecord(PrintWriter out) {
        
        Connection connection = null;
        
        try {
            connection = getConnection();
            int counter =5;
            
            PreparedStatement ps = (PreparedStatement) connection.prepareStatement("INSERT INTO MESSAGEINFO"
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
                } catch (SQLException ex) {}
            }
        }
    }
    
    private Connection getConnection() throws SQLException  {
        
        return dataSource.getConnection();
        
    }

    public long countAll(PrintWriter out) {
        
        long result = 0;
        Connection connection = null;
        try {
            
            connection = getConnection();
            PreparedStatement ps = (PreparedStatement) connection.prepareStatement("SELECT COUNT(*) FROM MessageInfo");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result = rs.getLong(1);
            }
            rs.close();
            out.println("Records in DB :" + result);
            ps.close();
            connection.commit();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null)  {
                try {
                    connection.close();
                } catch (SQLException ex) {}
            }
        }

        return result;
    }
}