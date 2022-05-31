/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Backing bean for the policy list.
 * Author: Jeff, Peter
 */

package sailpoint.web.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.Localizer;
import sailpoint.api.SailPointContext;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.LocalizedAttribute;
import sailpoint.object.Policy;
import sailpoint.object.QueryOptions;
import sailpoint.object.Filter.MatchMode;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseListBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.WebUtil;

public class PolicyListBean extends BaseListBean<Policy> 
    implements NavigationHistory.Page {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * HttpSession attribute we use to convey the selected policy id
     * to PolicyBean.  
     */
    public static final String ATT_OBJECT_ID = "PolicyId";
    
    /**
     * HttpSession attribute we use to convey the policy template
     * type to PolicyBean when creating a new policy.
     */
    public static final String ATT_POLICY_TYPE = "PolicyType";

    /**
     * HttpSession attributes used to save the policy table filters.
     * jsl - we don't really need these, there are almost never
     * more than 4 or 5 policies.
     */
    public static final String POL_NAME_FILTER = "PolicyListNameFilter";
    public static final String POL_TYPE_FILTER = "PolicyListTypeFilter";
    
    private static final String GRID_STATE = "policyListGridState";
    private static final Log log = LogFactory.getLog(PolicyListBean.class);
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Since policy templates almost never change, we'll maintain
     * a static cache.
     */
    static private List<Policy> _policyTemplates;

    SelectItem[] _policyTypes;
    String _newPolicyType;
    List<ColumnConfig> columns;
    Localizer localizer;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public PolicyListBean() {
        super();
        setScope(Policy.class);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Helper Method
    //
    //////////////////////////////////////////////////////////////////////
    
    @SuppressWarnings("unchecked")
    private void save() {
        Map session = getSessionScope();
        session.put(ATT_POLICY_TYPE, _newPolicyType);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the list of policy templates.
     * We'll keep this in a static cache and provide a static
     * accessor so we can get to it from PolicyBean.
     */
    static public List<Policy> getPolicyTemplates(SailPointContext con)
        throws GeneralException {

        if (_policyTemplates == null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("template", new Boolean(true)));
            _policyTemplates = con.getObjects(Policy.class, ops);
            // fully load them so we can clone them 
            if (_policyTemplates != null) {
                for (Policy p : _policyTemplates)
                    p.load();
            }
        }
        return _policyTemplates;
    }

	@Override
	public List<Policy> getObjects() throws GeneralException {
		
        // support subtyping like we do for Rules?

		return super.getObjects();
	}
    
    /** Retrieves any passed in filters from the request **/
    public void getQueryOptionsFromRequest(QueryOptions ops) throws GeneralException
    {
        if(getRequestParameter("policyType")!=null && !((String)getRequestParameter("policyType")).equals(""))
            ops.add(Filter.eq("type", getRequestParameter("policyType")));
        
        if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals(""))
            ops.add(Filter.ignoreCase(Filter.like("name", getRequestParameter("name"), MatchMode.START)));
        
    }
    
    @Override 
    public QueryOptions getQueryOptions() throws GeneralException
    {
        QueryOptions qo =  super.getQueryOptions();
        getQueryOptionsFromRequest(qo);
        // always want this
        qo.add(Filter.eq("template", new Boolean(false)));

        return qo;
    }
    
    @Override
    public Map<String, String> getSortColumnMap() {
        HashMap<String, String> columnMap = new HashMap<String, String>();
        columnMap.put("name", "name");
        columnMap.put("type", "type");
        columnMap.put("description", "description");
        columnMap.put("state", "state");
        return columnMap;
    }
    
    @Override
    public List<ColumnConfig> getColumns() throws GeneralException{
        if(columns==null)
            try {
                loadColumnConfig();
            } catch (GeneralException ge) {
                log.info("Unable to load columns: " + ge.getMessage());
            }
        return columns;
    }

    public void loadColumnConfig() throws GeneralException {
        this.columns = super.getUIConfig().getPoliciesTableColumns();
    }
    
    private Localizer getLocalizer() {
        if (this.localizer == null) {
            this.localizer = new Localizer(getContext());
        }
        return this.localizer;
    }
    
    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
            throws GeneralException {
        Map<String, Object> converted = super.convertRow(row, cols);

        String id = (String)converted.get("id");
        if (id != null) {
            converted.put("description", WebUtil.safeHTML(getLocalizer().getLocalizedValue(id, Localizer.ATTR_DESCRIPTION, getLocale())));
        }
        return converted;
    }
    
    

    public Map<String,Boolean> getSelections() {
        return new HashMap<String,Boolean>();
    }

    public String getNewPolicyType() {
        return _newPolicyType;
    }

    public void setNewPolicyType(String s) {
        if (s!=null) {
            _newPolicyType = s;
            save();
        }
        else {
            _newPolicyType = null;
            save();
        }
    }


    /**
     * The list of types for new policies.  This is derived
     * from the list of policy templates.  We require that there
     * can't be more than one template with the same type name.
     */
    public SelectItem[] getPolicyTypes() throws GeneralException {

        if (_policyTypes == null) {
            List<SelectItem> items = new ArrayList<SelectItem>();

            List<Policy> templates = getPolicyTemplates(getContext());
            if (templates != null) {
                for (Policy p : templates) {
                    String type = p.getType();
                    String desc = type;
                    String key = p.getTypeKey();
                    if (key != null)
                        desc = getMessage(key);
                    items.add(new SelectItem(type, desc));
                }
            }
            _policyTypes = items.toArray(new SelectItem[items.size()]);
        }

        return _policyTypes;
    }

    /**
     * jsl - we have a List and an array, do we really
     * need both?
     */
    public List<SelectItem> getPolicyTypeChoices()
        throws GeneralException {

        List<SelectItem> list = new ArrayList<SelectItem>();
        String selectMessage = this.getMessage("select_policy_type");
        list.add(new SelectItem("", selectMessage));
        SelectItem[] items = getPolicyTypes();
        if (items != null) {
            for (int i = 0 ; i < items.length ; i++)
                list.add(items[i]);
        }
        return list;
    }

    /**
     * Collapse empty filter strings to null so they
     * don't get used in the QueryOptions.
     */
    private String trim(String src) {
        if (src != null) {
            src = src.trim();
            if (src.length() == 0)
                src = null;
        }
        return src;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Use a custom select action handler rather than the one
     * down in BaseListBean so we can redirect to a different page
     * for each policy type.
     */
    public String editAction() throws GeneralException {

        String next = null;
        String selected = getSelectedId();

        if (selected == null || selected.length() == 0) {
            // can get here by pressing return in the filter box without
            // clicking on the Search button, which I guess makes it    
            // look like a click in the live grid
            next = null;
        }
        else {
            // I'm tired of fighting with faces redirect, just stick
            // the damn id on the session and go.
            getSessionScope().put(ATT_OBJECT_ID, selected);
            next = "editPolicy";
        }

        // make sure any lingering state is cleared 
        getSessionScope().put(BaseObjectBean.FORCE_LOAD, "true");

        if (this instanceof NavigationHistory.Page) {
            NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        }

        return next;
    }

    public String newAction() {

        // make sure any lingering state is cleared 
        getSessionScope().remove(ATT_OBJECT_ID);
        getSessionScope().put(BaseObjectBean.FORCE_LOAD, "true");

        return "newPolicy";
    }

    public String deleteAction() throws GeneralException {

        String selected = getSelectedId();
        if (selected != null && selected.length() > 0) {
            Policy p = getContext().getObjectById(Policy.class, selected);
            if (p != null) {
                String appId = p.getId();
                getContext().removeObject(p);
                // Should we commit here and skip deleting the attributes if there is an exception?
                //getContext().commitTransaction();
                
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("targetId", appId));
                List<LocalizedAttribute> las = getContext().getObjects(LocalizedAttribute.class, ops);
                if (las != null) {
                    for(LocalizedAttribute la : las) {
                        getContext().removeObject(la);
                    }
                }
                
                getContext().commitTransaction();
            }
        }

        return null;
    }
    
    public String reset()
    {
        setObjects(null);
        save();
        return null;
    }
    
    public String refresh() throws GeneralException {

        // stay on this page
        return null;
    }
    
    public String getGridStateName() { 
		return GRID_STATE; 
	}

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Policy List";
    }

    public String getNavigationString() {
        return "policyList";
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
}
