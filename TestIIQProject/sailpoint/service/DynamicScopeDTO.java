/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for an DynamicScope.
 *
 * Author: danny.feng
 */

package sailpoint.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import sailpoint.api.SailPointContext;
import sailpoint.integration.JsonUtil;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.DynamicScope;
import sailpoint.object.DynamicScope.PopulationRequestAuthority;
import sailpoint.object.DynamicScope.PopulationRequestAuthority.MatchConfig;
import sailpoint.object.Filter;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityAttributeFilterControl;
import sailpoint.object.IdentitySelector;
import sailpoint.object.QuickLink;
import sailpoint.object.QuickLinkOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.TargetSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;

public class DynamicScopeDTO extends BaseDTO {
    private static final long serialVersionUID = -5258821135243522826L;
    
    private static final Log log = LogFactory.getLog(DynamicScopeDTO.class);

    static final String KEY_POPULATION_DEFINITION_TYPE = "populationDefinitionType";

    public static final String KEY_ID = "id";
    public static final String KEY_NAME = "name";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_DISPLAYABLE_NAME = "displayableName";

    public static final String KEY_QUICK_LINK_ID = "quickLinkId";
    public static final String KEY_ALLOW_SELF = "allowSelf";
    public static final String KEY_ALLOW_OTHER = "allowOther";
    public static final String KEY_ALLOW_BULK = "allowBulk";
    public static final String KEY_OPTIONS = "options";
    public static final String KEY_CATEGORY = "category";
    public static final String KEY_CHILDREN = "children";

    public static final String KEY_ALLOW_ALL = "allowAll";
    public static final String KEY_SELECTOR = "selector";
    public static final String KEY_INCLUSIONS = "inclusions";
    public static final String KEY_EXCLUSIONS = "exclusions";

