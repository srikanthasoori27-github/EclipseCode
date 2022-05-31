package Coding;

import java.io.*;
import java.sql.*;
 
/**
 * A simple Java program that exports data from database to CSV file.
 * @author Nam Ha Minh
 * (C) Copyright codejava.net
 */
public class RoleExporter {
 
    public static void main(String[] args) {
        String jdbcURL = "jdbc:mysql://localhost:3306/sales";
        String username = "root";
        String password = "password";
         
        String csvFilePath = "Reviews-export.csv";
         
        try (Connection connection = DriverManager.getConnection(jdbcURL, username, password)) {
            String sql = "SELECT * FROM review";
             
            Statement statement = connection.createStatement();
             
            ResultSet result = statement.executeQuery(sql);
             
            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(csvFilePath));
             
            // write header line containing column names       
            fileWriter.write("course_name,student_name,timestamp,rating,comment");
             
            while (result.next()) {
                String courseName = result.getString("course_name");
                String studentName = result.getString("student_name");
                float rating = result.getFloat("rating");
                Timestamp timestamp = result.getTimestamp("timestamp");
                String comment = result.getString("comment");
                 
                if (comment == null) {
                    comment = "";   // write empty value for null
                } else {
                    comment = "\"" + comment + "\""; // escape double quotes
                }
                 
                String line = String.format("\"%s\",%s,%.1f,%s,%s",
                        courseName, studentName, rating, timestamp, comment);
                 
                fileWriter.newLine();
                fileWriter.write(line);            
            }
             
            statement.close();
            fileWriter.close();
             
        } catch (SQLException e) {
            System.out.println("Datababse error:");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("File IO error:");
            e.printStackTrace();
        }
         
    }
 
}