package com.acme.training.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.acme.training.util.SearchQuery;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.plugin.PluginBaseHelper;
import sailpoint.plugin.PluginContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;
import sailpoint.tools.ObjectNotFoundException;

/**
 * The service class does the work for the plugin.
 * Methods do the search - read from and update the search database
 *
 * @author 
 * 
 */
public class SearchService {
    /**
     * The plugin context.
     */
    private PluginContext pluginContext;

    /**
     * Constructor.
     *
     * @param pluginContext The plugin context.
     */
    public SearchService(PluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }
    
    /**
     * Adds the object type
     *
     * @param searchObject The name of the object type to search.
     * @param searchString The string for the object search.
     * @return The list of matching objects by name.
     * @throws GeneralException
     */
    public List<String> searchObject(String objectName, String searchString, int maxObjects) throws GeneralException {
        List<String> results = new ArrayList<String>(); 
        SailPointContext ctx = SailPointFactory.getCurrentContext();
                
        // Call method to get class name for the object from the search database
        String searchClass = null;
        try {
            searchClass = getClassName(objectName);
        }
        catch (Exception e) {
            results.add("Error: No entry in plugin database for object: " + objectName);                                 
            return results;
        }
        
        // get object of the class
        Class objClass = null;
        try {
            objClass = Class.forName(searchClass);
        }
        catch (Exception e) {
            results.add("Error obtaining class with name: " + searchClass);                                 
            return results;
        }
        
        // Search for objects of specified type containing search string.
        // Get all objects of the target up to the configured maximum. 
        // This provides a throttle to not bring in too many objects and perhaps overload the server.

        QueryOptions qo = new QueryOptions(); 
        qo.setResultLimit(maxObjects);
        
        @SuppressWarnings("unchecked")
	Iterator iter = ctx.search(objClass,qo);
        int count = 0;
        String check = null;
         
        // Iterate through the objects to see if any contain the string
        while (iter.hasNext()) {
            SailPointObject spObject = (SailPointObject)iter.next();
            check = spObject.toXml();
            if (check.contains(searchString)) {
                results.add(spObject.getName());
                count++;
            }
       }
       return results;
    }
    
    /**
     * Gets all the object types from the search database.
     *
     * @return The object names.
     * @throws GeneralException
     */
    public List<String> getObjectList() throws GeneralException {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            connection = PluginBaseHelper.getConnection();
            statement = PluginBaseHelper.prepareStatement(connection, SearchQuery.LIST_OBJECTS);
            resultSet = statement.executeQuery();

            List<String> objectList = new ArrayList<>();
            while (resultSet.next()) {
                objectList.add(resultSet.getString("name"));
            }

            return objectList;
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            IOUtil.closeQuietly(statement);
            IOUtil.closeQuietly(connection);
            IOUtil.closeQuietly(resultSet);
        }
    }

    /**
     * Gets the class name for the specified object.
     *
     * @param name The display name of the object type.
     * @return The class name for the object type.
     * @throws GeneralException
     */
    public String getClassName(String name) throws GeneralException {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            connection = PluginBaseHelper.getConnection();

            statement = PluginBaseHelper.prepareStatement(connection, SearchQuery.GET_OBJECT_CLASS, name);

            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("class_name");
            } else {
                throw new ObjectNotFoundException();
            }
        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            IOUtil.closeQuietly(statement);
            IOUtil.closeQuietly(connection);
            IOUtil.closeQuietly(resultSet);
        }
    }
    
    /**
     * Adds the object type to the database
     *
     * @param name The display name of the object type.
     * @param className The IdentityIQ class name for the object type.
     * @throws GeneralException
     */
    public void addObject(String name, String className) throws GeneralException {
        Connection connection = null;
        PreparedStatement statement = null;

        try {
            connection = PluginBaseHelper.getConnection();
            
            statement = PluginBaseHelper.prepareStatement(
                    connection, SearchQuery.ADD, name, className);

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            IOUtil.closeQuietly(statement);
            IOUtil.closeQuietly(connection);
        }
    }

    /**
     * Removes the object type from the database
     *
     * @param name The name of the object type.
     * @throws GeneralException
     */
    public void removeObject(String name) throws GeneralException {
        Connection connection = null;
        PreparedStatement statement = null;

        try {
            connection = PluginBaseHelper.getConnection();
            
            statement = PluginBaseHelper.prepareStatement(
                    connection, SearchQuery.DELETE, name);

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new GeneralException(e);
        } finally {
            IOUtil.closeQuietly(statement);
            IOUtil.closeQuietly(connection);
        }
    }





}
