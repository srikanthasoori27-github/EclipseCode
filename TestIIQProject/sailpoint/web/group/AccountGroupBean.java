/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.group;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.api.AccountGroupService;
import sailpoint.api.Explanator;
import sailpoint.authorization.ManagedAttributeDetailsAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Scope;
import sailpoint.object.UIConfig;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.EffectiveAccessListService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.URLUtil;
import sailpoint.tools.Util;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.identity.IdentityDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.Sorter;
import sailpoint.web.util.WebUtil;

/**
 * JSF bean for viewing AccountGroups.
 */
public class AccountGroupBean extends BaseObjectBean<ManagedAttribute> implements BaseListServiceContext {
    private static Log log = LogFactory.getLog(AccountGroupBean.class);

    private EntitlementSnapshot entitlements;
    private transient Map<String, Set<String>> warningsIssued;
    List<ColumnConfig> columns;
    List<String> projectionAttributes;
    List<Map<String,Object>> members;

    /**
     * Account groups that this account group inherits
     * its access.
     */
    private List<ManagedAttribute> inherited;
    
    /**
     * Account groups that inherit their access
     * from this accound group.
     */
    private List<ManagedAttribute> inheriting;
    Integer inheritingSize; 

    /**
     * Cached version of the permissions that are being
     * shown in the grid, typically just a single page 
     * and not the whole set.
     */
    List<Permission> _permissions = null;

    /**
     * UI Column config for permissions grid.
     */
    List<ColumnConfig> permissionColumns;

    /**
     * True if the user preference, or certification configuration specifies
     * that an entitlement's description should be displayed rather than
     * it's value.
     */
    private Boolean displayEntDescriptions;
    
    /**
     * Transitioning is enabled/disabled by the transitionToNewAccountGroupButton.  
     * It a boolean that tells the bean when not to accept changes (sets) from the 
     * jsf model while we are in the procrocess of transitioning to a new 
     * account group.  This hack to prevent jsf from updating the bean 
     * with values from the previous account group.
     */
    String _inTransition;

    
    private boolean hasProvisioningRight;
    
    /**
     * Default constructor.
     */
    public AccountGroupBean() throws GeneralException {
        super();

        super.setScope(ManagedAttribute.class);
        super.setStoredOnSession(false);
        
        displayEntDescriptions = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC);
        ManagedAttribute accountGroup = getObject();

        if(accountGroup==null) {

            String appName = Util.getString(super.getRequestParameter("applicationName"));
            String groupAttr = Util.getString(super.getRequestParameter("groupAttribute"));
            String groupNameParam = Util.getString(super.getRequestParameter("groupName"));
            String groupName = groupNameParam != null ? URLUtil.decodeUTF8(groupNameParam) : null;

            log.debug("applicationName = " + appName +
                    ", groupAttribute = " + groupAttr +
                    ", groupName = " + groupName);

            if ((null != appName) && (null != groupAttr) && (null != groupName)) {

                AccountGroupService svc = new AccountGroupService(getContext());
                accountGroup = svc.getAccountGroup(appName, groupAttr, groupName);
                if (null != accountGroup) {
                    super.setObjectId(accountGroup.getId());
                }
            }
        }

        warningsIssued = new HashMap<String, Set<String>>();
        
        hasProvisioningRight = initProvisioningRight();

