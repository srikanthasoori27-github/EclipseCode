package com.acme.training.util;
/**
 * Utility class containing the queries performed by the
 * services on the search plugin tables.
 *
 * @author
 */
public class SearchQuery {
	/**
     * Variable to constrain number of objects to search through for specified string.
     */
	public static final String SETTING_SEARCH_OBJECT_COUNT = "maxSearchObjectCount";
    
    /**
     * Query to get all object names.
     */
    public static final String LIST_OBJECTS = "SELECT * FROM trainingPlugin_objects ORDER BY name";

    /**
     * Query to select a single class name by object name.
     */
    public static final String GET_OBJECT_CLASS = "SELECT class_name FROM trainingPlugin_objects WHERE name=?";
    
    /**
     * Add object into the search database.
     */
    public static final String ADD = "INSERT INTO trainingPlugin_objects (name, class_name) VALUES (?, ?)";
    
    /**
     * Delete object from the search database.
     */
    public static final String DELETE = "DELETE FROM trainingPlugin_objects WHERE name = ?";

    /**
     * Private constructor.
     */
    private SearchQuery() {}

}
