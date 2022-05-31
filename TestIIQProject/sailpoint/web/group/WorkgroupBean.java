/**
 *
 */
package sailpoint.web.group;

import java.io.StringWriter;
import java.io.Writer;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.api.Identitizer;
import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.ScopeService;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.Identity.WorkgroupNotificationOption;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.UserRightsEditValidator;
import sailpoint.web.SailPointObjectDTO;
import sailpoint.web.group.WorkgroupBean.WorkgroupDTO.MemberDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.SelectItemByLabelComparator;
import sailpoint.web.util.WebUtil;

/**
 * @author dan.smith
 */
public class WorkgroupBean extends BaseObjectBean<Identity> implements
        NavigationHistory.Page, UserRightsEditValidator.UserRightsEditValidatorContext {

    private static Log log = LogFactory.getLog(WorkgroupBean.class);

    /**
     * Cached grid state.
     */
    private GridState gridState;

    /**
     * Cached copy of members
     */
    private List<Map<String, Object>> members;

    /**
     * Cached copy of members projection attributes.
     */
    private List<String> projectionAttributes;

    /**
     * Cached copy of members column config.
     */
    private List<ColumnConfig> columns;

    private static final String ATT_DTO = "workgroupDTO";
    private WorkgroupDTO dto;

    /**
     *
     */
    public WorkgroupBean() throws GeneralException {

        super();

        setScope(Identity.class);
        loadMemberColumns();
        loadProjectionAttributes();
        loadDto();
        setNeverAttach(true);
    }

    private void loadDto() throws GeneralException {
        WorkgroupDTO responseDto = null;
        final WorkgroupDTO sessiondDto = (WorkgroupDTO) getSessionScope().get(ATT_DTO);
        /* Determine if the session DTO is the one that we want */
        if (sessiondDto != null) {
            final Object sessionId = getSessionScope().get(WorkgroupListBean.ATT_OBJECT_ID);
            if (sessionId != null) {
                if (sessionId.equals(sessiondDto.getPersistentId())) {
                    responseDto = sessiondDto;
                }
            } else if (sessiondDto.isNewIdentity()) {
                responseDto = sessiondDto;
            }
        }
        if (responseDto == null) {
            responseDto = createDtoAndSaveInSession();
        }
        this.dto = responseDto;
    }

    @SuppressWarnings("unchecked")
    private WorkgroupDTO createDtoAndSaveInSession() throws GeneralException {

        WorkgroupDTO theDto;

        String id = (String) getSessionScope().get(
                WorkgroupListBean.ATT_OBJECT_ID);
        if (id == null) {
            // new object
            theDto = new WorkgroupDTO();
        } else {
            Identity workgroup = getContext().getObjectById(Identity.class, id);
            theDto = new WorkgroupDTO(workgroup, getWorkgroupMembers(workgroup));
        }

        getSessionScope().put(ATT_DTO, theDto);
        return theDto;
    }

    /**
     * Returns a list of MemberDTO objects for the workgroup
     *
     * @param identity - the Workgroup Identity
     * @return List of MemberDTOs
     * @throws GeneralException If an error occurs during search for members
     */
    private List<MemberDTO> getWorkgroupMembers(Identity identity) throws GeneralException {

        List<MemberDTO> members = new ArrayList<MemberDTO>();

        // bug 21397 - populate the member list using the projection attributes
        Iterator<Object[]> membersIterator = ObjectUtil.getWorkgroupMembers(getContext(), identity, this.projectionAttributes);
        while (membersIterator.hasNext()) {
            Object[] memberObj = membersIterator.next();
            MemberDTO member = new MemberDTO();

            int i = 0;
            for (String key : projectionAttributes) {
                if (key.equals("id")) {
                    member.setPersistentId((String) memberObj[i]);
                } else if (key.equals("name")) {
                    member.setName((String) memberObj[i]);
                } else {
                    Object value = memberObj[i];
                    if (value != null && Localizable.class.isAssignableFrom(value.getClass())) {
                        value = ((Localizable) value).getLocalizedMessage(getLocale(), getUserTimeZone());
                    }
                    member.setAttribute(key, value);
                }

                i++;
            }

            members.add(member);
        }

        return members;
    }

    public WorkgroupDTO getDto() {
        return this.dto;
    }

    /**
     * Overload from BaseObjectBean called to create a new object.
     */
    @Override
    public Identity createObject() {
        return new Identity();
    }

    @Override
    public Identity getObject() throws GeneralException {
        return super.getObject();
    }

    private void setCurrentTab() {
        Object bean = super.resolveExpression("#{groupNavigation}");
        if (bean != null)
            ((GroupNavigationBean) bean).setCurrentTab(1);


        Map session = getSessionScope();
        session.put("activeTab", "workgroupsGrid");

    }

    public String getId() {
        return this.dto.getPersistentId() == null ? "" : this.dto
                .getPersistentId();
    }

    public void setId(String s) {
    }

    /**
     * Returns a list of MemberDTO objects as a JSON response
     *
     * @return List of MemberDTOs as a JSON response
     * @throws GeneralException if an error occurs during construction of the
     *                          JSON response
     */
    public String getMembersJson() throws GeneralException {
        // bug 21397 - the DTO needs the ColumnConfigs to build the member JSON response.
        return this.dto.getMembersJson(createSortParams(), getMemberColumns());
    }

    public boolean isShowControlledScopesControl() throws GeneralException {
        ScopeService scopeSvc = new ScopeService(getContext());
        boolean show = scopeSvc.isScopingEnabled();
        if (show) {
            Identity user = getLoggedInUser();
            show = WebUtil.hasRight(getFacesContext(), "FullAccessGroup, SetWorkgroupCapability") ||
                    ((null != user) && Capability.hasSystemAdministrator(user.getCapabilityManager().getEffectiveCapabilities()));
        }

        return show;
    }

    // //////////////////////////////////////////////////////////////////////////
    //
    // GridState
    //
    // //////////////////////////////////////////////////////////////////////////

    public GridState loadGridState(String gridName) {
        GridState state = null;
        String name = "";
        IdentityService iSvc = new IdentityService(getContext());
        try {
            if (gridName != null)
                state = iSvc.getGridState(getLoggedInUser(), gridName);
        } catch (GeneralException ge) {
            log.info("GeneralException encountered while loading gridstates: "
                    + ge.getMessage());
        }

        if (state == null) {
            state = new GridState();
            state.setName(name);
        }
        return state;
    }

    public GridState getGridState() {
        if (null == this.gridState) {
            this.gridState = loadGridState("workgroupGridState");
        }
        return this.gridState;
    }

    public void setGridState(GridState gridState) {
        this.gridState = gridState;
    }

    // //////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    // //////////////////////////////////////////////////////////////////////////

    @Override
    public String saveAction() throws GeneralException {
        setCurrentTab();

        authorize(new RightAuthorizer(SPRight.FullAccessGroup, SPRight.ManageWorkgroup));

        if (!validate() || !isUnique(dto)) {
            return null;
        }

        SailPointContext ctx = getContext();

        Identity workgroup = null;
        if (this.dto.isNewIdentity()) {
            workgroup = getObject();
            workgroup.setName(this.dto.getName());
            workgroup.setWorkgroup(true);
            ctx.saveObject(workgroup);
            ctx.commitTransaction();
        } else {
            workgroup = ctx.getObjectById(Identity.class, this.dto.getPersistentId());
        }

        // defect 21296: we want to audit changes made for workgroups
        //set up previous for the diff that occurs in the Audit functions
        Identitizer idz = new Identitizer(ctx);
        Identity previous = (Identity) workgroup.deepCopy(((XMLReferenceResolver) ctx));

        this.dto.copyToIdentity(workgroup);
        this.dto.updateMembership(workgroup);

        idz.doWorkgroupTriggers(previous, workgroup, false);

        getContext().commitTransaction();

        clearHttpSession();

        String outcome = NavigationHistory.getInstance().back();
        if (outcome == null) {
            outcome = "save";
        }
        return outcome;
    }

    /**
     * Returns true is workgroup is unique (current only checks name for uniqueness)
     *
     * @param workgroup the workgroup to check for uniqueness
     * @return true if no other existing Identity matches unique fields of workgroup
     * @throws GeneralException When try to look up an object in peristence context fails
     */
    private boolean isUnique(WorkgroupDTO workgroup) throws GeneralException {
        boolean response = true;
        /* Workgroups are actually a special instance of Identity */
        Identity existingIdentity = getContext().getObjectByName(Identity.class, workgroup.getName());
        if (existingIdentity != null) {
			/* If an Identity exists with the name it is ok if it is this Identity (i.e. we are updating) */
            String existingId = existingIdentity.getId();
            String workgroupId = workgroup.getPersistentId();
            if (!existingId.equals(workgroupId)) {
                addMessage(new Message(Message.Type.Error, MessageKeys.WORKGROUP_DUPLICATE_NAME, workgroup.getName()));
                response = false;
            }
        }
        return response;
    }

    @Override
    public String cancelAction() throws GeneralException {
        setCurrentTab();

        clearHttpSession();

        String outcome = NavigationHistory.getInstance().back();
        if (outcome == null) {
            outcome = "cancel";
        }
        return outcome;
    }

    private boolean validate() throws GeneralException {
        if (Util.isNullOrEmpty(this.dto.getName())) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.NAME_REQUIRED), null);
            return false;
        }

        UserRightsEditValidator validator = new UserRightsEditValidator(this, this);
        List<Message> errors = validator.validate();
        if (!Util.isEmpty(errors)) {
            for (Message error: errors) {
                this.addMessage(error);
            }

            return false;
        }

        if (Util.size(getNewMemberIds()) > 0 && validator.hasUnmatchedPrivileges()) {
            addMessage(Message.error(MessageKeys.ERR_USER_RIGHTS_WORKGROUP_MEMBERS));
            return false;
        }

        return true;
    }

    @Override
    public void clearHttpSession() {

        super.clearHttpSession();

        getSessionScope().remove(ATT_DTO);
    }

    // //////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    // //////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Edit Workgroup Page";
    }

    public String getNavigationString() {
        return "editWorkgroup";
    }

    public Object calculatePageState() {
        Object[] state = new Object[1];
        state[0] = this.getGridState();
        return state;
    }

    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        setGridState((GridState) myState[0]);
    }

    // /////////////////////////////////////////////////////////////////////////
    //
    // Group Rights
    //
    // /////////////////////////////////////////////////////////////////////////

    public SelectItem[] getAllCapabilities() throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.setScopeResults(true);
        List<Capability> caps = getContext().getObjects(Capability.class, qo);
        List<SelectItem> items = new ArrayList<SelectItem>();

        for (Capability cap : caps) {
            items.add(new SelectItem(cap.getName(), super.getMessage(cap
                    .getDisplayName())));
        }

        // Sort in memory since the labels are i18n'ized.
        Collections.sort(items, new SelectItemByLabelComparator(getLocale()));

        return items.toArray(new SelectItem[items.size()]);
    }

    // /////////////////////////////////////////////////////////////////////////
    //
    // Workgroup Membership
    //
    // /////////////////////////////////////////////////////////////////////////

    public String removeMembers() throws GeneralException {

        this.dto.removeCurrentMembers();
        return "";
    }

    /**
     * Adds a member to the workgroup
     *
     * @return an empty string??
     * @throws GeneralException if an error occurs during the addition of the
     *                          MemberDTO
     */
    public String addMember() throws GeneralException {
        // bug 21397 - the DTO needs the projection attributes to build the
        // member object
        this.dto.addCurrentMember(this.projectionAttributes);
        return "";
    }

    private List<String> getNewMemberIds() {
        return this.dto.idsToAdd;
    }

    // /////////////////////////////////////////////////////////////////////////
    //
    // Membership
    //
    // /////////////////////////////////////////////////////////////////////////

    private QueryOptions getMembersQueryOptions(Identity workgroup,
                                                boolean limitResults) throws GeneralException {

        QueryOptions qo = new QueryOptions();

        if ((workgroup == null) || (Util.getString(workgroup.getId()) == null))
            return null;

        List<Identity> wgs = new ArrayList<Identity>();
        wgs.add(workgroup);

        qo.add(Filter.containsAll("workgroups", wgs));

        if (limitResults) {
            int start = Util.atoi(getRequestParameter("start"));
            int limit = getResultLimit();

            if (start > 0)
                qo.setFirstRow(start);
            if (limit > 0)
                qo.setResultLimit(limit);
        }

        // TODO: get sort information
        qo.setOrderBy("name");

        String direction = super.getRequestParameter("dir");
        if (direction != null) {
            qo.setOrderAscending(direction.equalsIgnoreCase("ASC"));
        } else {
            qo.setOrderAscending(true);
        }

        return qo;
    }

    private SortParams createSortParams() {

        SortParams params = new SortParams();

        params.setStart(Util.atoi(getRequestParameter("start")));
        params.setLimit(getResultLimit());
        String direction = super.getRequestParameter("dir");
        String sort = getRequestParameter("sort");
        if (direction != null) {
            params.setAscending(direction.equalsIgnoreCase("ASC"));
            params.setSort(sort);
        } else {
            JSONArray sortArray = null;
            try {
                sortArray = new JSONArray(sort);
                JSONObject sortObject = sortArray.getJSONObject(0);
                direction = sortObject.getString("direction");
                params.setAscending("ASC".equalsIgnoreCase(direction));
                params.setSort(sortObject.getString("property"));
            } catch (Exception e) {
                log.debug("Invalid sort input.");
                params.setAscending(false);
            }
        }

        return params;
    }

    private static class SortParams {
        private int start;
        private int limit;
        private boolean ascending;
        private String sort;

        public int getStart() {
            return this.start;
        }

        public void setStart(int start) {
            this.start = start;
        }

        public int getLimit() {
            return this.limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public boolean isAscending() {
            return this.ascending;
        }

        public void setAscending(boolean ascending) {
            this.ascending = ascending;
        }

        public String getSort() {
            return sort;
        }

        public void setSort(String sort) {
            this.sort = sort;
        }
    }

    @SuppressWarnings("unused")
    private int getMemberCount() throws GeneralException {
        int size = 0;
        Identity workgroup = getObject();
        QueryOptions qo = getMembersQueryOptions(workgroup, false);
        if (qo != null)
            size = getContext().countObjects(Identity.class, qo);
        return size;
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> getMemberList() throws GeneralException {
        if (this.members == null) {
            Identity group = getObject();

            QueryOptions qo = null;
            try {
                qo = getMembersQueryOptions(group, true);
            } catch (GeneralException ex) {
                Message msg = new Message(Message.Type.Error,
                        MessageKeys.ERR_GROUP_CANT_CREATE_QUERY, ex);
                log.error(msg.getMessage(), ex);
                addMessage(msg, null);
                qo = null;
            }

            this.members = new ArrayList<Map<String, Object>>();

            if (group.getId() == null)
                return this.members;

            if (qo != null) {
                Iterator<Object[]> identities = null;
                try {
                    identities = getContext().search(Identity.class, qo, this.projectionAttributes);
                } catch (GeneralException ex) {
                    Message msg = new Message(Message.Type.Error,
                            MessageKeys.ERR_GROUP_CANT_LIST_IDS, ex);
                    log.error(msg.getLocalizedMessage(), ex);
                    addMessage(msg, null);

                }

                if (identities != null) {
                    while (identities.hasNext()) {
                        Object[] identity = identities.next();
                        Map<String, Object> idMap = mapFromProjection(identity);
                        if (idMap != null)
                            this.members.add(idMap);
                    }
                }
            }
        }
        return this.members;
    }

    private Map<String, Object> mapFromProjection(Object[] obj)
            throws GeneralException {
        Map<String, Object> idMap = null;
        if (obj != null && obj.length > 1) {
            idMap = new HashMap<String, Object>();
            int i = 0;
            for (String col : this.projectionAttributes) {
                // if the value can be localized. If so, localize it
                Object value = obj[i];
                if (value != null
                        && Localizable.class.isAssignableFrom(value.getClass())) {
                    value = ((Localizable) value).getLocalizedMessage(
                            getLocale(), getUserTimeZone());
                }
                idMap.put(col, value);
                i++;
            }
        }
        return idMap;
    }

    private void loadProjectionAttributes() throws GeneralException {

        this.projectionAttributes = new ArrayList<String>();

        List<ColumnConfig> cols = getMemberColumns();
        if (cols != null) {
            for (ColumnConfig col : cols) {
                if (col.getProperty() != null) {
                    this.projectionAttributes.add(col.getProperty());
                }
            }
        }
        if (!this.projectionAttributes.contains("id")) {
            this.projectionAttributes.add("id");
        }
    }

    /**
     * Default sorting for the grid
     *
     * @return
     */
    public String getDefaultSort() {
        return "name";
    }

    public List<ColumnConfig> getMemberColumns() {
        return this.columns;
    }

    /**
     * @return Serialized GridResponseMetaData
     * @throws GeneralException
     */
    public String getMemberColumnJSON() throws GeneralException {
        return super.getColumnJSON(getDefaultSort(), getMemberColumns());
    }

    private void loadMemberColumns() throws GeneralException {
        this.columns = super.getUIConfig().getWorkgroupMemberTableColumns();
    }

    // /////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    // /////////////////////////////////////////////////////////////////////////

    /**
     * Convert a list of objects to a String array, assuming that the toString
     * method is suitable for the array. Could be a generic util.
     */
    private static String[] getObjectNames(
            List<? extends SailPointObject> objects) {

        String[] strings = null;
        if (objects != null) {
            strings = new String[objects.size()];
            for (int i = 0; i < strings.length; i++) {
                SailPointObject o = objects.get(i);
                strings[i] = (null != o) ? o.getName() : "";
            }
        } else {
            // Note that for list with JSF selector components,
            // the array must *not* be null
            strings = new String[0];
        }

        return strings;
    }

    /**
     * Must override this here since we are querying on Identities and we have
     * to include workgroup = true flag.
     */
    @Override
    protected QueryOptions addScopeExtensions(QueryOptions ops) {
        ops.add(Filter.eq("workgroup", true));
        return ops;
    }


    //////////////////////////////////////////////////////////////////////
    // UserRightsEditValidatorContext
    //////////////////////////////////////////////////////////////////////

    @Override
    public List<String> getNewCapabilities() throws GeneralException {
        return this.dto.getCapabilities();
    }

    @Override
    public List<String> getNewControlledScopes() throws GeneralException {
        List<String> scopes = this.dto.getControlledScopes();
        if (this.dto.getAssignedScope() != null && this.dto.isControlsAssignedScope()) {
            if (scopes == null) {
                scopes = new ArrayList<>();
            }
            scopes.add(this.dto.getAssignedScope());
        }
        return scopes;
    }

    @Override
    public List<String> getExistingCapabilities() throws GeneralException {
        return ObjectUtil.getObjectNames(getObject().getCapabilityManager().getEffectiveCapabilities());
    }

    @Override
    public List<String> getExistingControlledScopes() throws GeneralException {
        return ObjectUtil.getObjectIds(getObject().getEffectiveControlledScopes(Configuration.getSystemConfig()));
    }

    @Override
    public String getCapabilityRight() {
        return SPRight.SetWorkgroupCapability;
    }

    @Override
    public String getControlledScopeRight() {
        return SPRight.SetWorkgroupControlledScope;
    }

    @SuppressWarnings("unchecked")
    public static class WorkgroupDTO extends SailPointObjectDTO {
        private static final long serialVersionUID = 1L;

        private boolean newIdentity;
        private boolean controlsAssignedScope;
        private String assignedScopeDisplayName;
        private String ownerDisplayName;
        private String email;
        private List<String> capabilities;
        private List<String> controlledScopes;
        private String controlledScopesJson;
        private String notificationOption;
        private List<MemberDTO> members;
        // the following two are aggregate
        private List<String> idsToAdd;
        private List<String> idsToRemove;
        // the following two are current
        private String currentMemberIdToAdd;
        private List<String> currentMemberIdsToRemove;


        public static class MemberDTO extends SailPointObjectDTO {

            private static final long serialVersionUID = 1L;

            // bug 21397 - use the projection attributes to populate the member
            private Attributes<String, Object> attributes = new Attributes<String, Object>();

            @Deprecated
            public String getFirstName() {
                return (String) attributes.get("firstname");
            }

            @Deprecated
            public void setFirstName(String value) {
                attributes.put("firstname", value);
            }

            @Deprecated
            public String getLastName() {
                return (String) attributes.get("lastname");
            }

            @Deprecated
            public void setLastName(String value) {
                attributes.put("lastname", value);
            }

            /**
             * Insert a member attribute into it's attribute map
             *
             * @param key   - the attribute key
             * @param value - the attribute value
             * @return none
             */
            public void setAttribute(String key, Object value) {
                if (key.equals("id")) {
                    this.setPersistentId((String) value);
                } else if (key.equals("name")) {
                    this.setName((String) value);
                } else {
                    attributes.put(key, value);
                }
            }

            /**
             * Get a member attribute from it's map
             *
             * @param key - the attribute key
             * @return the value of the attribute
             */
            public Object getAttribute(String key) {
                if (key.equals("id")) {
                    return this.getPersistentId();
                } else if (key.equals("name")) {
                    return this.getName();
                } else {
                    return attributes.get(key);
                }
            }

            /**
             * Set the member attributes map
             *
             * @param attrs - the attribute map
             * @return none
             */
            public void setAttributes(Attributes<String, Object> attrs) {
                this.attributes = attrs;
            }

            /**
             * Get the member attributes map
             *
             * @return the member attributes map
             */
            public Attributes<String, Object> getAttributes() {
                return this.attributes;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == null) {
                    return false;
                }
                if (this == obj) {
                    return true;
                }

                MemberDTO other = MemberDTO.class.cast(obj);
                return getPersistentId().equals(other.getPersistentId());
            }

            @Override
            public int hashCode() {
                return getPersistentId().hashCode();
            }

            /**
             * Create a JSONObject for the member
             *
             * @param columns - the member table ColumnConfigs from UIConfig
             * @return the JSONObject for the member
             * @throws JSONException
             */
            public JSONObject toJsonObject(List<ColumnConfig> columns) throws JSONException {
                JSONObject memberAttrs = new JSONObject();

                // bug 21397 - Adding the list of ColumnConfigs as a parameter so the JSON
                // response gets created correctly.
                // Each property needs to be JSONized before adding it to
                // the JSON object to be serialized. In the case of a Date, we need to
                // format and localize it.
                memberAttrs.put("id", getPersistentId());
                for (ColumnConfig config : columns) {
                    String jsonProperty = config.getJsonProperty();
                    String propName = config.getProperty();

                    String value = "";
                    if (getAttribute(propName) != null) {
                        if (getAttribute(propName) instanceof Date) {
                            value = (Internationalizer.getLocalizedDate((Date) getAttribute(propName), getLocale(), getUserTimeZone()));
                        } else {
                            value = getAttribute(propName).toString();
                        }
                    }
                    memberAttrs.put(jsonProperty, value);
                }

                return memberAttrs;
            }
        }


        public static class MemberSorter implements Comparator<MemberDTO> {

            private SortParams params;

            public MemberSorter(SortParams params) {
                this.params = params;
            }

            public int compare(MemberDTO left, MemberDTO right) {
                Object v1 = left.getAttribute(params.getSort());
                Object v2 = right.getAttribute(params.getSort());

                if ((v1 != null) && (v2 != null)) {
                    return Collator.getInstance().compare(v1, v2);
                } else if (v1 == null && v2 != null) {
                    return -1;
                } else if (v1 != null && v2 == null) {
                    return 1;
                } else if ((null == v1) && (null == v2)) {
                    return 0;
                }

                return -1;
            }

        }


        // for new identities
        public WorkgroupDTO() throws GeneralException {
            this.newIdentity = true;
            this.members = new ArrayList<MemberDTO>();
            this.idsToAdd = new ArrayList<String>();
            this.idsToRemove = new ArrayList<String>();
            this.capabilities = new ArrayList<String>();
            this.controlledScopes = new ArrayList<String>();
            this.controlledScopesJson = WebUtil.basicJSONData(this.controlledScopes);
        }

        public WorkgroupDTO(Identity identity, List<MemberDTO> members) throws GeneralException {

            super(identity);

            if (identity == null) {
                throw new IllegalStateException(
                        "Null Identity not expected here");
            }

            this.newIdentity = false;
            this.members = members;
            this.idsToAdd = new ArrayList<String>();
            this.idsToRemove = new ArrayList<String>();

            this.assignedScopeDisplayName = identity.getAssignedScope() == null ? ""
                    : identity.getAssignedScope().getDisplayableName();
            this.ownerDisplayName = identity.getOwner() == null ? "" : identity
                    .getOwner().getDisplayableName();
            this.email = identity.getEmail() == null ? "" : identity.getEmail();

            loadControlsAssignedScope(identity);

            loadCapabilities(identity);

            loadControlledScopes(identity);

            this.notificationOption = identity.getNotificationOption() == null ? null
                    : identity.getNotificationOption().toString();

        }

        private void loadControlsAssignedScope(Identity identity)
                throws GeneralException {
            // If not yet set, This looks at the value on the identity and
            // will fall back to the system config property if not set.
            this.controlsAssignedScope = identity.getControlsAssignedScope() == null ? identity
                    .getControlsAssignedScope(getContext().getConfiguration())
                    : identity.getControlsAssignedScope();

        }

        private void loadCapabilities(Identity identity) {
            String[] capnames = getObjectNames(identity.getCapabilityManager().getEffectiveCapabilities());
            this.capabilities = Arrays.asList(capnames);
        }

        private void loadControlledScopes(Identity identity)
                throws GeneralException {
            this.controlledScopes = WebUtil.objectListToIds(identity
                    .getControlledScopes());
            this.controlledScopesJson = WebUtil.basicJSONData(identity
                    .getControlledScopes());
        }

        public boolean isNewIdentity() {
            return this.newIdentity;
        }

        public boolean isControlsAssignedScope() {
            return this.controlsAssignedScope;
        }

        public void setControlsAssignedScope(boolean controlsAssignedScope) {
            this.controlsAssignedScope = controlsAssignedScope;
        }

        public String getEmail() {
            return this.email;
        }

        public void setEmail(String val) {
            this.email = val;
        }

        public String getAssignedScopeDisplayName() {
            return this.assignedScopeDisplayName;
        }

        public String getOwnerDisplayName() {
            return this.ownerDisplayName;
        }

        public List<String> getCapabilities() {
            return this.capabilities;
        }

        public void setCapabilities(List<String> capabilities) {
            this.capabilities = capabilities;
        }

        public List<String> getControlledScopes() {
            return this.controlledScopes;
        }

        public void setControlledScopes(List<String> controlledScopes) {
            this.controlledScopes = controlledScopes;
        }

        public String getControlledScopesJson() {
            return this.controlledScopesJson;
        }

        public String getNotificationOption() {
            return this.notificationOption;
        }

        public void setNotificationOption(String notificationOption) {
            this.notificationOption = notificationOption;
        }

        public String getCurrentMemberIdToAdd() {
            return this.currentMemberIdToAdd;
        }

        public void setCurrentMemberIdToAdd(String currentMemberIdToAdd) {
            this.currentMemberIdToAdd = currentMemberIdToAdd;
        }

        public List<String> getCurrentMemberIdsToRemove() {
            return this.currentMemberIdsToRemove;
        }

        public void setCurrentMemberIdsToRemove(List<String> currentMemberIdsToRemove) {
            this.currentMemberIdsToRemove = currentMemberIdsToRemove;
        }

        /**
         * Add the current member to the workgroup
         *
         * @param projectionAttributes - the projection attributes to use to populate the
         *                             new member
         * @return none
         * @throws GeneralException from the member search
         */
        public void addCurrentMember(List<String> projectionAttributes) throws GeneralException {

            if (Util.isNullOrEmpty(this.currentMemberIdToAdd)) {
                return;
            }

            // bug 21397- the projection attributes are needed to populate the member
            // based on the UIConfig
            MemberDTO member = populateFromId(this.currentMemberIdToAdd, projectionAttributes);

            if (!this.members.contains(member)) {
                this.members.add(member);
                if (!this.idsToAdd.contains(this.currentMemberIdToAdd)) {
                    this.idsToAdd.add(member.getPersistentId());
                }
            }
        }

        /**
         * Populate the member attributes from the id using the projection attributes
         *
         * @param projectionAttributes - the projection attributes to use to populate the
         *                             new member
         * @return a MemberDTO for the new member
         * @throws GeneralException from the member search
         */
        private MemberDTO populateFromId(String id, List<String> projectionAttributes) throws GeneralException {

            MemberDTO member = new MemberDTO();

            // bug 21397 - search the member identities using the projection attributes and
            // populate the member
            Filter idFilter = Filter.eq("id", id);
            QueryOptions ops = new QueryOptions(idFilter);

            Iterator<Object[]> membersIterator = getContext().search(Identity.class, ops, projectionAttributes);
            if (membersIterator.hasNext()) {
                Object[] memberObj = membersIterator.next();

                int i = 0;
                for (String key : projectionAttributes) {
                    if (key.equals("id")) {
                        member.setPersistentId((String) memberObj[i]);
                    } else if (key.equals("name")) {
                        member.setName((String) memberObj[i]);
                    } else {
                        Object value = memberObj[i];
                        if (value != null && Localizable.class.isAssignableFrom(value.getClass())) {
                            value = ((Localizable) value).getLocalizedMessage(getLocale(), getUserTimeZone());
                        }
                        member.setAttribute(key, value);
                    }

                    i++;
                }
            }

            return member;
        }

        /**
         * Remove the selected members from the workgroup. The changes will be
         * persisted during the save.
         *
         * @return none
         */
        public void removeCurrentMembers() {

            // bug 21397 - refactored to use the member id to remove the member
            // rather than creating a MemberDTO
            for (String idToRemove : this.currentMemberIdsToRemove) {
                Iterator<MemberDTO> memberIterator = this.members.iterator();
                while (memberIterator.hasNext()) {
                    MemberDTO member = (MemberDTO) memberIterator.next();

                    String memberId = member.getPersistentId();
                    if (memberId.equals(idToRemove)) {
                        memberIterator.remove();

                        if (this.idsToAdd.contains(memberId)) {
                            this.idsToAdd.remove(memberId);
                        } else {
                            if (!this.idsToRemove.contains(memberId)) {
                                this.idsToRemove.add(memberId);
                            }
                        }
                    }
                }
            }
        }

        public void copyToIdentity(Identity identity) throws GeneralException {

            super.commit(identity);

            identity.setEmail(this.email);
            if (Util.isNullOrEmpty(this.email)) {
                identity.setEmail(null);
            }
            identity.setControlsAssignedScope(this.controlsAssignedScope);

            identity.setCapabilities(buildCapabilityObjects());

            identity.setControlledScopes(buildScopeObjects());

            if (!Util.isNullOrEmpty(this.notificationOption)) {
                identity.setNotificationOption(WorkgroupNotificationOption
                        .valueOf(this.notificationOption));
            }

            if (Util.isNullOrEmpty(trim(getOwner()))) {
                identity.setOwner(null);
            }

            if (Util.isNullOrEmpty(trim(getAssignedScope()))) {
                identity.setAssignedScope(null);
            }
        }

        public void updateMembership(Identity workgroup) throws GeneralException {

            SailPointContext context = getContext();
            boolean modifiedSomething = false;

            // add
            for (String id : this.idsToAdd) {
                Identity identity = context.getObjectById(Identity.class, id);
                if (identity != null) {
                    identity.add(workgroup);
                    context.saveObject(identity);
                    modifiedSomething = true;
                    auditWorkgroupIdentityChange(AuditEvent.IdentityWorkgroupAdd, workgroup, identity);
                } else {
                    WorkgroupBean.log.warn("Object with id: " + id + " was not found");
                }
            }

            // remove
            for (String id : this.idsToRemove) {
                Identity identity = context.getObjectById(Identity.class, id);
                if (identity != null) {
                    identity.remove(workgroup);
                    context.saveObject(identity);
                    modifiedSomething = true;
                    auditWorkgroupIdentityChange(AuditEvent.IdentityWorkgroupRemove, workgroup, identity);
                } else {
                    WorkgroupBean.log.warn("Object with id: " + id + " was not found");
                }
            }

            if (modifiedSomething) {
                context.commitTransaction();
            }
        }

        /**
         * Audit the fact that an identity was either added to or removed from a workgroup
         * @param eventName The name of the event being audited
         * @param workgroup The workgroup that the identity is being removed from/added to
         * @param identity The identity being added to the workgroup or removed from it
         */
        private void auditWorkgroupIdentityChange(String eventName, Identity workgroup, Identity identity) {
            if (Auditor.isEnabled(eventName)) {
                AuditEvent event = new AuditEvent();
                event.setAction(eventName);
                //IIQTC-241 :- Leaving the default source, make use of target(identity affected)
                //and String1/Value1 for the workgroup name.
                event.setString1(workgroup.getName());
                event.setTarget(identity.getName());
                Auditor.log(event);
            }
        }

        private List<Capability> buildCapabilityObjects()
                throws GeneralException {
            List<Capability> capabilityObjects = new ArrayList<Capability>();
            if (this.capabilities != null) {
                for (String capname : this.capabilities) {
                    Capability cap = getContext().getObjectByName(
                            Capability.class, capname);
                    if (cap != null)
                        capabilityObjects.add(cap);
                }
            }
            return capabilityObjects;
        }

        private List<Scope> buildScopeObjects() throws GeneralException {
            List<Scope> scopeObjects = new ArrayList<Scope>();
            if (this.controlledScopes != null) {
                for (String scopeId : this.controlledScopes) {
                    Scope scope = getContext().getObjectById(Scope.class,
                            scopeId);
                    if (scope != null)
                        scopeObjects.add(scope);
                }
            }
            return scopeObjects;
        }

        /**
         * Create a JSON response for the workgroup members
         *
         * @param params        - the params used to sort the members
         * @param memberColumns - the UIConfig ColumnConfigs for the member attributes
         * @return the JSON response as a String
         * @throws GeneralException from creating the JSON response
         */
        public String getMembersJson(SortParams params, List<ColumnConfig> memberColumns) throws GeneralException {

            try {
                Writer stringWriter = new StringWriter();
                JSONWriter jsonWriter = new JSONWriter(stringWriter);
                jsonWriter.object();

                jsonWriter.key("totalCount");
                jsonWriter.value(this.members.size());

                jsonWriter.key("workgroupMembers");
                JSONArray membersArray = new JSONArray();
                if(params.getSort()!=null) {
                    Collections.sort(this.members, new MemberSorter(params));

                    if (!params.isAscending()) {
                        Collections.reverse(this.members);
                    }
                }

                for (MemberDTO member : getInRange(params)) {
                    // bug 21397 - adding ColumnConfigs to the method that creates
                    // the member JSON response
                    membersArray.put(member.toJsonObject(memberColumns));
                }
                jsonWriter.value(membersArray);

                jsonWriter.endObject();
                return stringWriter.toString();
            } catch (JSONException ex) {
                throw new GeneralException(ex.getCause());
            }
        }

        private List<MemberDTO> getInRange(SortParams params) {

            int start = params.getStart();
            int end = start + params.getLimit();
            if (end > this.members.size()) {
                end = this.members.size();
            }

            return this.members.subList(start, end);
        }

    }
}