    public static final String KEY_POPULATION_REQUEST_AUTHORITY = "populationRequestAuthority";
    public static final String KEY_APPLICATION_REQUEST_CONTROL = "applicationRequestControl";
    public static final String KEY_MANAGED_ATTRIBUTE_REQUEST_CONTROL = "managedAttributeRequestControl";
    public static final String KEY_ROLE_REQUEST_CONTROL = "roleRequestControl";
    public static final String KEY_APPLICATION_REMOVE_CONTROL = "applicationRemoveControl";
    public static final String KEY_MANAGED_ATTRIBUTE_REMOVE_CONTROL = "managedAttributeRemoveControl";
    public static final String KEY_ROLE_REMOVE_CONTROL = "roleRemoveControl";
    public static final String KEY_QUICK_LINK_OPTIONS_LIST = "quickLinkOptionsList";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private DynamicScope _ds;
    private String name;
    private String description;
    private Map<String, Object> populationRequestControlMap = new HashMap<String, Object>();
    private List<String> inclusions = new ArrayList<String>();
    private List<String> exclusions = new ArrayList<String>();
    private String roleRequestControlId;
    private String roleRequestControlName;
    private String applicationRequestControlId;
    private String applicationRequestControlName;
    private String managedAttributeRequestControlId;
    private String managedAttributeRequestControlName;
    private String roleRemoveControlId;
    private String roleRemoveControlName;
    private String applicationRemoveControlId;
    private String applicationRemoveControlName;
    private String managedAttributeRemoveControlId;
    private String managedAttributeRemoveControlName;
    private List<QuickLinkOptions> _quickLinkOptionsList;
    private SailPointContext _context;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public DynamicScopeDTO(DynamicScope src,
                           List<QuickLinkOptions> quickLinkOptionsList,
                           SailPointContext context) throws GeneralException {
        _ds = src;
        setName(src.getName());
        setDescription(src.getDescription());
        initPopulationRequestControlMap(src);
        initIdentityLists(src);
        initRules(src);
        
        //bug#26262 -- set default value for maxHierarchyDepth
        applyDefaultMaxHierarchyDepth();
        
        _quickLinkOptionsList = quickLinkOptionsList;
        _context = context;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DynamicScopeDTO(Map<String,Object> data, SailPointContext context)
        throws GeneralException {
        _context = context;

        _ds = new DynamicScope();

        applyProperties(context, _ds, data);

        _quickLinkOptionsList = deserializeQuickLinkOptionsList(
                (List<Map>) data.get(KEY_QUICK_LINK_OPTIONS_LIST), _ds, context);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    public String getName() {
        return name;
    }

    public void setName(String s) {
        this.name = s;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DynamicScope getDynamicScope() {
        return _ds;
    }

    public Map<String,Object> getPopulationRequestControlMap() {
        return populationRequestControlMap;
    }

    public void setPopulationRequestControlMap(Map<String,Object> populationRequestControlMap) {
        this.populationRequestControlMap = populationRequestControlMap;
    }

    public List<String> getInclusions() {
        return inclusions;
    }

    public void setInclusions(List<String> inclusions) {
        this.inclusions = inclusions;
    }
    
    public List<String> getExclusions() {
        return exclusions;
    }

    public void setExclusions(List<String> exclusions) {
        this.exclusions = exclusions;
    }
    
    public String getRoleRequestControlId() {
        return roleRequestControlId;
    }

    public void setRoleRequestControlId(String roleRequestControlId) {
        this.roleRequestControlId = roleRequestControlId;
    }

    public String getRoleRequestControlName() {
        return roleRequestControlName;
    }

    public void setRoleRequestControlName(String roleRequestControlName) {
        this.roleRequestControlName = roleRequestControlName;
    }

    public String getApplicationRequestControlId() {
        return applicationRequestControlId;
    }

    public void setApplicationRequestControlId(String applicationRuleId) {
        this.applicationRequestControlId = applicationRuleId;
    }

    public String getApplicationRequestControlName() {
        return applicationRequestControlName;
    }

    public void setApplicationRequestControlName(String applicationRuleName) {
        this.applicationRequestControlName = applicationRuleName;
    }

    public String getManagedAttributeRequestControlId() {
        return managedAttributeRequestControlId;
    }

    public void setManagedAttributeRequestControlId(String managedAttributeRequestControlId) {
        this.managedAttributeRequestControlId = managedAttributeRequestControlId;
    }

    public String getManagedAttributeRequestControlName() {
        return managedAttributeRequestControlName;
    }

    public void setManagedAttributeRequestControlName(String managedAttributeRequestControlName) {
        this.managedAttributeRequestControlName = managedAttributeRequestControlName;
    }

    public String getRoleRemoveControlId() {
        return roleRemoveControlId;
    }

    public void setRoleRemoveControlId(String roleRemoveControlId) {
        this.roleRemoveControlId = roleRemoveControlId;
    }

    public String getRoleRemoveControlName() {
        return roleRemoveControlName;
    }

    public void setRoleRemoveControlName(String roleRemoveControlName) {
        this.roleRemoveControlName = roleRemoveControlName;
    }

    public String getApplicationRemoveControlId() {
        return applicationRemoveControlId;
    }

    public void setApplicationRemoveControlId(String applicationRemoveControlId) {
        this.applicationRemoveControlId = applicationRemoveControlId;
    }

    public String getApplicationRemoveControlName() {
        return applicationRemoveControlName;
    }

    public void setApplicationRemoveControlName(
            String applicationRemoveControlName) {
        this.applicationRemoveControlName = applicationRemoveControlName;
    }

    public String getManagedAttributeRemoveControlId() {
        return managedAttributeRemoveControlId;
    }

    public void setManagedAttributeRemoveControlId(
            String managedAttributeRemoveControlId) {
        this.managedAttributeRemoveControlId = managedAttributeRemoveControlId;
    }

    public String getManagedAttributeRemoveControlName() {
        return managedAttributeRemoveControlName;
    }

    public void setManagedAttributeRemoveControlName(
            String managedAttributeRemoveControlName) {
        this.managedAttributeRemoveControlName = managedAttributeRemoveControlName;
    }

    public void setQuickLinkOptionsList(List<QuickLinkOptions> options) {
        _quickLinkOptionsList = options;
    }

    public List<QuickLinkOptions> getQuickLinkOptionsList() {
        return _quickLinkOptionsList;
    }

    public String getId() {
        return _ds.getId();
    }

    /**
     * Copies related properties to passed in DynamicScope object.
     * 
     * @param ds
     *            DynamicScope object
     */
    protected void copyToDynamicScope(SailPointContext context, DynamicScope ds) throws GeneralException {
        ds.setName(getName());
        ds.setDescription(getDescription());
        ds.setAllowAll(_ds.isAllowAll());
        ds.setSelector(_ds.getSelector());
        ds.setExclusions(_ds.getExclusions());
        ds.setInclusions(_ds.getInclusions());
        copyPopulationRequestControlData(ds);
        copyIdentityLists(context, ds);
        copyRules(context, ds);
    }
    
    protected void initPopulationRequestControlMap(Map<String, Object> data)  {
        if (!Util.isEmpty(data)) {
            String definitionType = Util.getString(this.populationRequestControlMap, KEY_POPULATION_DEFINITION_TYPE);
            if (Util.isNullOrEmpty(definitionType)) {
                this.populationRequestControlMap.put(KEY_POPULATION_DEFINITION_TYPE, Configuration.LCM_REQUEST_CONTROLS_MATCH_NONE);
                return;
            }
            
            // could copy all the entries in the data object, but that might pollute populationRequestControlMap
            // just copy over the stuff we use
            this.populationRequestControlMap.put(KEY_POPULATION_DEFINITION_TYPE, definitionType);
            copyKeyValue(Configuration.LCM_REQUEST_CONTROLS_MATCH_ANY_OR_ALL, data, this.populationRequestControlMap);
            copyKeyValue(Configuration.LCM_REQUEST_CONTROLS_ENABLE_ATTRIBUTE_CONTROL, data, this.populationRequestControlMap);
            copyKeyValue(Configuration.LCM_REQUEST_CONTROLS_ATTRIBUTE_FILTER_CONTROL, data, this.populationRequestControlMap);
            copyKeyValue(Configuration.LCM_REQUEST_CONTROLS_ENABLE_SUBORDINATE_CONTROL, data, this.populationRequestControlMap);
            copyKeyValue(Configuration.LCM_REQUEST_CONTROLS_SUBORDINATE_CHOICE, data, this.populationRequestControlMap);
            copyKeyValue(Configuration.LCM_REQUEST_CONTROLS_MAX_HIERARCHY_DEPTH, data, this.populationRequestControlMap);
            copyKeyValue(Configuration.LCM_REQUEST_CONTROLS_ENABLE_CUSTOM_CONTROL, data, this.populationRequestControlMap);
            copyKeyValue(Configuration.LCM_REQUEST_CONTROLS_CUSTOM_CONTROL, data, this.populationRequestControlMap);
            
            copyKeyValue(Configuration.LCM_REQUEST_CONTROLS_DISABLE_SCOPING, data, this.populationRequestControlMap);
        }
    }
    
    private void copyKeyValue(String key, Map<String, Object> from, Map<String, Object> to) {
        to.put(key, from.get(key));
    }
    
    protected void initPopulationRequestControlMap(DynamicScope ds)  {
        
        if (ds != null) {
            boolean isMatchNone = false;
            PopulationRequestAuthority popAuthority = ds.getPopulationRequestAuthority();
            if((popAuthority != null) && (popAuthority.getMatchConfig() == null)) {
                if(!popAuthority.isAllowAll()) {
                    isMatchNone = true;
                }
            }
            
            if ((popAuthority == null) || (isMatchNone)) {
                this.populationRequestControlMap.put(KEY_POPULATION_DEFINITION_TYPE, Configuration.LCM_REQUEST_CONTROLS_MATCH_NONE);
                return;
            }

            String definitionType = (popAuthority.isAllowAll()) ? Configuration.LCM_REQUEST_CONTROLS_ALLOW_ALL :
                Configuration.LCM_REQUEST_CONTROLS_MATCH_ANY_OR_ALL;
            this.populationRequestControlMap.put(KEY_POPULATION_DEFINITION_TYPE, definitionType);
            
            MatchConfig matchConfig = popAuthority.getMatchConfig();
            if (matchConfig != null) {
                if (matchConfig.isMatchAll()) {
                    this.populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_MATCH_ANY_OR_ALL, Configuration.LCM_REQUEST_CONTROLS_MATCH_ALL);
                } else {
                    this.populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_MATCH_ANY_OR_ALL, Configuration.LCM_REQUEST_CONTROLS_MATCH_ANY);
                }

                if (matchConfig.isEnableAttributeControl()) {
                    this.populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_ENABLE_ATTRIBUTE_CONTROL, Boolean.TRUE);
                    String jsonData = JsonHelper.toJson(matchConfig.getIdentityAttributeFilterControl());
                    this.populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_ATTRIBUTE_FILTER_CONTROL, jsonData);
                }
                if (matchConfig.isEnableSubordinateControl()) {
                    this.populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_ENABLE_SUBORDINATE_CONTROL, Boolean.TRUE);
                    this.populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_SUBORDINATE_CHOICE, matchConfig.getSubordinateOption());
                    this.populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_MAX_HIERARCHY_DEPTH, matchConfig.getMaxHierarchyDepth());
                }
                if (matchConfig.isEnableCustomControl()) {
                    this.populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_ENABLE_CUSTOM_CONTROL, Boolean.TRUE);
                    this.populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_CUSTOM_CONTROL, matchConfig.getCustomControl());
                }
            }
            
            if (popAuthority.isIgnoreScoping()) {
                this.populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_DISABLE_SCOPING, Boolean.TRUE);
            }
        }
    }
    
    /**
     * Copies data from the internal representation to the argument DynamicScope
     * @param ds copy from the DynamicScopeDTO to the DynamicScope
     */
    protected void copyPopulationRequestControlData(DynamicScope ds) throws GeneralException {
            
        if (ds != null) {
            PopulationRequestAuthority popRequest = ds.getPopulationRequestAuthority();
            if (popRequest == null) {
                popRequest = new PopulationRequestAuthority();
                ds.setPopulationRequestAuthority(popRequest);
            }
            
            boolean isAllowAll = Configuration.LCM_REQUEST_CONTROLS_ALLOW_ALL.equals(
                    Util.get(this.populationRequestControlMap, KEY_POPULATION_DEFINITION_TYPE));
            boolean isMatchNone = Configuration.LCM_REQUEST_CONTROLS_MATCH_NONE.equals(
                    Util.get(this.populationRequestControlMap, KEY_POPULATION_DEFINITION_TYPE));
            popRequest.setAllowAll(isAllowAll);
            if (isAllowAll || isMatchNone) {
                // clear the match config so we don't have lingering artifacts that exist
                popRequest.setMatchConfig(null);
            } else {
                MatchConfig matchConfig = popRequest.getMatchConfig();
                if (matchConfig == null) {
                    matchConfig = new MatchConfig();
                    popRequest.setMatchConfig(matchConfig);
                }
                
                boolean isEnableAttributeControl = Util.getBoolean(this.populationRequestControlMap, Configuration.LCM_REQUEST_CONTROLS_ENABLE_ATTRIBUTE_CONTROL);
                matchConfig.setEnableAttributeControl(isEnableAttributeControl);
                if (isEnableAttributeControl) {
                    String attributeControlFilter = Util.getString(this.populationRequestControlMap, Configuration.LCM_REQUEST_CONTROLS_ATTRIBUTE_FILTER_CONTROL);
                    if (attributeControlFilter != null && attributeControlFilter.trim().length() != 0 && !attributeControlFilter.equals("{}")) {
                        IdentityAttributeFilterControl attributeFilterControl = JsonHelper.fromJson(IdentityAttributeFilterControl.class, attributeControlFilter);
                        matchConfig.setIdentityAttributeFilterControl(attributeFilterControl);
                    }
                } else {
                    matchConfig.setIdentityAttributeFilterControl(null);
                }
                
                boolean isEnableSubordinateControl = Util.getBoolean(this.populationRequestControlMap, Configuration.LCM_REQUEST_CONTROLS_ENABLE_SUBORDINATE_CONTROL);
                matchConfig.setEnableSubordinateControl(isEnableSubordinateControl);
                if (isEnableSubordinateControl) {
                    matchConfig.setSubordinateOption(Util.getString(this.populationRequestControlMap, Configuration.LCM_REQUEST_CONTROLS_SUBORDINATE_CHOICE));
                    matchConfig.setMaxHierarchyDepth(Util.getInt(this.populationRequestControlMap, Configuration.LCM_REQUEST_CONTROLS_MAX_HIERARCHY_DEPTH));
                } else {
                    matchConfig.setSubordinateOption(null);
                    matchConfig.setMaxHierarchyDepth(0);
                }
                
                boolean isEnableCustomControl = Util.getBoolean(this.populationRequestControlMap, Configuration.LCM_REQUEST_CONTROLS_ENABLE_CUSTOM_CONTROL);
                matchConfig.setEnableCustomControl(isEnableCustomControl);
                if (isEnableCustomControl) {
                    matchConfig.setCustomControl(Util.getString(this.populationRequestControlMap, Configuration.LCM_REQUEST_CONTROLS_CUSTOM_CONTROL));
                } else {
                    matchConfig.setCustomControl(null);
                }
                
                boolean isMatchAll = Configuration.LCM_REQUEST_CONTROLS_MATCH_ALL.equals(Util.get(this.populationRequestControlMap, Configuration.LCM_REQUEST_CONTROLS_MATCH_ANY_OR_ALL));
                matchConfig.setMatchAll(isMatchAll);
            }

            popRequest.setIgnoreScoping(Util.getBoolean(this.populationRequestControlMap, Configuration.LCM_REQUEST_CONTROLS_DISABLE_SCOPING));

        }
    }
    
    protected void initIdentityLists(DynamicScope ds) {
        List<Identity> iList = ds.getInclusions();
        for (Identity i : Util.safeIterable(iList)) {
            this.inclusions.add(i.getName());
        }
        iList = ds.getExclusions();
        for (Identity i : Util.safeIterable(iList)) {
            this.exclusions.add(i.getName());
        }
    }
    
    protected void initIdentityLists(SailPointContext context, Map<String, Object> data) throws GeneralException {
        List<Identity> exculsionList = (List<Identity>) deserializeSailPointObjects(
                context, (List<Map>) data.get(KEY_EXCLUSIONS), Identity.class);
        copyIdentityNames(exculsionList, this.exclusions);

        List<Identity> inculsionList = (List<Identity>) deserializeSailPointObjects(
                context, (List<Map>) data.get(KEY_INCLUSIONS), Identity.class);
        copyIdentityNames(inculsionList, this.inclusions);
    }
    
    private void copyIdentityNames(List<Identity> idList, List<String> nameList) {
        for (Identity id : Util.safeIterable(idList)) {
            nameList.add(id.getName());
        }
    }
    
    protected void copyIdentityLists(SailPointContext context, DynamicScope ds) throws GeneralException {
        List<Identity> identityList = resolveIdentityList(context, this.exclusions);
        ds.setExclusions(identityList);
        
        identityList = resolveIdentityList(context, this.inclusions);
        ds.setInclusions(identityList);
    }
    
    private List<Identity> resolveIdentityList(SailPointContext context, List<String> idList) throws GeneralException {
        List<Identity> retList = new ArrayList<Identity>();
        
        for (String idName : Util.safeIterable(idList)) {
            Identity id = context.getObjectById(Identity.class, idName);
            if (id != null) {
                retList.add(id);
            } else {
                log.error("getObjectById() returned null for Identity id:" + idName);
            }
        }
        
        return retList;
    }
    
    protected void initRules(DynamicScope ds) {
        Rule rule = ds.getRoleRequestControl();
        if (rule != null) {
            setRoleRequestControlId(rule.getId());
            setRoleRequestControlName(rule.getName());
        }

        rule = ds.getApplicationRequestControl();
        if (rule != null) {
            setApplicationRequestControlId(rule.getId());
            setApplicationRequestControlName(rule.getName());
        }

        rule = ds.getManagedAttributeRequestControl();
        if (rule != null) {
            setManagedAttributeRequestControlId(rule.getId());
            setManagedAttributeRequestControlName(rule.getName());
        }

        rule = ds.getRoleRemoveControl();
        if (rule != null) {
            setRoleRemoveControlId(rule.getId());
            setRoleRemoveControlName(rule.getName());
        }

        rule = ds.getApplicationRemoveControl();
        if (rule != null) {
            setApplicationRemoveControlId(rule.getId());
            setApplicationRemoveControlName(rule.getName());
        }

        rule = ds.getManagedAttributeRemoveControl();
        if (rule != null) {
            setManagedAttributeRemoveControlId(rule.getId());
            setManagedAttributeRemoveControlName(rule.getName());
        }
    }
    
    protected void copyRequestRules(SailPointContext context, DynamicScope ds) throws GeneralException {
        String id = getRoleRequestControlId();
        if (Util.isNotNullOrEmpty(id)) {
            ds.setRoleRequestControl(context.getObjectById(Rule.class, id));
        }
        else {
            ds.setRoleRequestControl(null);
        }
        id = getApplicationRequestControlId();
        if (Util.isNotNullOrEmpty(id)) {
            ds.setApplicationRequestControl(context.getObjectById(Rule.class, id));
        }
        else {
            ds.setApplicationRequestControl(null);
        }
        id = getManagedAttributeRequestControlId();
        if (Util.isNotNullOrEmpty(id)) {
            ds.setManagedAttributeRequestControl(context.getObjectById(Rule.class, id));
        }
        else {
            ds.setManagedAttributeRequestControl(null);
        }
    }

    protected void copyRemoveRules(SailPointContext context, DynamicScope ds) throws GeneralException {
        String id = getRoleRemoveControlId();
        if (Util.isNotNullOrEmpty(id)) {
            ds.setRoleRemoveControl(context.getObjectById(Rule.class, id));
        }
        else {
            ds.setRoleRemoveControl(null);
        }
        id = getApplicationRemoveControlId();
        if (Util.isNotNullOrEmpty(id)) {
            ds.setApplicationRemoveControl(context.getObjectById(Rule.class, id));
        }
        else {
            ds.setApplicationRemoveControl(null);
        }
        id = getManagedAttributeRemoveControlId();
        if (Util.isNotNullOrEmpty(id)) {
            ds.setManagedAttributeRemoveControl(context.getObjectById(Rule.class, id));
        }
        else {
            ds.setManagedAttributeRemoveControl(null);
        }
    }

    protected void copyRules(SailPointContext context, DynamicScope ds) throws GeneralException {
        copyRequestRules(context, ds);
        copyRemoveRules(context, ds);
    }

    /**
     * Generates the Map representation of the DTO.
     * 
     * @return Map data
     * @throws GeneralException
     */
    public Map<String,Object> toMap() throws GeneralException {
        Map<String,Object> map = new HashMap<String,Object>();

        addToMapIfNotNull(map, KEY_ID, _ds.getId());
        addToMapIfNotNull(map, KEY_NAME, getName());
        addToMapIfNotNull(map, KEY_DESCRIPTION, getDescription());

        addToMapIfNotNull(map, KEY_ALLOW_ALL, _ds.isAllowAll());
        addToMapIfNotNull(map, KEY_SELECTOR, _ds.getSelector());
        addToMapIfNotNull(map, KEY_INCLUSIONS, _ds.getInclusions());
        addToMapIfNotNull(map, KEY_EXCLUSIONS, _ds.getExclusions());

        addToMapIfNotNull(map, KEY_POPULATION_REQUEST_AUTHORITY,
                _ds.getPopulationRequestAuthority());

        addToMapIfNotNull(map, KEY_APPLICATION_REQUEST_CONTROL,
                _ds.getApplicationRequestControl());
        addToMapIfNotNull(map, KEY_MANAGED_ATTRIBUTE_REQUEST_CONTROL,
                _ds.getManagedAttributeRequestControl());
        addToMapIfNotNull(map, KEY_ROLE_REQUEST_CONTROL,
                _ds.getRoleRequestControl());

        addToMapIfNotNull(map, KEY_APPLICATION_REMOVE_CONTROL,
                _ds.getApplicationRemoveControl());
        addToMapIfNotNull(map, KEY_MANAGED_ATTRIBUTE_REMOVE_CONTROL,
                _ds.getManagedAttributeRemoveControl());
        addToMapIfNotNull(map, KEY_ROLE_REMOVE_CONTROL,
                _ds.getRoleRemoveControl());

        addToMapIfNotNull(map, KEY_QUICK_LINK_OPTIONS_LIST,
                _quickLinkOptionsList);

        try {
            return serializeObject(map);
        } catch (Exception e) {
            throw new GeneralException(e);
        }
    }

    protected void addToMapIfNotNull(Map<String,Object> map, String key,
                                     Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void applyProperties(SailPointContext context, DynamicScope ds, Map<String,Object> data)
        throws GeneralException {
        ds.setId((String) data.get(KEY_ID));
        ds.setName((String) data.get(KEY_NAME));
        setName((String) data.get(KEY_NAME));
        setDescription((String) data.get(KEY_DESCRIPTION));
        ds.setDescription((String) data.get(KEY_DESCRIPTION));
        
        ds.setAllowAll(Util.getBoolean(data, KEY_ALLOW_ALL));

        IdentitySelector selector = deserializeIdentitySelector((Map) data.get(KEY_SELECTOR));
        ds.setSelector(selector);

        initIdentityLists(context, data);
        initPopulationRequestControlMap(data);

        // TODO these should copy from the map too 
        ds.setApplicationRequestControl((Rule) deserializeSailPointObject(
                (Map) data.get(KEY_APPLICATION_REQUEST_CONTROL), Rule.class));
        ds.setManagedAttributeRequestControl((Rule) deserializeSailPointObject(
                (Map) data.get(KEY_MANAGED_ATTRIBUTE_REQUEST_CONTROL),
                Rule.class));
        ds.setRoleRequestControl((Rule) deserializeSailPointObject(
                (Map) data.get(KEY_ROLE_REQUEST_CONTROL), Rule.class));
        ds.setApplicationRemoveControl((Rule) deserializeSailPointObject(
                (Map) data.get(KEY_APPLICATION_REMOVE_CONTROL), Rule.class));
        ds.setManagedAttributeRemoveControl((Rule) deserializeSailPointObject(
                (Map) data.get(KEY_MANAGED_ATTRIBUTE_REMOVE_CONTROL),
                Rule.class));
        ds.setRoleRemoveControl((Rule) deserializeSailPointObject(
                (Map) data.get(KEY_ROLE_REMOVE_CONTROL), Rule.class));
    }

    //*********************************************************
    // Helper methods for serialization and deserialization
    //*********************************************************

    @SuppressWarnings("unchecked")
    protected Map<String,Object> serializeObject(Object object)
        throws Exception {

        if (object == null) return null;

        ObjectMapper objectMapper = JsonHelper.getObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(Identity.class, new SailPointObjectSerializer());
        simpleModule.addSerializer(Rule.class, new SailPointObjectSerializer());
        simpleModule.addSerializer(Application.class, new SailPointObjectSerializer());
        simpleModule.addSerializer(GroupDefinition.class, new SailPointObjectSerializer());
        simpleModule.addSerializer(QuickLinkOptions.class, new SailPointObjectSerializer());
        objectMapper.registerModule(simpleModule);

        String json = objectMapper.writeValueAsString(object);
        return (Map<String,Object>) JsonUtil.parse(json);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<QuickLinkOptions> deserializeQuickLinkOptionsList(List<Map> list, DynamicScope ds, SailPointContext context) 
            throws GeneralException 
    {
        if (Util.isEmpty(list)) return null;

        List<QuickLinkOptions> qloList = new ArrayList<QuickLinkOptions>();
        for (Map map : Util.safeIterable(list)) {
            String quickLinkId = (String) map.get(KEY_QUICK_LINK_ID);
            if (quickLinkId != null) {
                QuickLink ql = context.getObjectById(QuickLink.class,
                        quickLinkId);
                if (ql != null) {
                    QuickLinkOptions qlo = new QuickLinkOptions();
                    qlo.setQuickLink(ql);
                    qlo.setDynamicScope(ds);
                    qlo.setAllowBulk(Util.getBoolean(map, KEY_ALLOW_BULK));
                    qlo.setAllowOther(Util.getBoolean(map, KEY_ALLOW_OTHER));
                    qlo.setAllowSelf(Util.getBoolean(map, KEY_ALLOW_SELF));
                    qlo.setOptions(new Attributes((Map) map.get(KEY_OPTIONS)));

                    qloList.add(qlo);
                }
            }
        }
        return qloList;
    }

    @SuppressWarnings("rawtypes")
    protected IdentitySelector deserializeIdentitySelector(Map data) throws GeneralException {

        if (data == null)
            return null;

        String json = JsonHelper.toJson(data);

        // We want to use JSON deserialization to fetch real SailPointObjects here, so need to register
        // deserializers for each type that might be part of any property of IdentitySelector.
        ObjectMapper objectMapper = JsonHelper.getObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addDeserializer(Identity.class, new SailPointObjectDeserializer<>(Identity.class));
        simpleModule.addDeserializer(Rule.class, new SailPointObjectDeserializer<>(Rule.class));
        simpleModule.addDeserializer(Application.class, new SailPointObjectDeserializer<>(Application.class));
        simpleModule.addDeserializer(GroupDefinition.class, new SailPointObjectDeserializer<>(GroupDefinition.class));
        simpleModule.addDeserializer(TargetSource.class, new SailPointObjectDeserializer<>(TargetSource.class));
        // Also need custom deserializer for Filter to determine leaf vs composite.
        simpleModule.addDeserializer(Filter.class, new FilterDeserializer());
        objectMapper.registerModule(simpleModule);

        try {
            return objectMapper.readValue(json, IdentitySelector.class);
        } catch (Exception ex) {
            throw new GeneralException(ex);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Object deserializeSailPointObject(Map map, Class clazz) throws GeneralException {
        if (map == null) return null;

        String id = (String) map.get(KEY_ID);
        return _context.getObjectById(clazz, id);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected List deserializeSailPointObjects(SailPointContext context, List<Map> list, Class clazz)
        throws GeneralException {
        if (list == null) return null;

        List result = new ArrayList();
        for (Map map : list) {
            String id = (String) map.get(KEY_ID);
            Object object = context.getObjectById(clazz, id);
            if (object != null) {
                result.add(object);
            }
        }
        return result;
    }

    //***************************************************************
    // Helper Classes for JSON serialization and deserialization
    //***************************************************************

    /**
     * Transforms SailPointObject into a Map with id and other related values.
     * 
     * @author danny.feng
     *
     */

    public static final class SailPointObjectSerializer extends JsonSerializer<SailPointObject> {

        @Override
        public void serialize(SailPointObject sp, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(KEY_ID, sp.getId());
            if (sp.hasName()) {
                jsonGenerator.writeStringField(KEY_NAME, sp.getName());
            }
            if (sp instanceof Identity) {
                jsonGenerator.writeStringField(KEY_DISPLAYABLE_NAME, ((Identity) sp).getDisplayableName());
            } else if (sp instanceof QuickLinkOptions) {
                QuickLinkOptions qlo = (QuickLinkOptions) sp;
                jsonGenerator.writeStringField(KEY_QUICK_LINK_ID, qlo.getQuickLink().getId());
                jsonGenerator.writeBooleanField(KEY_ALLOW_SELF, qlo.isAllowSelf());
                jsonGenerator.writeBooleanField(KEY_ALLOW_OTHER, qlo.isAllowOther());
                jsonGenerator.writeBooleanField(KEY_ALLOW_BULK, qlo.isAllowBulk());
                jsonGenerator.writeObjectField("options", qlo.getOptions());
            }
            jsonGenerator.writeEndObject();
        }
    }

    private void applyDefaultMaxHierarchyDepth() {
        if (populationRequestControlMap == null) {
            populationRequestControlMap = new HashMap<String, Object>();
        }
        int maxDepth = Util.getInt(populationRequestControlMap, Configuration.LCM_REQUEST_CONTROLS_MAX_HIERARCHY_DEPTH);
        if (maxDepth == 0) {
            populationRequestControlMap.put(Configuration.LCM_REQUEST_CONTROLS_MAX_HIERARCHY_DEPTH, 5);
        }
    }

    /**
     * Custom JSON deserializer that uses the ID in the object to fetch the actual SailPointObject as the value
     * @param <T> Class of SailPointObject
     */
    public final class SailPointObjectDeserializer<T extends SailPointObject> extends JsonDeserializer<T> {

        private Class<T> clazz;

        public SailPointObjectDeserializer(Class<T> clazz) {
            super();
            this.clazz = clazz;
        }

        @Override
        public T deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
            ObjectNode root = mapper.readTree(jsonParser);

            String id = null;
            if (root.has(KEY_ID)) {
                id = root.get(KEY_ID).asText();
            }

            if (Util.isNotNullOrEmpty(id)) {
                try {
                    return _context.getObjectById(this.clazz, id);
                } catch (GeneralException ge) {
                    return null;
                }
            }

            return null;
        }
    }

    /**
     * Custom JSON deserializer to determine if the type is LeafFilter or CompositeFilter based on the presence of children
     */
    public final class FilterDeserializer extends JsonDeserializer<Filter> {

        @Override
        public Filter deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
            ObjectNode root = mapper.readTree(jsonParser);

            Class<? extends Filter> filterClass = LeafFilter.class;
            if (root.has(KEY_CHILDREN)) {
                filterClass = CompositeFilter.class;
            }

            return mapper.treeToValue(root, filterClass);
        }
    }

}
