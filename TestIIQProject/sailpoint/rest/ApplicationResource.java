/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ExceptionCleaner;
import sailpoint.api.Localizer;
import sailpoint.authorization.LcmEnabledAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorFactory;
import sailpoint.integration.ListResult;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.object.SPRight;
import sailpoint.object.Schema;
import sailpoint.service.ApplicationDTO;
import sailpoint.service.StatisticsService;
import sailpoint.service.application.ApplicationStatusDTO;
import sailpoint.service.application.ApplicationStatusService;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Localizable;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.extjs.GridColumn;
import sailpoint.web.extjs.GridField;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;


/**
 * A sub-resource for an application.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ApplicationResource extends BaseResource {    

    private static final Log log = LogFactory.getLog(ApplicationResource.class);

    // The name of the application we're dealing with.
    private String application;
    

    /**
     * Create an application resource for the given application.
     * 
     * @param  application  The name or ID of the application.
     * @param  parent       The parent of this subresource.
     */
    public ApplicationResource(String application, BaseResource parent) {
        super(parent);
        this.application = decodeRestUriComponent(application, false);
    }

    /**
     * Return the Application this resource is servicing.
     */
    private Application getApplication() throws GeneralException {
        Application app = getContext().getObjectById(Application.class, this.application);
        return getApplication(app);
    }
    
    private Application getApplicationByName() throws GeneralException {
        Application app = getContext().getObjectByName(Application.class, this.application);
        return getApplication(app);
    }
    
    private Application getApplication(Application app) throws ObjectNotFoundException {
        if (app == null) {
            throw new ObjectNotFoundException(Application.class, this.application);
        }
        return app;
    }

    /**
     * Attempts to find the Application either by ID or by name.
     * @return the Application
     * @throws GeneralException
     */
    private Application getApplicationByIdOrName() throws GeneralException {
        Application app = getContext().getObjectById(Application.class, this.application);
        if (app == null) {
            app = getContext().getObjectByName(Application.class, this.application);
        }
        if (app == null) {
            throw new ObjectNotFoundException(Application.class, this.application);
        }
        return app;
    }
    
    /**
     * Return a list of the instances for this application.
     */
    @GET
    @Path("summary")
    public ObjectResult getSummary() throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ViewApplication));

        Map<String, Object> appSummary = null;
        Localizer localizer = new Localizer(getContext());

        Application app = getApplicationByIdOrName();
        if (app != null){
            appSummary = new HashMap<String, Object>();
            appSummary.put("id", app.getId());
            appSummary.put("name", app.getName());
            
            appSummary.put("description", localizer.getLocalizedValue(app, Localizer.ATTR_DESCRIPTION, getLocale()));
            appSummary.put("type", app.getType());
            if (app.getOwner() != null){
                appSummary.put("ownerId", app.getOwner().getId());
                appSummary.put("owner", app.getOwner().getName());
                appSummary.put("ownerDisplayName", app.getOwner().getDisplayableName());
            }
            if (app.getRemediators() != null){
                List<Map<String, String>> remediators = new ArrayList<Map<String, String>>();
                for(Identity identity : app.getRemediators()){
                    Map<String, String> identityObj = new HashMap<String, String>();
                    identityObj.put("name", identity.getName());
                    identityObj.put("displayName", identity.getDisplayableName());
                    identityObj.put("id", identity.getId());
                    remediators.add(identityObj);
                }

                appSummary.put("remediators", remediators);
            }
        }

        ObjectResult result = new ObjectResult(appSummary);

        return result;
    }

    /**
     * Return a list of the instances for this application.
     */
    @GET @Path("instances")
    public ListResult getInstances() throws GeneralException {

        //Used in ApplicationSelector and RequestAccessSelectAccountPanel
    	authorize(new LcmEnabledAuthorizer());

        Application app = getApplication();
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("application", app));
        qo.add(Filter.notnull("instance"));
        qo.setDistinct(true);
        qo.addOrdering("instance", true);

        String props = "instance";
        Iterator<Object[]> instances = getContext().search(Link.class, qo, props);
        return createListResult(instances, props);
    }

    /**
     * Return the first 10 resource objects for the given application and
     * object type.
     * @param objectType Valid schema object type.
     * @return ListResult including resource objects, or if an
     * error occurs, a RequestResult with the failure message.
     *
     * NOTE: This requires name, because app may not yet have ID -rap
     */
    @GET @Path("testConnector/{objectType}")
    public RequestResult testConnector(@PathParam("objectType") String objectType) throws GeneralException{

        // Authorize the user. Currently we just check for the ManageApplication spright
        authorize(new RightAuthorizer(SPRight.ManageApplication));

        RequestResult result = null;

        GridResponseMetaData gridMeta = new GridResponseMetaData();
        gridMeta.addField(new GridField("id"));

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        CloseableIterator iterator = null;

        try {

            String error = null;

            // Try and get the app from the session. We store it on the session so we
            // can take into account any edits the user has made on the app page.
            // If it's not on the session, pull it from the database
            Application app = (Application)this.getSession().getAttribute("connectorDebug-" + application);
            if (app != null)
                this.getSession().removeAttribute("connectorDebug-" + application);
            else
                app = getContext().getObjectByName(Application.class, application);

            Schema schema = app.getSchema(objectType);
            Connector connector = ConnectorFactory.getConnector(app, null);
            if ( connector == null ) {
                error = localize(MessageKeys.APP_RES_TEST_CONNECTOR_NO_CONNECTOR, application);
            }

            if (Util.isNullOrEmpty(schema.getIdentityAttribute())){
                error = localize(MessageKeys.APP_RES_TEST_CONNECTOR_ERR_NOD_ID_ATTR, application);
            }

            // If an error has been encountered, bail
            if (error != null){
                result = new ListResult(new ArrayList(), 0);
                result.setErrors(Arrays.asList(error));
                result.setStatus(RequestResult.STATUS_FAILURE);
                result.setMetaData(gridMeta.asMap());
                return result;
            }

            List<String> interestingAttrs = new ArrayList<String>();

            //  Display attribute: The attribute designated as the "Display Attribute".
            String displayAttr = schema.getDisplayAttribute();
            if (!Util.isNullOrEmpty(displayAttr))
                interestingAttrs.add(displayAttr);

            // Native identity attribute:  The attribute designated as the "Identity Attribute".
            String nativeIdAttr = schema.getIdentityAttribute();
            if (!Util.isNullOrEmpty(nativeIdAttr))
                interestingAttrs.add(nativeIdAttr);

            // Instance attribute: The attribute designated as the "Instance Attribute".
            String instanceAttr = schema.getInstanceAttribute();
            if (!Util.isNullOrEmpty(instanceAttr))
                interestingAttrs.add(instanceAttr);

            // Add entitlements to our list of attribute to display
            if (schema.getEntitlementAttributeNames() != null){
                for(String attr : schema.getEntitlementAttributeNames() ){
                    if (!interestingAttrs.contains(attr))
                        interestingAttrs.add(attr);
                }
            }

            //All attributes that are marked as managed.
            for (AttributeDefinition def : schema.getAttributes()){
                if (!interestingAttrs.contains(def.getName()) && def.isManaged()){
                    interestingAttrs.add(def.getName());
                }
            }

             //Group attribute: The attributes designated as "Group Attributes"
            for(String attDefName : schema.getGroupAttributes()) {
                if (!Util.isNullOrEmpty(attDefName) && !interestingAttrs.contains(attDefName)) {
                    interestingAttrs.add(attDefName);
                }
            }

            //Permissions: The direct permissions granted to the account.
            // This is the Connector.ATTR_DIRECT_PERMISSIONS attribute on the ResourceObject.
            // If "Include Permissions" is not checked, this column will not be available.
            if (schema.includePermissions())
                interestingAttrs.add(Connector.ATTR_DIRECT_PERMISSIONS);

            //Any other attributes in the schema that do not fit the above criteria,
            // in the order that they appear in the schema.
            for (AttributeDefinition def : schema.getAttributes()){
                if (!interestingAttrs.contains(def.getName())){
                    interestingAttrs.add(def.getName());
                }
            }

            List<GridColumn> columns = new ArrayList<GridColumn>();
            for(String attr : interestingAttrs){
                attr = escapeAttributeName(attr);
                GridColumn col = new GridColumn(attr);
                col.setHeader(attr);
                col.setFlex(1F);
                if (columns.size() >= 10)
                    col.setHidden(true);
                columns.add(col);
            }

            gridMeta.addColumns(columns);
            
            /*Options is required to differentiate if the flow is from preview account or aggregation.
              This indicator will be used in AD Connector to disable cache functionality from preview account flow.*/
            Map<String, Object> options = new HashMap<String, Object>();
            options.put("isCallFromTestConnector", true);
            iterator = connector.iterateObjects(objectType, null, options);

            if ( iterator != null ) {
                int num = 0;
                while ( iterator.hasNext() && num <10) {

                    ResourceObject obj = (ResourceObject)iterator.next();

                    // check for the existence of a IIQSourceApplication attribute.
                    // These resource objs belong to a managed application and will
                    // not share a schema with the application we are examining, so they can be ignored.
                    if (obj.getAttributeNames() != null &&
                            !obj.getAttributeNames().contains(Connector.ATT_SOURCE_APPLICATION)){
                        Map<String, Object> objMap = new HashMap<String, Object>();

                        objMap.put("id", obj.getAttribute(nativeIdAttr));

                        for(String attr : interestingAttrs){
                            Object val = obj.getAttribute(attr);
                            objMap.put(escapeAttributeName(attr), localizeObject(val, getLocale(), getUserTimeZone()));
                        }

                        items.add(objMap);
                        num++;
                    }
                }
            }

            result = new ListResult(makeJsonSafeKeys(items), items.size());

            result.setMetaData(gridMeta.asMap());
        } catch (Throwable e) {
            log.error(e);
            result = new ListResult(new ArrayList(), 0);
            String msg = ExceptionCleaner.cleanConnectorException(e);
            if (msg == null)
                msg = localize(MessageKeys.APP_RES_TEST_CONNECTOR_EXCEPTION);
            result.setErrors(Arrays.asList(Util.escapeHTML(msg, false)));
            result.setStatus(RequestResult.STATUS_FAILURE);
            result.setMetaData(gridMeta.asMap());
        } finally{
            if (iterator != null)
                iterator.close();
        }

        return result;
    }

    /**
     * Escape the attribute name consistently for connector test results
     */
    private String escapeAttributeName(String attr) {
        //IIQHH-273 -- escape double quote in header which causes javascript error.
        // Double quotes are handled by escapeHTML, which is also needed for XSS handling.
        attr = Util.escapeHTML(attr, false);
        return attr;
    }


    private static Object localizeObject(Object obj, Locale locale, TimeZone tz){

        if (obj == null){
            return null;
        } else if (obj instanceof Localizable){
            return ((Localizable)obj).getLocalizedMessage(locale, tz);
        } else if (Collection.class.isAssignableFrom(obj.getClass())){
            List items = new ArrayList();
            Iterator iter = ((Collection)obj).iterator();
            while(iter.hasNext()){
                Object next = iter.next();
                items.add(localizeObject(next, locale, tz));
            }

            return items;
        }

        return obj;
    }
    
    @Path("links")
    public LinksResource getLinks() throws GeneralException {
        return new LinksResource(getApplication(), this);
    }
    
    /**
     * 
     * @return AccountSchmea Attributes for a specified application
     * @throws GeneralException
     */
    @GET
    @Path("schemaAttributes")
    public ListResult getApplicationSchemaAttributes() throws GeneralException {
        
        authorize(new RightAuthorizer("ViewApplication", "ManageApplication"));
        
        Application app = getApplication();
        int count = 0;
        List<Map<String, String>> schemaAttr = new ArrayList<Map<String,String>>();
        if (app != null){

            if(app.getAccountSchema() != null) {
                count += app.getAccountSchema().getAttributes().size();
                for(AttributeDefinition ad : app.getAccountSchema().getAttributes()) {
                    Map<String, String>schemaObj = new HashMap<String, String>();
                    //We really only need the name, but SailPoint Suggest component needs all three of these
                    schemaObj.put("name", ad.getName());
                    schemaObj.put("displayName", ad.getName());
                    schemaObj.put("id", ad.getName());
                    schemaObj.put("attrType", ad.getType());
                    schemaAttr.add(schemaObj);
                }
            }
                
            
        }
        ListResult result = new ListResult(schemaAttr, count);
        
        return result;
            
    }

    @GET
    @Path("status")
    public ApplicationStatusDTO getApplicationStatus(@QueryParam("includeRequests") Boolean includeRequests) throws Exception {
        authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));

        ApplicationStatusService svc = new ApplicationStatusService(getContext());
        //Have to load the object because it could be ID -rap
        return svc.getAppStatus(getApplication().getName(), includeRequests);
    }

    public static final String ATTR_HOST_NAME = "hostName";
    @POST
    @Path("status")
    public void requestApplicationStatus(Map<String, Object> data) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessEnvironmentMonitoring));
        String hostName = Util.otos(data.get(ATTR_HOST_NAME));
        if (Util.isNullOrEmpty(hostName)) {
            throw new InvalidParameterException("Host name required");
        }
        StatisticsService svc = new StatisticsService(getContext());
        svc.requestApplicationStatus(getApplicationByName().getName(), hostName);
    }

    @GET
    public ApplicationDTO getApplicationDTO() throws Exception {
        authorize(new RightAuthorizer(SPRight.ViewApplication));
        ApplicationService svc = new ApplicationService(getContext());
        ApplicationDTO dto = svc.getApplicationDTO(getContext(), getApplication().getId());

        return dto;
    }

    private void validateApplicationDTO(ApplicationDTO applicationDTO) throws GeneralException {
        if (sailpoint.tools.Util.isEmpty(applicationDTO.getId())) return;

        if (!applicationDTO.getId().equals(getApplication().getId())) {
            throw new GeneralException("Application ids don't match. Please check the application id passed.");
        }
    }

    @PUT
    public void updateApplication(Map<String, Object> data) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessEnvironmentMonitoring));

        try {
            String json = JsonHelper.toJson(data);
            ApplicationDTO applicationDTO = JsonHelper.fromJson(ApplicationDTO.class, json);
            validateApplicationDTO(applicationDTO);
            ApplicationService svc = new ApplicationService(applicationDTO.getId(), this);
            svc.updateApplication(applicationDTO);
        } catch(Exception e) {
            log.warn("Unable to Update Application: " + data, e);
            throw e;
        }
    }
    
}
