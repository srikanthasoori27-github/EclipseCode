/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.AuditConfig;
import sailpoint.object.AuditEvent;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.object.AuditConfig.AuditAction;
import sailpoint.search.SelectItemComparator;
import sailpoint.tools.GeneralException;
import sailpoint.web.SearchProxy;
import sailpoint.web.messages.MessageKeys;

/**
 * @author peter.holcomb
 *
 */
public class AuditSearchBean extends SearchBean<AuditEvent> {

    private static final Log log = LogFactory.getLog(IdentitySearchBean.class);
    
    /**
     * The audit config allows us to map audit actions to their display names
     */
    private static final AuditConfig config = AuditConfig.getAuditConfig();
    
    private static final String AUDIT_DATE = "audit.created";
    private static final String AUDIT_ACTION = "audit.action";
    private static final String AUDIT_SOURCE = "source";
    private static final String AUDIT_TARGET = "audit.target";
    private static final String TARGET = "target";
    private static final String AUDIT_CLIENT_HOST = "audit.clientHost";
    private static final String AUDIT_SERVER_HOST = "audit.serverHost";

    private static final String GRID_STATE = "auditSearchGridState";
    
    private List<String> selectedAuditFields;
    private List<SearchProxy> audits;
    private List<SelectItem> auditFields;
    private List<String> defaultFieldList;

    public AuditSearchBean () {
        super();
        super.setScope(AuditEvent.class);
        restore();
    }  

    protected void restore() {
        setSearchType(SearchBean.ATT_SEARCH_TYPE_AUDIT);
        super.restore();
        if(getSearchItem()==null) {
            setSearchItem(new SearchItem());
            
            // add the default audit search fields
            selectedAuditFields = getDefaultFieldList();
        }
        else {
            selectedAuditFields = getSearchItem().getAuditFields();
        }
    }

    protected void save() throws GeneralException{
        if(getSearchItem() == null) 
            setSearchItem(new SearchItem());
        getSearchItem().setType(SearchItem.Type.Audit);
        setFields();
        super.save();
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    ////////////////////////////////////////////////////////////////////////////


    @Override
    public List<String> getDefaultFieldList() {
        if(defaultFieldList == null) {
            defaultFieldList = new ArrayList<String>(4);
            defaultFieldList.add(AUDIT_DATE);
            defaultFieldList.add(AUDIT_ACTION);
            defaultFieldList.add(AUDIT_SOURCE);
            defaultFieldList.add(AUDIT_TARGET);
        }
        return defaultFieldList;
    }

    /**
     * The raw row data needs some massaging to present the way we want it to.
     * Use the AuditConfig to get the display names of the audit event actions,
     * and look up the display names of the sources and targets.  We have to 
     * assume that the sources and targets are Identities, but they might not be
     * (e.g. - system processes that triggered the audit event).  In any case,
     * if we can't find the friendly value we want, just use the data as-is.
     */
    public Object convertColumn(String name, Object value) {
        if (value == null)
            return value;
        
        if ((value instanceof String) && (((String)value).equals("")))
            return value;
        
        if (name.equals(AUDIT_ACTION)) {            
            AuditAction ae = config.getAuditAction((String)value);
            if (ae != null)
                return ae.getDisplayableName();
        } 
        
        if ((name.equals(AUDIT_SOURCE)) || (name.equals(AUDIT_TARGET)) || (name.equals(TARGET))) {
            Identity identity;
            try {
                identity = getContext().getObjectByName(Identity.class, (String)value);
                if (identity != null)
                    return  identity.getDisplayName();
            } catch (GeneralException e) {
                // why doesn't BaseListBean.convertColumn() throw exceptions?
                log.debug("Problem searching for identity: " + name);
            }
        }

        //TODO: Need to escape client/server host? -rap
        
        return value;
    }
    
    
    protected void setFields() {
    	super.setFields();
        getSearchItem().setAuditFields(selectedAuditFields);
    }

    /**
     * @return the auditFields
     */
    public List<SelectItem> getAuditFieldList() {

        auditFields = new ArrayList<SelectItem>();

        // As a step toward making this data driven we're assuming that
        // the input definitions also define the return fields.  This
        // needs to be more flexible for other classes.

        // what I'd like to do...
        //SearchConfig config = getSearchConfig();
        //SearchScope scope = config.getScope(SearchConfig.SCOPE_AUDIT);
        //List<Argument> fields = scope.getReturnFields();

        // crap, the input definitions have two fields for a before/after
        // comparison on the creation date, but there is only one return field
        auditFields.add(new SelectItem(AUDIT_DATE, getMessage(MessageKeys.SRCH_INPUT_DEF_AUDIT_DATE)));

        // this will cache _inputDefinitions, should do this in the constructor!
        getInputs();
        List<SearchInputDefinition> definitions = getInputDefinitions();
        if (definitions != null) {
            for (SearchInputDefinition def : definitions) {
                // have to filter out the two "created" input definitions
                // we've got a constant over in SearchItem but we
                // don't use it in the definition, why?
                if (SearchItem.Type.Audit.name().equals(def.getSearchType()) &&
                        !def.getPropertyName().equals("created")) {
                    auditFields.add(new SelectItem(def.getName(), getMessage(def.getDescription())));
                }
            }
        }

        // Sort the list based on localized labels
        Collections.sort(auditFields, new SelectItemComparator(getLocale()));

        return auditFields;
    }

    @Override
    public List<String> getSelectedColumns() {
        if(selectedColumns==null) {
            selectedColumns = new ArrayList<String>();
            if(selectedAuditFields!=null)
                selectedColumns.addAll(selectedAuditFields);
        }
        return selectedColumns;
    }

    @Override
    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_AUDIT;
    }
    
    @Override
    public String getGridStateName() {
        return GRID_STATE;
    }
    
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "created";
    }

    /**
     * List of allowable definition types that should be taken into
     * account when building filters Should be overridden.*/
    @Override
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = super.getAllowableDefinitionTypes();
        allowableTypes.add(ATT_SEARCH_TYPE_AUDIT);
        return allowableTypes;
    }

    /**
     * @return the selectedAuditFields
     */
    public List<String> getSelectedAuditFields() {
        return selectedAuditFields;
    }

    /**
     * @param selectedAuditFields the selectedAuditFields to set
     */
    @SuppressWarnings("unchecked")
    public void setSelectedAuditFields(List<String> selectedAuditFields) {
        SearchItem item = (SearchItem) getSessionScope().get(getSearchItemId());
        if (item == null)
            item = getSearchItem();
        item.setAuditFields(selectedAuditFields);
        this.selectedAuditFields = selectedAuditFields;
        getSessionScope().put(getSearchItemId(), item);
    }

    public String getDisplayHelpMsg() {
        return this.getDisplayHelpMsg(MessageKeys.AUDIT_LCASE);
    }
    
    public String getCriteriaHelpMsg() {
        return this.getCriteriaHelpMsg(MessageKeys.AUDITS_LCASE);
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = super.getQueryOptions();
        qo.setDirtyRead(true);
        return qo;
    }
}
