package com.acme.training.rest;

import java.util.List;


import javax.ws.rs.*;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.acme.training.service.SearchService;

import sailpoint.rest.plugin.AllowAll;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;

/**
 * The resource class which defines the REST interface.
 * Each REST end point is defined, the appropriate authorization is checked
 * and the corresponding service method is called.
 *
 * @author 
 * 
 */
@Path("TrainingPlugin")
@Produces("application/json") 
@Consumes("application/json")
@AllowAll
public class SearchResource extends BasePluginResource {
    /**
     * The configurable plugin setting for the maximum returnable objects.
     */
    private static final String SETTING_SEARCH_OBJECT_COUNT = "maxSearchObjectCount";

    /**
     * The configured maximum search results value.
     */
    private int maxSearchObjects;
    
	@Override
	public String getPluginName() {
		return "TrainingPlugin";
	}
	
    /**
     * Gets a list of the requested objects that contain the specified string value.
     *
     * @param searchObject The name of the object type to search.
     * @param searchString The string for the object search.
     * @return The list of matching objects (names).
     * @throws Exception 
     *   
     */
    @GET
    @Path("search/{searchObject},{searchString}")
    @AllowAll
    public List<String> getResults(@PathParam("searchObject") String searchObject, @PathParam("searchString") String searchString) throws Exception {

       // Get configuration attribute from inherited method
       maxSearchObjects = getSettingInt(SETTING_SEARCH_OBJECT_COUNT);
       return getSearchService().searchObject(searchObject, searchString, maxSearchObjects);
    }
    

    
    /**
     * Gets a list of the object types from the search database.
     *
     * @return The list of object types.
     * @throws GeneralException
     */
    @GET
    @Path("search/objectNames")
    @AllowAll
    public List<String> getObjectNames() throws Exception{
        return getSearchService().getObjectList();
    }
    
    /**
     * Gets the class name for an object type.
     *
     * @param objectName The name of the object type.
     * @return The class name.
     * @throws GeneralException
     */
    @GET
    @Path("search/className/{objectName}")
    @AllowAll
    public String getClassName(@PathParam("objectName") String objectName) throws GeneralException {
        return getSearchService().getClassName(objectName);
    }
    
    /**
     * Adds the object type to the search database.
     *
     * @param objectName The display name of the object type.
     * @param objectClass The IdentityIQ class corresponding to the object type.
     * @throws GeneralException
     */
    @POST
    @Path("search/objectName/{objectName},{objectClass}")
    @AllowAll
    public void addObject(@PathParam("objectName") String objectName, @PathParam("objectClass") String objectClass) throws GeneralException {
        
    	getSearchService().addObject(objectName, objectClass);
    }
    
    /**
     * Removes the object type from the search database
     *
     * @param objectName The name of the object type to remove.
     * @throws GeneralException
     */
    @DELETE
    @Path("search/objectName/{objectName}")
    @AllowAll
    public void deleteObject(@PathParam("objectName") String objectName) throws GeneralException {

        getSearchService().removeObject(objectName);
    }
    
    /**
     * Gets an instance of the SearchService class.
     *
     * @return The service.
     */
    private SearchService getSearchService() {
        return new SearchService(this);
    }


}


