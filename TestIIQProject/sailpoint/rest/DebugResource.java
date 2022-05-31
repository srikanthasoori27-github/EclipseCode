/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.BasicMessageRepository;
import sailpoint.api.CertificationPhaser;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.MessageRepository;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.authorization.CapabilityAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.CacheReference;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.SignOffHistory;
import sailpoint.object.TaskSchedule;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItemArchive;
import sailpoint.server.CacheService;
import sailpoint.server.Environment;
import sailpoint.server.PostImportVisitor;
import sailpoint.server.Servicer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.extjs.GridColumn;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
@Path("debug")
public class DebugResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(DebugResource.class);
    
    private static final String COLUMN_CONFIG_KEY_FORMAT = "debug%sGridColumns";
    private static final String SEARCH_PROPERTIES_KEY_FORMAT = "debug%sSearchProperties";
    
    private static final String CLASS_NAME_NONE = "none";

    private Class clazz;
    private SubClass subClazz;
    
    /**
     * Any type that needs some special handling.  For now, just workgroups, to
     * differentiate from Identities.
     */
    private enum SubClass {
        None,
        Workgroup
    }

    @GET
    @Path("{class}")
    public ListResult listObjects(@PathParam("class") String className)
            throws GeneralException {
        
        authSystemAdmin();
        
        // 'none' is a special case, it gets us to this listObjects method
        // but no class has been selected yet. So just return an empty list result. 
        if (CLASS_NAME_NONE.equals(className)) {
            return new ListResult(null, 0);
        }
        
        List<Map<String, Object>> out = new ArrayList<>();
        Map meta = new HashMap();
        int total = 0;

        this.subClazz = getSubClass(className);
        this.clazz = getClass(className);
        

        if (this.clazz != null) {
 
            //Some classes don't have name, so change sortBy to id if necessary
            if (this.sortBy != null && this.sortBy.equals("name") && !objectHasNameProperty(this.clazz)){
                this.sortBy = "id";
            }

            QueryOptions ops = getQueryOptions(getColumnConfigKey());
            Filter searchFilter = getSearchFilter(query);
            if (searchFilter != null) {
                ops.add(searchFilter);
            }
            
            out = this.getResults(getColumnConfigKey(), this.clazz, ops);
            total = this.countResults(this.clazz, ops);

            GridResponseMetaData gridMetadata = this.getMetaData();
            meta = gridMetadata.asMap();
        }

        escapeValues(out);

        ListResult res = new ListResult(out, total);
        res.setMetaData(meta);
        return res;
    }

    /**
     * Escapes the values in the results list. Assumes a list of
     * flat maps.
     *
     * @param results The list of results to be escaped..
     */
    private void escapeValues(List<Map<String, Object>> results) {
        for (Map<String, Object> result : Util.safeIterable(results)) {
            for (String key : result.keySet()) {
                Object value = result.get(key);
                if (null != value) {
                    result.put(key, WebUtil.escapeHTML(value.toString(), false));
                }
            }
        }
    }

    @QueryParam("useName") boolean _useName;
    public static final String PARAM_USE_NAME = "useName";
    @GET
    @Path("{class}/{id}")
    public RequestResult getObject(@PathParam("class") String className, @PathParam("id") String id)
            throws GeneralException {

        authSystemAdmin();

        MultivaluedMap<String,String> params = this.uriInfo.getQueryParameters();
        this._useName = Util.atob(params.getFirst(PARAM_USE_NAME));

        // decode the special placeholder for '/'
        id = decodeRestUriComponent(id);
        
        this.clazz = getClass(className);
        
        Map<String, Object> out = new HashMap<>();
        out.put("id", id);
        out.put("class", className);
        out.put("xml", "");
        out.put("readOnly", false);
        ListResult result = null;
        SailPointObject obj = null;
        if (_useName) {
            obj = getContext().getObjectByName(this.clazz, id);
        } else {
            obj = getContext().getObjectById(this.clazz, id);
        }

        if (obj != null) {
            // obscure out password clear text before serializing
            filterPasswordValues(obj);
            
            out.put("xml", obj.toXml(true));
            if(obj.isImmutable()) {
                out.put("readOnly", true);
            }
            result = new ListResult(Arrays.asList(out), 1);
        }
        else {
            result = new ListResult(Collections.EMPTY_LIST, 0);
        }

        return result;
    }

    /**
     * Obscure password values from SP object so it is not exposed in debug output.
     *
     * 1) IdentityRequest -> ProvisioningProject -> QuestionHistory
     * 2) WorkItemArchive -> Attributes
     * 
     * @param obj - object to be scanned for secret properties that need to be filtered
     */
    private void filterPasswordValues(SailPointObject obj) {
        if (obj instanceof IdentityRequest) {
            ObjectUtil.scrubPasswords(((IdentityRequest) obj).getProvisionedProject());
        }
        else if (obj instanceof WorkItemArchive) {
            ObjectUtil.scrubPasswords(((WorkItemArchive) obj).getAttributes());
        }
    }

    @POST
    @Path("bulkDelete/{class}")
    public RequestResult deleteObjects(@PathParam("class") String className, MultivaluedMap<String, String> form)
            throws GeneralException {

        authSystemAdmin();

        RequestResult result = new RequestResult();
        result.setStatus(RequestResult.STATUS_FAILURE);

        processForm(form);

        this.clazz = getClass(className);
        this.subClazz = getSubClass(className);
        
        Terminator terminator = new Terminator(getContext());
        QueryOptions ops = getSelectionQuery();

        String queryVal = form.get("query") != null ? form.get("query").get(0).trim() : "";
        boolean selectAll = Util.atob(form.get("selectAll").get(0));

        if (selectAll && queryVal.length() > 0){
            Filter f = getSearchFilter(queryVal);
            if (f != null)
                ops.add(f);
        }
        
        ArrayList<String> readOnlyObjects = new ArrayList<>();
        // Cant use projection search on a TaskSchedule, so can't use IncrementalObjectIterator 
        if (TaskSchedule.class.equals(clazz)) {
            List<TaskSchedule> schedules = getContext().getObjects(clazz, ops);
            if (!Util.isEmpty(schedules)) {
                for (TaskSchedule schedule : schedules) {
                    deleteObject(schedule, terminator, readOnlyObjects);
                }
            }
        } else {
            IncrementalObjectIterator<SailPointObject> spObjs = new IncrementalObjectIterator<SailPointObject>(getContext(), clazz, ops);
            while(spObjs.hasNext()){
                SailPointObject o = spObjs.next();
                deleteObject(o, terminator, readOnlyObjects);
            }
        }
        
        if(readOnlyObjects.size() > 0) {
            result.setWarnings(readOnlyObjects);
            result.setStatus(RequestResult.STATUS_WARNING);
        }
        else {
            result.setStatus(RequestResult.STATUS_SUCCESS);
        }

        return result;
    }
    
    private void deleteObject(SailPointObject o, Terminator terminator, List<String> readOnlyObjects) 
            throws GeneralException {
        
        if(o.isImmutable() == false) {
            terminator.deleteObject(o);
            getContext().decache();
        }
        else {
            readOnlyObjects.add(o.getId() + " (" + o.getName() + ")");
        }
    }

    @POST
    @Path("{class}/{id}")
    public RequestResult updateObject(@PathParam("class") String className, @PathParam("id") String id, @FormParam("xml") String xml, @FormParam("useName") boolean useName)
            throws GeneralException {

        authSystemAdmin();

        RequestResult result = new RequestResult();
        result.setStatus(RequestResult.STATUS_FAILURE);

        // decode the special placeholder for '/'
        id = decodeRestUriComponent(id);
        
        XMLObjectFactory f = XMLObjectFactory.getInstance();
        Object o = f.parseXml(getContext(), xml, true);
        if (o instanceof SailPointObject) {
            SailPointObject spObj = (SailPointObject) o;
            
            // Get the signOffHistory of the new object.
            // If it exists and is greater than 0, don't allow any updates.
            List<SignOffHistory> sos = spObj.getSignOffs();
            
            // Get the existing object and check if it is immutable.
            // If it is, don't allow any updates.
            SailPointObject dbObj = null;
            if (useName) {
                dbObj = getContext().getObjectByName(spObj.getClass(), id);
            } else {
                dbObj = getContext().getObjectById(spObj.getClass(), id);
            }

            
            if((dbObj != null && dbObj.isImmutable() == false) && (sos == null || (sos != null && sos.size() < 1))) {
                ObjectUtil.checkIllegalRename(getContext(), spObj);
                getContext().decache();
                getContext().saveObject(spObj);
                getContext().commitTransaction();
                // Treat the object as if we had just imported it from the console
                new PostImportVisitor(getContext()).visit(spObj);
                getContext().commitTransaction();
                getContext().decache();
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }
            else {
                result.addError(spObj.getId() + " (" + spObj.getName() + ")");
            }
        }

        return result;
    }

    @POST
    @Path("{class}")
    public RequestResult newObject(@PathParam("class") String className, @FormParam("xml") String xml)
            throws GeneralException {

        authSystemAdmin();
        
        RequestResult result = new RequestResult();
        result.setStatus(RequestResult.STATUS_FAILURE);
        
        XMLObjectFactory f = XMLObjectFactory.getInstance();
        Object o = f.parseXml(getContext(), xml, true);
        if (o instanceof SailPointObject) {
            SailPointObject spObj = (SailPointObject) o;
            List<SignOffHistory> sos = spObj.getSignOffs();
            if(sos == null || (sos != null && sos.size() < 1)) {
                ObjectUtil.checkIllegalRename(getContext(), spObj);
                getContext().decache();
                getContext().saveObject(spObj);
                getContext().commitTransaction();
                // Treat the object as if we had just imported it from the console
                new PostImportVisitor(getContext()).visit(spObj);
                getContext().commitTransaction();
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }
            else {
                result.addError("Object " + spObj.getId() + " (" + spObj.getName() + ") contains an electronic signature.");
            }
        }

        return result;
    }

    @POST
    @Path("Identity/clearPreferences")
    public RequestResult newObject(MultivaluedMap<String, String> form)
            throws GeneralException {

        authSystemAdmin();
        
        RequestResult result = new RequestResult();
        result.setStatus(RequestResult.STATUS_FAILURE);

        processForm(form);

        this.clazz = Identity.class;
        QueryOptions ops = super.getSelectionQuery();

        String queryVal = form.get("query") != null ? form.get("query").get(0).trim() : "";
        boolean selectAll = Util.atob(form.get("selectAll").get(0));

        if (selectAll && queryVal.length() > 0){
            Filter f = getSearchFilter(queryVal);
            if (f != null)
                ops.add(f);
        }

        IncrementalObjectIterator<Identity> idents = new IncrementalObjectIterator<>(getContext(),  Identity.class, ops);
        while(idents.hasNext()){
            Identity identity = idents.next();
            if (identity.getUIPreferences() != null){
                getContext().removeObject(identity.getUIPreferences());
                identity.setUIPreferences(null);
                getContext().saveObject(identity);
                getContext().commitTransaction();
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }
            getContext().decache();
        }

        return result;
    }

    @POST
    @Path("phaseCertification")
    public RequestResult phaseCertification(MultivaluedMap<String, String> form) throws GeneralException {

        authSystemAdmin();

        RequestResult result = null;

        processForm(form);

        BasicMessageRepository repo = new BasicMessageRepository();

        QueryOptions ops = getSelectionQuery();
        try {
            Iterator<Object[]> certs = getContext().search(Certification.class, ops, Arrays.asList("id"));
            if (certs != null) {
                while (certs.hasNext()) {
                    Object[] cert = certs.next();
                    phaseCertification(repo, (String) cert[0], getSingleFormValue(form, "phase"));
                }
            }

            List<String> msgs = new ArrayList<>();
            if (repo.getMessages() != null) {
                for (Message msg : repo.getMessages()) {
                    msgs.add(msg.getLocalizedMessage());
                }
            }

            result = new ListResult(msgs, msgs.size());

        } catch (GeneralException e) {
            log.error(e);
            result = new RequestResult();
            result.setStatus(RequestResult.STATUS_FAILURE);
            result.addError(e.getMessage());
        }

        return result;
    }

    @GET
    @Path("Rule/{id}/run")
    public RequestResult updateObject(@PathParam("id") String idOrName)
            throws GeneralException {

        authSystemAdmin();

        String resultXMl = "";
        String status = RequestResult.STATUS_FAILURE;
        if (idOrName != null && idOrName.trim().length() > 0) {
            try{
                SailPointContext con = getContext();
                Rule rule = con.getObjectById(Rule.class, idOrName);
                Object result = null;
                if (rule != null)
                    result = con.runRule(rule, null);

                if (result != null) {
                    XMLObjectFactory f = XMLObjectFactory.getInstance();
                    resultXMl = f.toXml(result);
                }
                status = RequestResult.STATUS_SUCCESS;
            }
            catch(Throwable t){
                resultXMl = "Exception running rule: " + t.getMessage();
            }
        }
        return new ObjectResult(resultXMl, status, null, null, null);
    }

    @GET
    @Path("Cache")
    public RequestResult viewCache()
            throws GeneralException {

        authSystemAdmin();

        String status = RequestResult.STATUS_SUCCESS;

        StringBuilder sb = new StringBuilder();

        Environment env = Environment.getEnvironment();
        Servicer services = env.getServicer();
        CacheService service = (CacheService)services.getService(CacheService.NAME);
        if (service != null) {
            List<CacheReference> caches = service.getCaches();
            if (caches != null) {
                for (CacheReference cache : caches) {
                    SailPointObject obj = cache.getObject();
                    if (obj != null) {
                        sb.append(obj.toXml());
                    }
                }
            }
        }

        String resultXMl = sb.toString();

        return new ObjectResult(resultXMl, status, null, null, null);
    }


    @GET
    @Path("Cache/reset")
    public RequestResult resetCache()
            throws GeneralException {

        authSystemAdmin();

        String status = RequestResult.STATUS_SUCCESS;

        // A pretty large backdoor...
        Environment env = Environment.getEnvironment();
        Servicer services = env.getServicer();
        CacheService caches = (CacheService)services.getService(CacheService.NAME);
        if (caches != null)
            caches.forceRefresh(getContext());

        String resultXml = "";

        return new ObjectResult(resultXml, status, null, null, null);
    }
    
    /**
     * Returns call timing information for all meters.
     */
    @GET
    @Path("meters")
    public RequestResult meters() throws GeneralException {
        
        authSystemAdmin();
        
        List<Map<String, Object>> values = new ArrayList<>();
        Collection<Meter> meterCollection =  Meter.getGlobalMeterCollection();
        for (Meter meter : meterCollection) {
            Map<String, Object> val = new HashMap<>();
            val.put("name", meter.getName());
            val.put("hits", meter.getEntries());
            val.put("errors", meter.getErrors());
            val.put("min", meter.getMin());
            val.put("max", meter.getMax());
            val.put("total", meter.getTotal());
            val.put("average", meter.getAverage());
            values.add(val);
        }

        return new ListResult(values, meterCollection.size());
    }
    
    private void authSystemAdmin() throws GeneralException {
    	authorize(new CapabilityAuthorizer(Capability.SYSTEM_ADMINISTRATOR));
    }

    private void phaseCertification(MessageRepository repo, String id, String phaseName) throws GeneralException {

        Certification cert = getContext().getObjectById(Certification.class, id);
        Certification.Phase targetPhase = Certification.Phase.valueOf(phaseName);
        Certification.Phase current = cert.getPhase();
        if (targetPhase.compareTo(current) <= 0) {
            repo.addMessage(Message.error(MessageKeys.MSG_PLAIN_TEXT,
                    "Certification '" + id + "' is already on or past the requested phase. " +
                            "Current phase is " + current));
        } else {
            CertificationPhaser phaser = new CertificationPhaser(getContext(), repo);

            while (targetPhase.compareTo(cert.getPhase()) > 0) {
                phaser.advancePhase(cert);
                getContext().commitTransaction();
                String msg = "Transitioned certification '" + id + "' to " + Internationalizer.getMessage(
                        cert.getPhase().getMessageKey(), Locale.US);
                repo.addMessage(Message.info(MessageKeys.MSG_PLAIN_TEXT, msg));
            }
        }
    }

    private Class getClass(String className) {
        if (className != null) {
            SubClass sub = getSubClass(className);
            if (sub != null) {
                return getClassFromSubClass(sub);
            }
        }
        return ObjectUtil.getMajorClass(className);
    }
    
    /**
     * Special handling to get main class from sub class. Right now, 
     * just to get Identity for Workgroup. 
     */
    private Class getClassFromSubClass(SubClass subClass) {
        if (SubClass.Workgroup.equals(subClass)) {
            return Identity.class;
        }
        
        return null;
    }
    
    private SubClass getSubClass(String className) {
        SubClass[] values = SubClass.values();
        for (SubClass value : values) {
            if (value.toString().equals(className))
                return value;
        }
        
        return null;
    }
    
    @Override
    protected List<String> getProjectionColumns(String columnsKey) throws GeneralException{
        List<String> columns = super.getProjectionColumns(columnsKey);
        Iterator<String> columnsIterator = columns.iterator();
        while (columnsIterator.hasNext()) {
            String column = columnsIterator.next();
            if (column.equals("name") && !objectHasNameProperty(this.clazz)) {
                columnsIterator.remove();
            }
        }
        
        return columns;
    }
    
    @Override
    protected List<ColumnConfig> getColumns(String columnsKey) throws GeneralException{
        List<ColumnConfig> columns = super.getColumns(columnsKey);
        
        List<ColumnConfig> columnsCopy = new ArrayList<ColumnConfig>();
        if (columns != null) {
            for (ColumnConfig column : columns) {
                if (!column.getDataIndex().equals("name") || objectHasNameProperty(this.clazz)) {
                    columnsCopy.add(new ColumnConfig(column));
                }
            }
        }
        
        return columnsCopy;
    }
    
    @Override
    protected QueryOptions getQueryOptions(String columnsKey) throws GeneralException {
        QueryOptions ops = super.getQueryOptions(columnsKey);
        Filter subClassFilter = getAdditionalFilter();
        if (subClassFilter != null) {
            ops.add(subClassFilter);
        }

        return ops;
    }
    
    @Override
    protected QueryOptions getSelectionQuery() {
        QueryOptions ops = super.getSelectionQuery();
        Filter additionalFilter = this.getAdditionalFilter();
        if (additionalFilter != null) {
            ops.add(additionalFilter);
        }
        
        return ops;
    }

    @Override
    public List<Map<String,Object>> getResults(String columnsKey,
            Class<? extends SailPointObject> scope,
            QueryOptions qo) throws GeneralException {

        List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
        
        //Cant use projection search on a TaskSchedule, so here's this 
        //ugly hardcoded stuff instead. 
        if (TaskSchedule.class.equals(scope)) {
            List<TaskSchedule> objs = getContext().getObjects(TaskSchedule.class, qo);
            if (objs != null) {
                for (TaskSchedule obj : objs) {
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("id", obj.getId());
                    result.put("name", obj.getName());
                    result.put("lastExecution", obj.getLastExecution());
                    result.put("nextExecution", obj.getNextExecution());
                    results.add(result);
                }
            }
            makeJsonSafeKeys(results);           
        } else {
            results = super.getResults(columnsKey, scope, qo);
        }
        
        return results;
    }
    
    /**
     * @see sailpoint.rest.BaseListResource#countResults(java.lang.Class, sailpoint.object.QueryOptions)
     * 
     * Task schedule handles result counting differently due to QuartzPersistenceManager, 
     * can account for it by changing the query options to have no result limit otherwise 
     * call countResults as normal
     */
    @Override
    public int countResults(Class<? extends SailPointObject> scope,
            QueryOptions qo) throws GeneralException {

        int count = 0;
        
        if (TaskSchedule.class.equals(scope)) {
            qo.setResultLimit(0);          
        }
        count = super.countResults(scope, qo);
        
        return count;
    }
    
    /**
     * In case we need some special filters for certain types or subtypes, 
     * put it here.
     */
    private Filter getAdditionalFilter() {
        
        Filter filter = null;
        if (Identity.class.equals(this.clazz)) {
            if (SubClass.Workgroup.equals(this.subClazz)) {
                filter = Filter.eq("workgroup", true);
            } else {
                filter = Filter.eq("workgroup", false);
            }
        }
        
        return filter;

    }
    
    /**
     * @ignore If there's a problem, return true since most SailPointObjects
     *         have a "name" property.
     */
    private boolean objectHasNameProperty(Class clazz) {        
        SailPointObject classObj;
        try {
            classObj = (SailPointObject)clazz.newInstance();
            return classObj.hasName();
        } catch (InstantiationException | IllegalAccessException e) {
            return true;
        }
    }
    
    private String getColumnConfigKey() {
        String key = String.format(COLUMN_CONFIG_KEY_FORMAT, this.clazz.getSimpleName());
        UIConfig uiConfig = UIConfig.getUIConfig();
        if (!uiConfig.getAttributes().containsKey(key)) {
            key = "debugObjectGridColumns";
        }
        return key;
    }
    
    private GridResponseMetaData getMetaData() throws GeneralException {
        GridResponseMetaData meta = new GridResponseMetaData();
        meta.setRoot("objects");
        meta.setTotalProperty("count");
        
        List<ColumnConfig> columns = getColumns(getColumnConfigKey());
        
        if (columns != null) {
            for (ColumnConfig column : columns) {
                meta.addColumn(new GridColumn(column));
            }
        }

        return meta;
    }
    
    private Filter getSearchFilter(String searchString) {
        String key = String.format(SEARCH_PROPERTIES_KEY_FORMAT, this.clazz.getSimpleName());
        UIConfig uiConfig = UIConfig.getUIConfig();
        Filter filter = null;
        if (searchString != null) {
            if (uiConfig.getAttributes().containsKey(key)) {
                String properties = uiConfig.getAttributes().getString(key);
                List<String> propList = Util.csvToList(properties);
                if (!Util.isEmpty(propList)) {
                    List<Filter> orFilters = new ArrayList<Filter>();
                    for (String prop : propList) {
                        orFilters.add(Filter.ignoreCase(Filter.like(prop,  searchString, Filter.MatchMode.START)));
                    }
                    filter = Filter.or(orFilters);
                }
            } else {
                if (objectHasNameProperty(this.clazz)){
                    filter = Filter.or(
                            Filter.ignoreCase(Filter.like("id", searchString, Filter.MatchMode.START)),
                            Filter.ignoreCase(Filter.like("name", searchString, Filter.MatchMode.START))
                            );
                } else {
                    filter = Filter.ignoreCase(Filter.like("id", searchString, Filter.MatchMode.START));
                }
            }
        }
        
        return filter;
    }
}