        log.debug("id = " + getObjectId());
    }  // AccountGroupBean()

    /**
     * Alternate constructor that allows another bean to manage this one.
     */
    public AccountGroupBean(ManagedAttribute accountGroup) {
        super();
        // If bean is created from another bean, assume we have authorized things already and are doing the right thing.
        setSkipAuthorization(true);
        setScope(ManagedAttribute.class);
        setStoredOnSession(false);
        displayEntDescriptions = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC);
        if (null != accountGroup) {
            setObjectId(accountGroup.getId());
            setObject(accountGroup);
        }        

        hasProvisioningRight = initProvisioningRight();

        log.debug("id = " + getObjectId());
    }
    
    @Override
    public String getObjectId() {
        String objId = super.getObjectId();
        if (objId == null || objId.trim().length() == 0) {
            objId = (String) getRequestParam().get("id");
            
            if (objId != null && objId.trim().length() > 0) {
                setObjectId(objId);
                setObject(null);
            }
        }
        
        return objId;
    }

    /**
     * Return an EntitlementSnapshot that has all of the permissions and
     * attributes of the ManagedAttribute.  This method is here because we already
     * have some pieces of the UI that can display EntitlementSnapshots.
     *
     * @return An EntitlementSnapshot that has all of the permissions and
     *         attributes of the ManagedAttribute.
     */
    public EntitlementSnapshot getEntitlements() throws GeneralException {

        if (null == this.entitlements) {
            ManagedAttribute group = getObject();

            if (null != group) {
                this.entitlements =
                    new EntitlementSnapshot(group.getApplication().getName(),
                            // we had not been including the nativeIdentity,
                            // does that mean we don't need the instance either? 
                            null, null, null,
                            group.getAllPermissions(),
                            group.getAttributes());
            }
        }

        return this.entitlements;
    }

    /**
     * @return true if the logged in user is allowed to provision ManagedAttributes; false otherwise
     */
    public boolean isHasProvisioningRight() {
        return hasProvisioningRight;
    }

    /**
     * @param hasProvisioningRight true if the logged in user is allowed to provision ManagedAttributes; false otherwise
     */
    public void setHasProvisioningRight(boolean hasProvisioningRight) {
        this.hasProvisioningRight = hasProvisioningRight;
    }

    /**
     * Prior to 6.0 the member query hit the link external table.  Now we rely
     * on the IdentityEntitlement objects.  
     * 
     * The concept of MemberAttribute no longer plays a part in this query.
     * Prior to 6.0 the member attribute allowed cusomters to specify the name
     * of the link attribute that "held" the membership for cases where
     * it might be defined by a rule instead of a direct mapping.
     *
     * @return QueryOptions to query IdentityEntitlements for the specified managed attribute;
     * null if nothing should be returned
     */
    private QueryOptions getMembersQueryOptions(ManagedAttribute managedAttribute, boolean limitResults)
        throws GeneralException {
        AccountGroupService svc = new AccountGroupService(getContext());

        QueryOptions qo;
        try {
            qo = svc.getMembersQueryOptions(managedAttribute);
        } catch (Exception e) {
            log.error("The membership query failed.  Nothing will be returned", e);
            return null;
        }
        if ( managedAttribute == null ) return null;

        if(limitResults) {
            int start = Util.atoi(getRequestParameter("start"));
            int limit = getResultLimit();
    
            if(start>0) 
                qo.setFirstRow(start);        
            if(limit>0)
                qo.setResultLimit(limit);
        }

        String orderBy = "identity.name";
        boolean asc = true;
        String sort = super.getRequestParameter("sort");
        
        if (null != sort) {
            ColumnConfig col = getColumnByDataIndex(sort);
            if (null != col) {
                orderBy = col.getSortProperty();
                asc = sortAscending();
            }
        }
        
        qo.addOrdering(orderBy, asc);

        return qo;
    }  // getMembersQueryOptions()

    private ColumnConfig getColumnByDataIndex(String dataIndex) {
        if (null != dataIndex) {
            List<ColumnConfig> cols = getColumns();
            if (null != cols) {
                for (ColumnConfig col : cols) {
                    if (dataIndex.equals(col.getDataIndex())) {
                        return col;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     *
     * @return
     */
    public int getMemberCount() throws GeneralException {
        ManagedAttribute group =  getObject();

        // if MA is null or has no attribute then no members
        if (group == null || Util.isNullOrEmpty(group.getAttribute())) {
            return 0;
        }

        AccountGroupService accountGroupSvc = new AccountGroupService(getContext());
        return accountGroupSvc.getMemberCount(group);
    }  // getMemberCount()

 
    /**
     *
     * @return
     */
    public List<Map<String,Object>> getMemberList() throws GeneralException{
        if(members==null) {
            ManagedAttribute group =  getObject();

            QueryOptions qo = null;
            try {
                qo = getMembersQueryOptions(group, true);
            } catch ( GeneralException ex ) {
                Message msg = new Message(Message.Type.Error,
                        MessageKeys.ERR_GROUP_CANT_CREATE_QUERY, ex);
                log.error(msg.getMessage(), ex);
                addMessage(msg, null);
                qo = null;
            }

            members = new ArrayList<Map<String,Object>>();
            if (qo != null) {
                Iterator<Object[]> identities = null;
                try {
                    identities = getContext().search(IdentityEntitlement.class, qo, getProjectionColumns());
                } catch ( GeneralException ex ) {
                    Message msg = new Message(Message.Type.Error,
                            MessageKeys.ERR_GROUP_CANT_LIST_IDS, ex);
                    log.error(msg.getLocalizedMessage(), ex);
                    addMessage(msg, null);

                }

                if ( identities != null ) {
                    while ( identities.hasNext() ) {
                        Object[] identity = identities.next();
                        if ( identity != null && identity.length > 1 ) {
                            int i = 0;
                            Map<String, Object> idMap = new HashMap<String, Object>();
                            for (String col : getProjectionColumns()) {
                                // if the value can be localized. If so, localize it
                                Object value = identity[i];
                                if (value != null && Localizable.class.isAssignableFrom(value.getClass())){
                                    value = ((Localizable)value).getLocalizedMessage(getLocale(), getUserTimeZone());
                                } else if (value instanceof String) {
                                    // XSS protections
                                    value = WebUtil.escapeHTML((String)value, false);
                                }
                                idMap.put(col, value);
                                i++;
                            }
                            members.add(idMap);
                        }
                    }
                }
            }
        }
        return members;
    }  // getMembers()

    /**
     *
     * @return
     */
    public int getPermissionsCount() {
        ManagedAttribute group = null;
        try {
            group = getObject();
        } catch ( GeneralException ex ) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_GROUP_CANT_CNT_PERMS, ex);
            log.error(msg.getLocalizedMessage(), ex);
            addMessage(msg, null);
        }

        int size = 0;
        if ( group != null ) {
            List<Permission> perms = group.getAllPermissions();
            if ( perms != null )
                size = perms.size();
        }

        return size;
    }  // getPermissionCount()

    public List<Permission> getPermissions() {
        if ( _permissions == null ) {

            _permissions = new ArrayList<Permission>();
            List<Permission> allperms = getPermissionList();
            if ( Util.size(allperms) > 0 ) {
                if ( allperms != null ) {
                    // the  ajax request
                    int firstRow = getFirstRow();
                    int resultLimit = getResultLimit();

                    int totalSize = allperms.size();
                    int lastRow = totalSize;
                    if ( ( firstRow + resultLimit ) <  totalSize ) {
                        lastRow = firstRow + resultLimit;
                    }
                    
                    boolean ascending = sortAscending();
                    String sortColumn = getRequestParameter("sort");
                    if ( Util.getString(sortColumn) == null ) {
                        sortColumn = "target";
                    }                    
                    Comparator<Permission> comparator = Permission.SP_PERMISSION_BY_TARGET;
                    if ( sortColumn.equals("rights")) {
                        comparator = Permission.SP_PERMISSION_BY_RIGHT;
                    } else
                    if ( sortColumn.equals("annotation")) {
                        comparator = Permission.SP_PERMISSION_BY_ANNOTATION;
                    }                    
                    if ( ascending ) {
                        Collections.sort(allperms, comparator);                         
                    } else {			               
                        Collections.sort(allperms, Collections.reverseOrder(comparator));                        
                    }
                    
                    _permissions = allperms.subList(firstRow,lastRow);                    
                }
            }
        } 
        return _permissions;
    }

    /**
     *
     */
    public List<Permission> getPermissionList() {
        ManagedAttribute group = null;
        try {
            group = getObject();
        } catch ( GeneralException ex ) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_GROUP_CANT_CNT_PERMS, ex);
            log.error(msg.getLocalizedMessage(), ex);
            addMessage(msg, null);
        }

        List<Permission> perms = null;
        if ( group != null ) {
            perms = group.getAllPermissions();
        } else {
            perms = new ArrayList<Permission>();
        }

        // TODO need to pay attention to requestParamaters named s1-s3 and
        //      sort accordingly.

        return perms;
    }  // getPermissionList()

    /**
     *
     * @return
     */
    public List<Map<String,Object>> getAttributes() {
        ManagedAttribute group = null;
        try {
            group = getObject();
        } catch ( GeneralException ex ) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_GROUP_CANT_GET_ATTRS, ex);
            log.error(msg.getLocalizedMessage(), ex);
            addMessage(msg, null);
        }

        List<Map<String,Object>> attrs = new ArrayList<Map<String,Object>>();

        if (null != group) {
            Application app = group.getApplication();
            Schema schema = app.getSchema(group.getType());
            if ( schema == null ) {              
                Message msg = new Message(Message.Type.Error,
                        MessageKeys.ERR_GROUP_SCHEMA_NOT_FOUND, group.getType());
                log.error(msg.getLocalizedMessage());
                addMessage(msg, null);
            }

            Attributes<String,Object> groupAttrs = group.getAttributes();
            if ( groupAttrs != null ) {
                for ( String key : groupAttrs.keySet() ) {
                    Object value = groupAttrs.get(key);

                    AttributeDefinition attrDef = schema.getAttributeDefinition(key);
                    boolean entAttr = false;
                    if ( attrDef != null && attrDef.isEntitlement() ) {
                        entAttr = true;
                    }

                    Map<String,Object> map = new HashMap<String,Object>();
                    map.put("name", key);
                    map.put("value", value);
                    map.put("entitlement", entAttr);
                    attrs.add(map);
                }
            }
        }

        return attrs;
    }  // getAttributes()

    /**
     *
     * @param ops
     * @return
     * @throws GeneralException
     */
    private List<SelectItem> getGroupsSelectList(QueryOptions ops) throws GeneralException{
        List<SelectItem> items = new ArrayList<SelectItem>();

        List<ManagedAttribute> groups = getContext().getObjects(ManagedAttribute.class, ops);
        for (int i = 0; i < groups.size(); i++) {
            ManagedAttribute accountGroup =  groups.get(i);
            items.add(new SelectItem(accountGroup.getId(), accountGroup.getName()));
        }

        return items;
    }

    public List<SelectItem> getAccountGroupsByOwner() throws GeneralException{
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("owner.name", getLoggedInUserName()));

        return getGroupsSelectList(ops);
    }

    public List<SelectItem> getAccountGroupsByApplictionId(String ownerName, String applicationId) throws GeneralException{
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application.id", applicationId));
        ops.add(Filter.eq("owner.name", ownerName));
        return getGroupsSelectList(ops);
    }

    private void setCurrentTab() {
        Object bean = super.resolveExpression("#{groupNavigation}");
        if ( bean != null )
            ((GroupNavigationBean)bean).setCurrentTab(2);
    }

    @Override
    public String saveAction() throws GeneralException {
        setCurrentTab();
        authorize(new RightAuthorizer(SPRight.FullAccessGroup));
        return super.saveAction();
    }

    @Override
    public String cancelAction() throws GeneralException {
        setCurrentTab();
        clearHttpSession();
        return super.cancelAction();
    }

    private void addWarning(String groupName, String applicationName) {
        Set<String> referencesForWhichWarningsWereIssued = warningsIssued.get(applicationName);

        if ( ( referencesForWhichWarningsWereIssued == null ) || 
                ( !referencesForWhichWarningsWereIssued.contains(groupName) ) ) {

            Message msg = new Message(Message.Type.Warn,
                    MessageKeys.ERR_GROUP_CANT_FIND_MEMBERATTR, groupName, applicationName);
            addMessage(msg, null);

            if (referencesForWhichWarningsWereIssued == null) {
                referencesForWhichWarningsWereIssued = new HashSet<String>();
            }
            referencesForWhichWarningsWereIssued.add(groupName);
            warningsIssued.put(applicationName, referencesForWhichWarningsWereIssued);
        }
    }

    public List<ColumnConfig> getColumns() {
        if(columns==null) {
            try {
                loadColumnConfig();
            } catch (GeneralException ge) {
                log.warn("Unable to load Account Group Identity Column Config.  Exception:" + ge.getMessage());
            }
        }
        return columns;
    }

    public void setColumns(List<ColumnConfig> columns) {
        this.columns = columns;
    }

    void loadColumnConfig() throws GeneralException {
        this.columns = super.getUIConfig().getAccountGroupIdentityTableColumns();
    }

    public List<ColumnConfig> getPermissionColumns() {
        if(permissionColumns==null) {
            try {
                loadPermissionColumnConfig();
            } catch (GeneralException ge) {
                log.warn("Unable to load Account Group Permission Column Config.  Exception:" + ge.getMessage());
            }
        }
        return permissionColumns;
    }

    public void setPermissionColumns(List<ColumnConfig> columns) {
        this.permissionColumns = columns;
    }

    void loadPermissionColumnConfig() throws GeneralException {
        this.permissionColumns = super.getUIConfig().getAccountGroupPermissionTableColumns();
    }
    
    public String getPermissionColumnJson() {
        return getColumnJSON("target", getPermissionColumns());
    }
            
    public List<String> getProjectionColumns() throws GeneralException {

        if (projectionAttributes == null) {
            projectionAttributes = new ArrayList<String>();

            List<ColumnConfig> cols = getColumns();
            if (cols != null) {
                for (ColumnConfig col : cols) {
                    if (col.getProperty()!=null) {
                        projectionAttributes.add(col.getProperty());
                    }
                }
            }
            // Took this out because it was causing non-distinct identities to be returned
            // --Bernie
            // projectionAttributes.add("id");
        }

        return projectionAttributes;
    }

    /*
     * Bug #19086 Override isAuthorized in order to bypass scope checking if the user
     * should otherwise have access to the object.
     *
     * @param object
     * @return
     * @throws GeneralException
     */
    @Override
    public boolean isAuthorized(SailPointObject object) throws GeneralException {

        if (isSkipAuthorization() != null && isSkipAuthorization()) {
            return true;
        }

        String referer = getRequestParameter("refererType");
        String refererId = getRequestParameter("refererId");
        String authorizedIdentity = (String)getSessionScope().get(IdentityDTO.VIEWABLE_IDENTITY);

        return ManagedAttributeDetailsAuthorizer.isAuthorized(this, (ManagedAttribute)object, referer, refererId, authorizedIdentity);
    }

    @Override
    public ManagedAttribute getObject() throws GeneralException {

        ManagedAttribute obj = super.getObject();

        if (obj == null || obj.getId() == null || obj.getId().trim().length() == 0) {
            String id = getRequestParameter("id");
            if (id != null && id.trim().length() > 0) {
                ManagedAttribute replacement = getContext().getObjectById(ManagedAttribute.class, id);
                if (replacement != null) {
                    obj = replacement;
                }
            }
        }

        return obj;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //  Inheritance
    //
    ///////////////////////////////////////////////////////////////////////////

    private List<ManagedAttribute> getAllInherited() {

        if ( inherited == null ) {        
            inherited = new ArrayList<ManagedAttribute>();
            try {
                ManagedAttribute obj = getObject();
                if (obj != null) {
                    List<ManagedAttribute> inheritence = obj.getInheritance();
                    if (inheritence != null)
                        inherited.addAll(inheritence);
                }
            } catch (GeneralException ge) {
                log.warn("Error getting inherited groups." + ge.toString());
            }
        }
        return inherited;
    }

    List<ManagedAttribute> _inheritedPage;

    public List<ManagedAttribute> getInherited() {

        if ( _inheritedPage != null ) 
            return _inheritedPage;

        List<ManagedAttribute> allinherited = getAllInherited();
        if (Util.size(allinherited) == 0 ) {
            return _inheritedPage;
        }

        try {
            // the  ajax request
            boolean ascending = sortAscending();
            String sortColumn = getRequestParameter("sort");
            if ( Util.getString(sortColumn) == null ) {
                sortColumn = "displayableName";
            }

            Comparator<ManagedAttribute> comparator = ManagedAttribute.SP_ACCOUNTGROUP_BY_NAME;
            if ( "nativeIdentity".compareTo(sortColumn) == 0 ) {
                comparator = ManagedAttribute.SP_ACCOUNTGROUP_BY_NATIVE_IDENTITY;
            } else
            if ( "owner".compareTo(sortColumn) == 0 ) {
                comparator = ManagedAttribute.SP_ACCOUNTGROUP_BY_OWNER;
            } else
            if ( "modified".compareTo(sortColumn) == 0 ) {
                comparator = ManagedAttribute.SP_ACCOUNTGROUP_BY_MODIFIED;
            }

            if ( ascending ) {
                Collections.sort(allinherited, comparator);                         
            } else {			               
                Collections.sort(allinherited, Collections.reverseOrder(comparator));                        
            }

            int firstRow = getFirstRow();
            int resultLimit = getResultLimit();

            int totalSize = allinherited.size();
            int lastRow = totalSize;
            if ( ( firstRow + resultLimit ) <  totalSize ) {
                lastRow = firstRow + resultLimit;
            }
            _inheritedPage = allinherited.subList(firstRow,lastRow);                   

        } catch (Exception ge) {
            log.error("Error getting inherited groups.", ge);
        }
        return _inheritedPage;
    }
    
    public void setInherited(ArrayList<ManagedAttribute> inherited) {
        this.inherited = inherited;
    }
    
    public int getInheritedSize() {        
        List<ManagedAttribute> grps = getAllInherited();
        return ( grps.size() >  0 ) ? grps.size() : 0;
    }

    public List<ManagedAttribute> getInheriting() {
        if ( inheriting == null ) {        
            inheriting = new ArrayList<ManagedAttribute>();
            try {
                ManagedAttribute obj = getObject();
                if (obj != null) {
                    List<ManagedAttribute> vals = new ArrayList<ManagedAttribute>();
                    vals.add(obj);
                    Filter filter  = Filter.containsAll("inheritance", vals);
                    QueryOptions qo = new QueryOptions();

                    String sortColumn = getRequestParameter("sort");
                    String sortDirection = this.getRequestParameter("dir");
                    if ( Util.getString(sortColumn) == null ) {
                        sortColumn = "displayableName";
                        sortDirection = "ASC";
                    } else {
                        if(sortColumn.startsWith("[")) {
                            List<Sorter> sorters = JsonHelper.listFromJson(Sorter.class, sortColumn);
                            for(Sorter sorter : sorters) {
                                String jsonProperty = sorter.getProperty();
                                qo.addOrdering(Util.getKeyFromJsonSafeKey(jsonProperty), sorter.isAscending());
                            }
                        }
                        else {
                            qo.addOrdering(Util.getKeyFromJsonSafeKey(sortColumn), "ASC".equalsIgnoreCase(sortDirection));
                        }
                    }
                    
                    qo.add(filter);
                    qo.setFirstRow(getFirstRow());
                    qo.setResultLimit(getResultLimit());
                    Iterator<ManagedAttribute> it = getContext().search(ManagedAttribute.class, qo);
                    while ( it.hasNext() ) {
                        ManagedAttribute child = it.next();
                        inheriting.add(child);
                    }
                }
            } catch (GeneralException ge) {
                log.error("Error getting inheriting groups.", ge);
            }
        }
        return inheriting;
    }
    
    public void setInheriting(ArrayList<ManagedAttribute> inheriting) {
        this.inheriting = inheriting;
    }

    public int getInheritingSize() {        
        if ( inheritingSize == null ) {
            try {
                ManagedAttribute obj = getObject();
                if (obj != null) {
                    QueryOptions qo = new QueryOptions();
                    List<ManagedAttribute> inheriting = new ArrayList<ManagedAttribute>();
                    inheriting.add(obj);
                    Filter f = Filter.containsAll("inheritance", inheriting);
                    qo.add(f);
                    int size = getContext().countObjects(ManagedAttribute.class, qo);
                    inheritingSize = new Integer(size);
                }
            } catch(GeneralException e) {
                log.error("Error getting inheriting size. " + e.toString());
            }
        }
        return ( inheritingSize == null ) ? 0 : inheritingSize.intValue();
    }

    private int getFirstRow() {
        int firstRow = 0;

        int start = Util.atoi(getRequestParameter("start"));
        int offset = Util.atoi(getRequestParameter("offset"));
        if ( start > 0 ) {
            firstRow = start;
        } else {
            firstRow = offset;
        }
        return firstRow;
    }

    protected int getResultLimit() {
        int resultLimit = 0;

        int limit = Util.atoi(getRequestParameter("limit"));
        int pagesize = Util.atoi(getRequestParameter("page_size"));
        if ( limit > 0 ) {
            resultLimit = limit;
        } else {
            resultLimit = pagesize;
        }
        return WebUtil.getResultLimit(resultLimit);
    }

    private boolean sortAscending() {
        String direction = getRequestParameter("dir");
        boolean ascending = true;
        if ( Util.getString(direction) == null ) {
            ascending = true;
        } else {
            ascending = direction.equalsIgnoreCase("ASC");
        }
        return ascending;
    }

    /**
     */
    public String getPermissionsGridJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;
        
        try {
            jsonWriter.object();
            
            jsonWriter.key("totalCount");
            jsonWriter.value(getPermissionsCount());

            JSONArray permissions = new JSONArray();
            List<Permission> perms = getPermissions();
            
            Application app = getObject().getApplication();
            
            for ( Permission perm : perms ) {
                Map<String,Object> permMap = new HashMap<String,Object>();
                
                Map<String, String> target = new HashMap<String, String>();
                target.put("name", perm.getTarget());
                String desc = Explanator.getPermissionDescription(app, perm.getTarget(), getLocale());
                target.put("description", desc);
                target.put("descriptionFirst", Boolean.toString(displayEntDescriptions));
                
                permMap.put("target", target);
                permMap.put("targetId", perm.getTarget() + Util.uuid());
                permMap.put("rights", perm.getRights());
                permMap.put("annotation", perm.getAnnotation());
                permissions.put(permMap);   
            }
            jsonWriter.key("permissions");
            jsonWriter.value(permissions);

            
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (GeneralException ge) {
            log.error("Failed to generate JSON due to General Exception: " + ge);
            result = "{}";     
        } 
        return result;
    }

    
    public String getInheritedGridJson() {
        return getGroupGridJson("inheritedGroups", getInherited(), getInheritedSize());
    }
 
    public String getInheritingGridJson() {
        return getGroupGridJson("inheritingGroups", getInheriting(), getInheritingSize());
    } 

    public String getNameOnlyInheritingGroupGridJson() {
        List<ColumnConfig> columns;
        try {
            columns = getUIConfig().getNameOnlyAccountGroupTableColumns();
        } catch (GeneralException e) {
            columns = null;
        }
        return getGroupGridJson("inheritingGroups", getInheriting(), columns, getInheritingSize());
    }

    public String getNameOnlyInheritedGroupGridJson() {
        List<ColumnConfig> columns;
        try {
            columns = getUIConfig().getNameOnlyAccountGroupTableColumns();
        } catch (GeneralException e) {
            columns = null;
        }
        return getGroupGridJson("inheritedGroups", getAllInherited(), columns, getInheritedSize());
    }

    private String getGroupGridJson(String rootId, List<ManagedAttribute> groups, int totalSize) {
        return getGroupGridJson(rootId, groups, new AccountGroupListBean().getColumns(), totalSize);
    }
    
    
    private String getGroupGridJson(String rootId, List<ManagedAttribute> groups, List<ColumnConfig> columns, int totalSize) {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result = null;

        try {
            jsonWriter.object();
            
            jsonWriter.key("totalCount");
            jsonWriter.value(totalSize);

            JSONArray accountGroups = getGroupMaps(groups);
            jsonWriter.key(rootId);
            jsonWriter.value(accountGroups);
            
            jsonWriter.key("metaData");
            JSONObject metaData = new JSONObject();
            metaData.put("totalProperty", "totalCount");
            metaData.put("root", rootId);
            metaData.put("id", "id");
            setColumnData(columns, metaData); 

            jsonWriter.value(metaData);
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (GeneralException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        }
        return result;
    }

    private void setColumnData(List<ColumnConfig> columns, JSONObject metaData ) throws GeneralException {
        String widthString = (String) getRequestParam().get("currentWidth");
        int currentWidth;
        if (widthString == null) {
            currentWidth = 800;
        } else {
            try {
                currentWidth = Integer.parseInt(widthString);
            } catch (NumberFormatException e) {
                log.warn("Could not convert the width, " + widthString + ", to a number.  Setting a default width of 800.", e);
                currentWidth = 800;
            }
        }
        
        try {
            JSONArray fields = new JSONArray(); 
            JSONArray columnConfig = new JSONArray();
            if ( columns != null ) {
                for (ColumnConfig column : columns) {
                    JSONObject field = new JSONObject();
                    String jsonProp = column.getJsonProperty();
                    if ( ( jsonProp != null ) && 
                         ( ( "id".compareTo(jsonProp) == 0 ) || 
                           ( "application".compareTo(jsonProp) == 0 ) ) ) {
                        // skip these
                        continue;
                    } 
                    field.put("name", jsonProp);
                    fields.put(field);
                    JSONObject configObj = new JSONObject();
                    configObj.put("header", WebUtil.localizeMessage(column.getHeaderKey()));
                    configObj.put("dataIndex", column.getJsonProperty());
                    int percentWidth = column.getPercentWidth();
                    if (percentWidth > 0) {
                        configObj.put("width", percentWidth * (currentWidth / 100));
                    } else {
                        int width = column.getWidth();
                        if (width > 0) {
                            configObj.put("width", width);
                        }
                    }
                    configObj.put("sortable", column.isSortable());
                    configObj.put("hideable", column.isHideable());
                    configObj.put("hidden", column.isHidden());
                    columnConfig.put(configObj);
                }
            }
            metaData.put("fields", fields);
            metaData.put("columnConfig", columnConfig);
            String column = getRequestParameter("sort");
            if ( Util.getString(column) != null ) {
                // if it starts with a bracket, we can assume it is a JSON array of sorters, ExtJS Store style.
                if(column.startsWith("[")) {
                    List<Sorter> sorters = JsonHelper.listFromJson(Sorter.class, column);
                    
                    //protect against bad request values by escaping and including only the entries we want
                    for (Sorter sorter : Util.iterate(sorters)) {
                        sorter.setProperty(WebUtil.escapeHTML(sorter.getProperty(), false));
                        sorter.setDirection(WebUtil.escapeHTML(sorter.getDirection(), false));
                    }
                    
                    metaData.put("sorters", JsonHelper.toJson(sorters));
                }
                else {
                    metaData.put("sortColumn", WebUtil.escapeHTML(column, false));
                    metaData.put("sortDirection", sortAscending() ? "ASC" : "DESC");
                }
            } else {
                //provide default sorting
                String sortColumn = "displayableName";
                if (fields.getJSONObject(0) != null)
                    sortColumn = fields.getJSONObject(0).getString("name");
                metaData.put("sortColumn", sortColumn);
                metaData.put("sortDirection", sortAscending() ? "ASC" : "DESC");
            }
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
        }
    }

    private JSONArray getGroupMaps(List<ManagedAttribute> groups ) {
        JSONArray accountGroups = new JSONArray();
        if ( groups != null ) {
            for ( ManagedAttribute group : groups ) {
                Map<String,Object> groupMap = new HashMap<String,Object>();
                groupMap.put("id", group.getId());
                // XSS
                groupMap.put("displayableName", WebUtil.escapeHTML(group.getDisplayableName(), false));
                groupMap.put("attribute", group.getAttribute());
                groupMap.put("value", group.getValue());
                groupMap.put("application-name", group.getApplication().getName());
                String ownerName = "";
                if ( group.getOwner() != null ) 
                    ownerName = group.getOwner().getDisplayableName();
                groupMap.put("owner", ownerName);
                groupMap.put("owner-displayName", ownerName);
                Date mod = group.getModified();
                if ( mod != null ) {
                    groupMap.put("modified", Internationalizer.getLocalizedDate(mod, getLocale(), getUserTimeZone()));
                }
                accountGroups.put(groupMap);   
            }
        }
        return accountGroups;
    }

    public static String REQUEST_PARAM_TYPE_COMBO_APP = "application";
    public static String REQUEST_PARAM_FILTER_ACCOUNT_SCHEMA = "filterAccountSchema";
    public static String REQUEST_PARAM_DISTINCT_OBJECT_TYPES = "distinctObjectTypes";
    public static String REQUEST_PARAM_INCLUDE_MA = "includeMA";

    public static String TYPE_FIELD_LABEL = "label";
    public static String TYPE_FIELD_VALUE = "value";

    /**
     *
     * @return
     * @throws GeneralException
     */
    public String getTypeComboJson() throws GeneralException {


        //Add the default entries to the map
        List<Map<String, String>> records = new ArrayList<Map<String, String>>();
        HashMap<String, String> recAll = new HashMap<String, String>();
        if ("true".equalsIgnoreCase(getRequestParameter(REQUEST_PARAM_INCLUDE_MA))) {

            HashMap<String, String> recEnt = new HashMap<String, String>();
            recEnt.put(TYPE_FIELD_LABEL, new Message(MessageKeys.ENTITLEMENTS).getLocalizedMessage(getLocale(), null));
            recEnt.put(TYPE_FIELD_VALUE, AccountGroupListBean.PARAM_TYPE_ENTITLEMENTS);
            records.add(recEnt);
            HashMap<String, String> recPerm = new HashMap<String, String>();
            recPerm.put(TYPE_FIELD_LABEL, new Message(MessageKeys.PERMISSIONS).getLocalizedMessage(getLocale(), null));
            recPerm.put(TYPE_FIELD_VALUE, AccountGroupListBean.PARAM_TYPE_PERMISSIONS);
            records.add(recPerm);
        }


        List<String> projectionColumns = new ArrayList<String>();
        projectionColumns.clear();
        //Will want to fetch the i18n displayName when that gets implemented
        projectionColumns.add("schemas.objectType");
        projectionColumns.add("schemas.aggregationType");
        String sort = "schemas.objectType";
        QueryOptions qo = new QueryOptions();
        String selectedApp = super.getRequestParameter(REQUEST_PARAM_TYPE_COMBO_APP);
        if (Util.isNotNullOrEmpty(selectedApp)) {
            qo.add(Filter.eq("id", selectedApp));
        }

        if ("true".equalsIgnoreCase(getRequestParameter(REQUEST_PARAM_FILTER_ACCOUNT_SCHEMA))) {
            qo.add(Filter.ne("schemas.objectType", Application.SCHEMA_ACCOUNT));
        }
        boolean removeDups = false;
        if("true".equalsIgnoreCase(getRequestParameter(REQUEST_PARAM_DISTINCT_OBJECT_TYPES))) {
            //Do this after we've gotten results. Can't use distinct in the query since we're querying on Application
            removeDups = true;
        }

        //Add the groupTypes
        Iterator<Object []> objectTypesItr = getContext().search(Application.class, qo, projectionColumns);
        //Keep track of visited objectTypes in order to remove duplicates if request Param is set.
        LinkedHashSet<String> objectTypes = new LinkedHashSet<String>();
        while(objectTypesItr.hasNext()) {
            Object[] row = objectTypesItr.next();
            if(!Util.isNullOrEmpty((String) row[0])) {
                String objectType = (String)row[0];
                String aggregationType = (String)row[1];
                boolean isGroupAggregation = Schema.isGroupAggregation(objectType, aggregationType);
                String translatedObjectType = new Message(objectType).getLocalizedMessage(getLocale(), null);

                // Don't include the type if it doesn't aggregate Managed Attributes,
                // or if this type is a duplicate and request param is set to remove dups.
                if(!isGroupAggregation || (removeDups && objectTypes.contains(translatedObjectType))) {
                    continue;
                } else {
                    HashMap<String, String> rec = new HashMap<String, String>();
                    rec.put(TYPE_FIELD_LABEL, translatedObjectType);
                    rec.put(TYPE_FIELD_VALUE, objectType);
                    records.add(rec);
                    objectTypes.add(translatedObjectType);
                }
            }
        }

        return JsonHelper.success("totalCount", records.size(), "rows", records);
    }

    public String getMembersGridJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;
        
        try {
            jsonWriter.object();
            
            jsonWriter.key("totalCount");
            jsonWriter.value(getMemberCount());

            JSONArray members = new JSONArray();
            List<Map<String,Object>> memberList = null;
            memberList = getMemberList();
            
            if ( memberList != null ) {
                for ( Map<String,Object> member : memberList ) {
                    HashMap<String,Object> memberMap = new HashMap<String,Object>();
                    memberMap.put("id", (String)member.get("id"));
                    for ( ColumnConfig config : columns ) {
                        String jsonProperty = config.getJsonProperty();
                        String propName = config.getProperty();
                        String val = (String)member.get(propName);
                        if ( val == null ) val = "";
                        memberMap.put(jsonProperty, val);
                    }
                    members.put(memberMap);   
                }
            }
            jsonWriter.key("members");
            jsonWriter.value(members);
            
            jsonWriter.key("metaData");
            JSONObject metaData = new JSONObject();
           
            metaData.put("totalProperty", "totalCount");
            metaData.put("root", "members");
            metaData.put("id", "id");
            setColumnData(getColumns(), metaData);
            jsonWriter.value(metaData);
            
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (GeneralException ge) {
            log.error("Failed to generate JSON for this search", ge);
            result = "{}";
        }
        return result;
    }

    public String getGridDataJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;
        
        try {
            jsonWriter.object();
            
            jsonWriter.key("totalCount");
            jsonWriter.value("1");

            JSONArray counts = new JSONArray();
            HashMap<String,Object> gridsMap = new HashMap<String,Object>();
            gridsMap.put("memberCount", getMemberCount());
            gridsMap.put("inheritedCount", getInheritedSize());
            gridsMap.put("inheritingCount", getInheritingSize());
            gridsMap.put("permissionCount", getPermissionsCount());
            gridsMap.put("permissionGridMetaData", getPermissionColumnJson());
            gridsMap.put("accessGridMetaData", getAccessGridColumnJson());
            gridsMap.put("accessCount", getAccessCount());
            gridsMap.put("classificationGridMetaData", getClassificationGridColumnJson());
            gridsMap.put("classificationCount", getClassificationCount());
            gridsMap.put("displayName", (getObject() != null) ? getObject().getDisplayableName() : null);
            gridsMap.put("id", getObject() != null ? getObject().getId() : null);
            counts.put(gridsMap);

            jsonWriter.key("counts");
            jsonWriter.value(counts);
            
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (GeneralException ge) {
            log.error("Failed to generate JSON for this search", ge);
            result = "{}";
        }
        return result;
    }
   
    public void setTransitioning(String transitioning) {
        _inTransition = transitioning;
    }

    public String getTransitioning() {
        return _inTransition;
    }

    public String getDescription() throws GeneralException {
        String description = null;
        ManagedAttribute group = getObject();
        if ( group != null ) {
            // jsl - Explanator doesn't have an interface for this
            // but I'm not going to punt until we can decide
            // whether this class needs to live
            //description = Explanator.getDescription(group);
        }
        return description;
    }
    
    public void setDescription(String description) throws GeneralException {
        if ( !Util.atob(_inTransition ) )  {
            ManagedAttribute group = getObject();
            if ( group != null ) {
                group.setDescription(description);
            }
        }
    }

    public Identity getOwner() throws GeneralException {
        Identity owner = null;
        ManagedAttribute group = getObject();
        if ( group != null ) {
            owner = group.getOwner();    
        }
        return owner;
    }

    public void setOwner(Identity identity) throws GeneralException {
        if ( !Util.atob(_inTransition ) )  {
            ManagedAttribute group = getObject();
            if ( group != null ) {
                group.setOwner(identity);
            }
        }
    }

    public Scope getAssignedScope() throws GeneralException {
        Scope scope = null;
        ManagedAttribute group = getObject();
        if ( group != null ) {
            scope = group.getAssignedScope();    
        }
        return scope;

    }

    public void setAssignedScope(Scope scope) throws GeneralException {
        if ( !Util.atob(_inTransition ) )  {
            ManagedAttribute group = getObject();
            if ( group != null ) {
                group.setAssignedScope(scope);
            }
        }
    }
    
    public Boolean getDisplayEntDescriptions() {
        return displayEntDescriptions;
    }

    public void setDisplayEntDescriptions(Boolean displayEntDescriptions) {
        this.displayEntDescriptions = displayEntDescriptions;
    }
    
    private boolean initProvisioningRight() {
        boolean hasProvisioningRight;
        List<Capability> capabilities = getLoggedInUserCapabilities();
        Set<String> rights = new HashSet<String>(getLoggedInUserRights());
        hasProvisioningRight = rights.contains(SPRight.ManagedAttributeProvisioningAdministrator) || Capability.hasSystemAdministrator(capabilities);
        return hasProvisioningRight;
    }

    private BaseListResourceColumnSelector accountGroupAccessGridColumnSelector =
            new BaseListResourceColumnSelector(UIConfig.ACCOUNT_GROUP_ACCESS_TABLE_COLUMNS);

    public String getAccessGridJson() throws GeneralException {
        ManagedAttribute currentObj = getObject();
        ListResult result = null;

        if (currentObj != null) {

            EffectiveAccessListService svc = new EffectiveAccessListService(getContext(), this, accountGroupAccessGridColumnSelector);

            result = svc.getEffectiveAccess(currentObj.getId());

        }

        return JsonHelper.toJson(result);
    }

    public String getAccessGridColumnJson() throws GeneralException {
        return getColumnJSON("targetName", accountGroupAccessGridColumnSelector.getColumns());
    }
    
    private int getAccessCount() throws GeneralException {
        ManagedAttribute currentObj = getObject();
        if (currentObj != null) {
            EffectiveAccessListService svc = new EffectiveAccessListService(getContext(), this, accountGroupAccessGridColumnSelector);
            return svc.getEffectiveAccessCount(currentObj.getId());
        }
        
        return 0;
    }

    private BaseListResourceColumnSelector accountGroupClassificationGridColumnSelector =
            new BaseListResourceColumnSelector(UIConfig.UI_CLASSIFICATIONS_COLUMNS);


    public String getClassificationGridColumnJson() throws GeneralException {
        return getColumnJSON("name", accountGroupClassificationGridColumnSelector.getColumns());
    }

    private int getClassificationCount() throws GeneralException {
        ManagedAttribute currentObj = getObject();
        if (currentObj != null) {
            return Util.size(currentObj.getClassifications());
        }

        return 0;
    }


    @Override
    public int getStart() {
        return Util.atoi(getRequestParameter("start"));
    }

    @Override
    public int getLimit() {
        return getResultLimit();
    }

    @Override
    public String getQuery() {
        return null;
    }

    @Override
    public List<Sorter> getSorters(List<ColumnConfig> columnConfigs) throws GeneralException {
        String sort = getRequestParameter("sort");
        String sortDirection = this.getRequestParameter("dir");

        List<Sorter> sorts = null;
        if (!Util.isNullOrEmpty(sort)) {
            if (sort.startsWith("[")) {
                // if it starts with a bracket, we can assume it is a JSON array of
                // sorters, ExtJS Store style.
                sorts = JsonHelper.listFromJson(Sorter.class, sort);
            } else {
                Sorter sorter = new Sorter(sort, sortDirection, false);
                sorts = new ArrayList<Sorter>(Arrays.asList(sorter));
            }
        }

        return sorts;

    }
    
    @Override
    public String getGroupBy() {
        return null;
    }

    @Override
    public List<Filter> getFilters() {
        return null;
    }
}  // class AccountGroupBean
