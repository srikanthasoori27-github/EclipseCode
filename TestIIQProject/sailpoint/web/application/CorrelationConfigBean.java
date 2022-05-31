/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.model.SelectItem;

import sailpoint.object.CorrelationConfig;
import sailpoint.object.DirectAssignment;
import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;

public class CorrelationConfigBean extends BaseBean {
    /**
     * Name of the session attribute where the share list is stored.
     */
    private static String ATT_DYNAMIC_CORRELATION = "dynamicCorrelation";
    private static String ATT_DIRECT_CORRELATION = "directCorrelation";
    private static String ATT_NEW_DIRECT = "currentDirect";
    private static String ATT_CONFIG_DIRTY = "dirtyCorrelationConfig";

    CorrelationConfig _config;

    List<FilterWrapperBean> _dynamicBeans;
    List<DirectCorrelationBean> _directBeans;

    private Map<String, Boolean> _selectedDynamic;
    private Map<String, Boolean> _selectedDirect;
    private Map<String, Boolean> _selectedConditions;

    private FilterWrapperBean _newDynamic;
    private DirectCorrelationBean _newDirect;

    // static list of operations to ignore the value check
    private static List<LogicalOperation> _noValueNeeded = null;
    
    private boolean _dirty;

    @SuppressWarnings("unchecked")
    public CorrelationConfigBean(){ 
        _selectedDynamic = new HashMap<String, Boolean>();
        _selectedDirect = new HashMap<String, Boolean>();
        _selectedConditions = new HashMap<String, Boolean>();
        _newDynamic = new FilterWrapperBean();
        _newDirect = (DirectCorrelationBean)getSessionScope().get(ATT_NEW_DIRECT);
        if ( _newDirect == null ) {
            _newDirect = new DirectCorrelationBean();
            getSessionScope().put(ATT_NEW_DIRECT, _newDirect);
        }
        _dynamicBeans = (List<FilterWrapperBean>)getSessionScope().get(ATT_DYNAMIC_CORRELATION);
        _directBeans = (List<DirectCorrelationBean>)getSessionScope().get(ATT_DIRECT_CORRELATION);
    }  

    public CorrelationConfigBean(CorrelationConfig config) { 
        this();
        _config = config;
        if (_config != null) {
            _config.load();
        }
    }
    @SuppressWarnings("unchecked")
    public void setSessionObjectsIntoConfig() {
        CorrelationConfig config = _config;
        if ( _config == null ) {
            config = new CorrelationConfig();
        }

        List<FilterWrapperBean> dynamics = (List<FilterWrapperBean>)getSessionScope().get(ATT_DYNAMIC_CORRELATION);
        if ( Util.size(dynamics) > 0 ) {
            List<Filter> list = new ArrayList<Filter>();
            for ( FilterWrapperBean dynamic : dynamics) {
                list.add(dynamic.getObject());
            }
            config.setAttributeAssignments(list);
        } else {
            config.setAttributeAssignments(null);
        }

        List<DirectCorrelationBean> directs = (List<DirectCorrelationBean>)getSessionScope().get(ATT_DIRECT_CORRELATION);
        if ( Util.size(directs) > 0 ) {
            List<DirectAssignment> list = new ArrayList<DirectAssignment>();
            for ( DirectCorrelationBean direct : directs ) {
                list.add(direct.getObject());
            }
            config.setDirectAssignments(list);
        }
    }
    
    /*
     * Just get the object without potentially wiping out 
     */
    public CorrelationConfig getObject() {
        CorrelationConfig config = _config;
        if ( _config == null ) {
            config = new CorrelationConfig();
        }
        return _config;
    }
 
    ///////////////////////////////////////////////////////////////////////////
    //
    // Direct Correlation 
    //
    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public List<DirectCorrelationBean> getDirect() {
        if ( _directBeans == null ) {
            List<DirectCorrelationBean> sessionDirectBean = (List<DirectCorrelationBean>)getSessionScope().get(ATT_DIRECT_CORRELATION);
            List<DirectCorrelationBean> objectBeans = new ArrayList<DirectCorrelationBean>();
            if ( _config != null ) {
                List<DirectAssignment> direct= _config.getDirectAssignments();
                if ( Util.size(direct) > 0 ) {
                    for ( DirectAssignment dir : direct) {
                        objectBeans.add(new DirectCorrelationBean(dir));
                    }
                } 
            }
            
            //Something done at this point has caused our crappy cache to have a discrepancy.  Null always loses
            //since setting the app's correlation config to nothing doesn't hit this. Session wins on difference if dirty
            if (!Util.nullSafeEq(sessionDirectBean, objectBeans)) {
                if (Util.isEmpty(objectBeans) && sessionDirectBean != null) {
                    _directBeans = sessionDirectBean;
                } else if (sessionDirectBean == null){
                    _directBeans = objectBeans;
                } else if (isDirty()){
                    _directBeans = sessionDirectBean;
                } else {
                    _directBeans = objectBeans;
                }
            }
            getSessionScope().put(ATT_DIRECT_CORRELATION, _directBeans);
        }
        return _directBeans;
    }

    public void setDirect(List<DirectCorrelationBean> direct) {
        _directBeans = direct;
    }

    public Map<String, Boolean> getSelectedDirect() {
        return _selectedDirect;
    }
    
    public void setSelectedDirect(Map<String, Boolean> selectedDirect) {
        _selectedDirect = selectedDirect;
    }

    @SuppressWarnings("unchecked")
    public String removeDirect() {

        if ( _directBeans == null ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_NO_CONDITIONAL),null);
            return "";
        }

        Set<String> ids = null;
        if ( _selectedDirect != null ) {
            ids = _selectedDirect.keySet();
        }

        if ( Util.size(ids) == 0 ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_NO_SELECTED_CONDITIONAL),null);
            return "";
        }

        Set<String> directToRemove = getSelectedIds(_selectedDirect, ids);

        Iterator<DirectCorrelationBean> i = _directBeans.iterator();
        while (i.hasNext()) {
            DirectCorrelationBean currentBean = i.next();
            if ( directToRemove.contains(currentBean.getId()) ) {
                i.remove();
                setDirty(true);
            }
        }
        getSessionScope().put(ATT_DIRECT_CORRELATION, _directBeans);
        return null;
    }

    @SuppressWarnings("unchecked")
    public String moveDirectUp() throws GeneralException {
        if ( _directBeans != null ) {
            moveDirectItem(_directBeans, true);
            setDirty(true);
            getSessionScope().put(ATT_DIRECT_CORRELATION, _directBeans);
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public String moveDirectDown() throws GeneralException {
        if ( _directBeans != null ) {
            moveDirectItem(_directBeans, false);
            setDirty(true);
            getSessionScope().put(ATT_DIRECT_CORRELATION, _directBeans);
        }
        return "";
    }

    //TODO : consolidate this and the filter move method
    private void moveDirectItem(List<DirectCorrelationBean> beanList, boolean up ) {
        if ( beanList!= null ) {
            String idToMove = getItemToMove();
            int current = -1;
            int totalSize = beanList.size();
            DirectCorrelationBean toMove = null;
            for ( int i=0; i< totalSize; i++) {
                DirectCorrelationBean dynamic = beanList.get(i);
                if ( dynamic.getId().compareTo(idToMove) == 0 ) {
                    current = i;
                    toMove = dynamic;
                    break;
                } 
            }
            if ( ( current != -1 ) &&  ( toMove != null ) ) {
                int newPosition = 0;
                beanList.remove(current);
                if (up) {
                    newPosition = current -1;
                    if ( newPosition < 0 ) newPosition = 0;
                } else  {
                    newPosition = current +1;
                    if ( newPosition >= totalSize ) newPosition = current;
                }

                beanList.add(newPosition, toMove);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public String addDirect() {
        String result = "";
        if ( validateDirect(_newDirect) ) {
            if ( _directBeans == null ) {
                _directBeans = new ArrayList<DirectCorrelationBean>();
            } 

            ArrayList<DirectCorrelationBean> newList = new ArrayList<DirectCorrelationBean>();
            boolean found = false;
            for ( DirectCorrelationBean bean : _directBeans ) {
                // if exists replace...
                if ( bean.getId().compareTo(_newDirect.getId()) == 0 ) {
                    newList.add(_newDirect);
                    found = true;
                } else
                    newList.add(bean);
                
            }
            _directBeans = newList;
            if ( ! found ) {
                _directBeans.add(_newDirect);
            }
            _newDirect = new DirectCorrelationBean();
            getSessionScope().put(ATT_NEW_DIRECT, _newDirect);
            setDirty(true);
        }
        getSessionScope().put(ATT_DIRECT_CORRELATION, _directBeans);
        return result;
    }

    @SuppressWarnings("unchecked")
    public String editDirect() {
        if ( _directBeans != null ) {
            String idToMove = getItemToMove();
            for ( int i=0; i< _directBeans.size(); i++) {
                DirectCorrelationBean dynamic = _directBeans.get(i);
                if ( dynamic.getId().compareTo(idToMove) == 0 ) {
                    _newDirect = dynamic;
                    getSessionScope().put(ATT_NEW_DIRECT, _newDirect);
                    setDirty(true);
                    break;
                } 
            }
        }
        return "";
    }

    private boolean validateDirect(DirectCorrelationBean bean) {
        if ( bean != null ) {
            Identity id = bean.getObject().getIdentity();
            if ( id == null )  {
                addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_NO_IDENTITY),null);
                return false;
            }
            List<Filter> filters = bean.getObject().getFilters();
            if ( filters == null ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_NO_FILTER),null);
                return false;
            }
        }
        return true;
    }

    public DirectCorrelationBean getNewDirect() {
        return _newDirect;
    }

    public void setNewDirect(DirectCorrelationBean direct) {
        _newDirect = direct;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Dynamic Correlation 
    //
    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public List<FilterWrapperBean> getDynamic() {

        if ( _dynamicBeans == null ) {
            List<FilterWrapperBean> sessionDynamicFilter = (List<FilterWrapperBean>)getSessionScope().get(ATT_DYNAMIC_CORRELATION);
            List<FilterWrapperBean> objectFilters = null;
            _dynamicBeans = new ArrayList<FilterWrapperBean>();
            if ( _config != null ) {
                objectFilters = new ArrayList<FilterWrapperBean>();
                List<Filter> filters = _config.getAttributeAssignments();
                if ( Util.size(filters) > 0 ) {
                    for ( Filter filter : filters ) {
                        objectFilters.add(new FilterWrapperBean(filter));
                    }
                }
            }
            //Something done at this point has caused our crappy cache to have a discrepancy.  Null always loses
            //since setting the app's correlation config to nothing doesn't hit this. Session wins on difference if dirty
            if (!Util.nullSafeEq(sessionDynamicFilter, objectFilters)) {
                if (Util.isEmpty(objectFilters) && sessionDynamicFilter != null) {
                    _dynamicBeans = sessionDynamicFilter;
                } else if (sessionDynamicFilter == null){
                    _dynamicBeans = objectFilters;
                } else if (isDirty()){
                    _dynamicBeans = sessionDynamicFilter;
                } else {
                    _dynamicBeans = objectFilters;
                }
            }
            
            getSessionScope().put(ATT_DYNAMIC_CORRELATION, _dynamicBeans);
        }
        return _dynamicBeans;
    }

    public void setDynamic(List<FilterWrapperBean> dynamic) {
        _dynamicBeans = dynamic;
    }

    public Map<String, Boolean> getSelectedDynamic() {
        return _selectedDynamic;
    }
    
    public void setSelectedDynamic(Map<String, Boolean> selectedDynamic) {
        _selectedDynamic = selectedDynamic;
    }

    String _item;
    public String getItemToMove() {
        return _item;
    }

    public void setItemToMove(String item) {
        _item = item;
    }

    @SuppressWarnings("unchecked")
    public String moveDynamicUp() throws GeneralException {
        if ( _dynamicBeans != null ) {
            moveFilterItem(_dynamicBeans, true);
            setDirty(true);
            getSessionScope().put(ATT_DYNAMIC_CORRELATION, _dynamicBeans);
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public String moveDynamicDown() throws GeneralException {
        if ( _dynamicBeans != null ) {
            moveFilterItem(_dynamicBeans, false);
            setDirty(true);
            getSessionScope().put(ATT_DYNAMIC_CORRELATION, _dynamicBeans);
        }
        return "";
    }

    private void moveFilterItem(List<FilterWrapperBean> beanList, boolean up ) {
        if ( beanList!= null ) {
            String idToMove = getItemToMove();
            int current = -1;
            FilterWrapperBean toMove = null;
            int totalSize = beanList.size();
            for ( int i=0; i< totalSize; i++) {
                FilterWrapperBean dynamic = beanList.get(i);
                if ( dynamic.getId().compareTo(idToMove) == 0 ) {
                    current = i;
                    toMove = dynamic;
                    break;
                } 
            }
            if ( ( current != -1 ) &&  ( toMove != null ) ) {
                int newPosition = 0;
                beanList.remove(current);
                if (up) {
                    newPosition = current - 1;
                    if ( newPosition < 0 ) newPosition = 0;
                } else  {
                    newPosition = current + 1;
                    if ( newPosition >= totalSize ) newPosition = current;
                }
                beanList.add(newPosition, toMove);
            }
        }
    }

    // 
    // Dynamic Conditions
    // 

    @SuppressWarnings("unchecked")
    public String addCondition() {
        String result = "";
        if ( _newDirect != null ) {
            FilterWrapperBean newFilter = _newDirect.getNewFilter();
            List<FilterWrapperBean> filters = _newDirect.getConditions();
            if ( validateFilter(newFilter.getObject()) ) {
                if ( checkForExisting(filters, newFilter) ) {
                    return "";
                }
                filters.add(newFilter);
                _newDirect.setNewFilter(new FilterWrapperBean());
                setDirty(true);
            } 
        } 
        return result;
    }

    @SuppressWarnings("unchecked")
    public String removeCondition() {
        List<FilterWrapperBean> filters = _newDirect.getConditions();
        if ( filters == null ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_NO_CONDITIONS),null);
            return "";
        }

        Set<String> ids = null;
        if ( _selectedConditions != null ) {
                ids = _selectedConditions.keySet();
        }

        if ( Util.size(ids) == 0 ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_ATLEAST_ONE_CONDITION),null);
                return "";
        }

        Set<String> conditionsToRemove = getSelectedIds(_selectedConditions, ids);
        Iterator<FilterWrapperBean> i =filters.iterator();
        while (i.hasNext()) {
            FilterWrapperBean currentBean = i.next();
            if ( conditionsToRemove.contains(currentBean.getId()) ) {
                i.remove();
                setDirty(true);
            }
        }
        return "";
    }

    public String moveConditionUp() throws GeneralException {
        List<FilterWrapperBean> filters = _newDirect.getConditions();
        if ( filters != null ) {
            moveFilterItem(filters, true);
            setDirty(true);
        }
        return "";
    }

    public String moveConditionDown() throws GeneralException {
        List<FilterWrapperBean> filters = _newDirect.getConditions();
        if ( filters != null ) {
            moveFilterItem(filters, false);
            setDirty(true);
        }
        return "";
    }

    public Map<String, Boolean> getSelectedConditions() {
        return _selectedConditions;
    }
    
    public void setSelectedConditions(Map<String, Boolean> selectedConditions) {
        _selectedConditions = selectedConditions;
    }

    @SuppressWarnings("unchecked")
    public String removeDynamic() {

        if ( _dynamicBeans == null ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_NO_ATTRIBUTEBASED),null);
            return "";
        }
        Set<String> ids = null;
        if ( _selectedDynamic != null ) {
            ids = _selectedDynamic.keySet();
        }

        if ( Util.size(ids) == 0 ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_NO_SELECTED_ATTRIBUTEBASED),null);
            return "";
        }

        Set<String> dynamicToRemove = getSelectedIds(_selectedDynamic, ids);
        Iterator<FilterWrapperBean> i = _dynamicBeans.iterator();
        while (i.hasNext()) {
            FilterWrapperBean currentBean = i.next();
            if (dynamicToRemove.contains(currentBean.getId())) {
                i.remove();
                setDirty(true);
            }
        }
        getSessionScope().put(ATT_DYNAMIC_CORRELATION, _dynamicBeans);
        return null;
    }

    @SuppressWarnings("unchecked")
    public String addDynamic() {
        String result = "";
        if ( validateFilter((LeafFilter)_newDynamic.getObject()) ) {
            if ( _dynamicBeans == null ) {
                _dynamicBeans = new ArrayList<FilterWrapperBean>();
            } 
            if ( checkForExisting(_dynamicBeans, _newDynamic) ) {
                return "";
            }
            _dynamicBeans.add(_newDynamic);
            _newDynamic = new FilterWrapperBean();
            setDirty(true);
        }
        getSessionScope().put(ATT_DYNAMIC_CORRELATION, _dynamicBeans);
        return result;
    }

    public FilterWrapperBean getNewDynamic() {
        return _newDynamic;
    }

    public void setNewDynamic(FilterWrapperBean newDynamic) {
        _newDynamic = newDynamic;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Util
    //
    ///////////////////////////////////////////////////////////////////////////

    private Set<String> getSelectedIds(Map<String,Boolean> selected, Set<String> ids) {
        Set<String> toRemove = new HashSet<String>();
        for (String id : ids ) {
            if ( selected.get(id) ) {
                toRemove.add(id);
            }
        }
        return toRemove;
    }

    private List<LogicalOperation> getNoValueNeededList() {
        if ( _noValueNeeded == null ) {
            _noValueNeeded = new ArrayList<LogicalOperation>();
            _noValueNeeded.add(LogicalOperation.NOTNULL);
            _noValueNeeded.add(LogicalOperation.ISNULL);
            _noValueNeeded.add(LogicalOperation.ISEMPTY);
        }
        return _noValueNeeded;

    }
    protected boolean validateFilter(LeafFilter filter) {
        if ( filter == null ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_NO_FILTER),null);
            return false;
        }
        if ( Util.getString(filter.getProperty()) == null ) {
            Message msg = new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_NO_PROPERTY);
            addMessage(msg,null);
            return false;
        }

        LogicalOperation op = filter.getOperation();
        List<LogicalOperation> noValueNeeded = getNoValueNeededList();
        if ( !noValueNeeded.contains(op) ) {
            if ( Util.getString((String)filter.getValue()) == null ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_NO_VALUE),null);
                return false;
            }
        }
        return true;
    }

    protected boolean checkForExisting(List<FilterWrapperBean> filterList, FilterWrapperBean newFilter) {
        if ( ( filterList != null ) && ( newFilter != null ) ) {
            for ( FilterWrapperBean filter : filterList ) {
               if ( equals((LeafFilter)newFilter.getObject(), (LeafFilter)filter.getObject()) ) {
                   addMessage(new Message(Message.Type.Error, MessageKeys.CORWIZ_ERR_CONDITION_EXISTS),null);
                   return true;
               }
            }
        }
        return false;
    }

    protected boolean equals(LeafFilter filter1, LeafFilter filter2) {
        String prop = filter1.getProperty();
        if ( prop != null ) {
            if ( prop.compareTo(filter2.getProperty()) != 0 ) 
                return false;
        }
        String val = (String)filter1.getValue();
        if ( val != null ) {
            if ( val.compareTo((String)filter2.getValue()) != 0 ) 
                return false;
        }
        String operator = filter1.getOperation().getStringRepresentation();
        if ( operator != null ) {
            if ( operator.compareTo(filter2.getOperation().getStringRepresentation()) != 0 ) 
                return false;
        }
        return true;
    }

    /** 
     * Wipe away any breadcrumbs we left behind
     */
    public void reset() {
        //Don't blow our session stuff away if we've
        //reloaded the bean after an edit.
        if (!isDirty()) {
            getSessionScope().remove(ATT_DYNAMIC_CORRELATION);
            getSessionScope().remove(ATT_DIRECT_CORRELATION);
            getSessionScope().remove(ATT_NEW_DIRECT);
            _dynamicBeans = null;
            _directBeans = null;
        }
    }

    /** 
     * Return a list of SelectItems one for each defined searchable 
     * Identity attribute
     */
    public List<SelectItem> getSearchableAttributes() throws GeneralException {
        List<SelectItem> names = new ArrayList<SelectItem>();
        ObjectConfig config = Identity.getObjectConfig(); 
        if ( config != null ) {
            // Create a new list so we don't modify the cached object config.
            List<ObjectAttribute> attrs = new ArrayList<ObjectAttribute>();
            List<ObjectAttribute> multis = config.getMultiAttributeList();
            List<ObjectAttribute> searchables = config.getSearchableAttributes();
            if ( Util.size(searchables) > 0 ) {
                attrs.addAll(searchables); 
            }
            if ( Util.size(multis) > 0 ) {
                attrs.addAll(multis); 
            }
            Map<String,String> attrMap = new HashMap<String,String>();
            if ( Util.size(attrs) > 0 ) {
                for ( ObjectAttribute attr : attrs ) {
                    String name = attr.getName();
                    String displayName = attr.getDisplayName();
                    if ( "manager".compareTo(name) != 0 ) {
                        Message msg = new Message(MessageKeys.MSG_PLAIN_TEXT,displayName);
                        attrMap.put(msg.getLocalizedMessage(getLocale(), getUserTimeZone()),name);
                    } 
                }

                // add this as a default value so we can match on an identities
                // name. This isn't included in the default searchable list...
                attrMap.put("Name", "name");
                List<String> keys = new ArrayList<String>(attrMap.keySet());
                Collections.sort(keys);
                for ( String key : keys ) {
                    String name = attrMap.get(key);
                    names.add(new SelectItem(name,key));
                }
            }
        }
        return names;
    }

    /** 
     */
    public List<SelectItem> getCorrelationConfigObjects() throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.setScopeResults(true);
        List<String> atts = new ArrayList<String>();
        atts.add("name");

        List<SelectItem> items = new ArrayList<SelectItem>();
        items.add(new SelectItem("", "Select an Existing Configuration"));

        Iterator<Object[]> rows = getContext().search(CorrelationConfig.class, ops, atts);
        if (rows != null) {
            while (rows.hasNext()) {
                Object[] row = rows.next();
                String name = (String)(row[0]);
                if ( name != null ) 
                    items.add(new SelectItem(name, name));
            }
        }
        return items;
    }

    /**
     * Get json represenation of the existing listen of correlation conf objects.
     * 
     * @return
     * @throws GeneralException
     */
    public String getCorrelationConfigObjectsJson() throws GeneralException {

            QueryOptions ops = new QueryOptions();
            ops.setScopeResults(true);
            List<String> atts = new ArrayList<String>();
            atts.add("id");
            atts.add("name");
            List<Map<String, Object>> objects = new ArrayList<Map<String, Object>>();
            Iterator<Object[]> rows = getContext().search(CorrelationConfig.class, ops, atts);
            if (rows != null) {
                while (rows.hasNext()) {
                    Object[] row = rows.next();
                    String id = (String)(row[0]);
                    String name = (String)(row[1]);

                    Map obj = new HashMap();
                    obj.put("id", id);
                    obj.put("name", name);

                    objects.add(obj);
                }
            }

            return JsonHelper.toJson(objects);
        }

    public List<SelectItem> getOperators() {
        List<SelectItem> operators = new ArrayList<SelectItem>();
	operators.add(new SelectItem("EW",new Message(MessageKeys.CORWIZ_CONDITION_ENDSWITH).getLocalizedMessage(getLocale(), null)));
	operators.add(new SelectItem("EQ",new Message(MessageKeys.CORWIZ_CONDITION_EQUAL).getLocalizedMessage(getLocale(), null)));
	operators.add(new SelectItem("ISEMPTY",new Message(MessageKeys.CORWIZ_CONDITION_EMPTY).getLocalizedMessage(getLocale(), null)));
	operators.add(new SelectItem("ISNULL",new Message(MessageKeys.CORWIZ_CONDITION_NULL).getLocalizedMessage(getLocale(), null)));
	operators.add(new SelectItem("LIKE",new Message(MessageKeys.CORWIZ_CONDITION_LIKE).getLocalizedMessage(getLocale(), null)));
	operators.add(new SelectItem("NE",new Message(MessageKeys.CORWIZ_CONDITION_NOTEQUAL).getLocalizedMessage(getLocale(), null)));
	operators.add(new SelectItem("NOTNULL",new Message(MessageKeys.CORWIZ_CONDITION_NOTNULL).getLocalizedMessage(getLocale(), null)));
	operators.add(new SelectItem("SW",new Message(MessageKeys.CORWIZ_CONDITION_STARTSWITH).getLocalizedMessage(getLocale(), null)));
        return operators;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // FilterWrapperBean
    //
    ///////////////////////////////////////////////////////////////////////////

    /**  
     * Simple wrapper around a Filter object to provide unique 
     * id and default empty values.
     */
    public class FilterWrapperBean {

        String _id;
        LeafFilter _object;


        public FilterWrapperBean() {
            _id = Util.uuid();
            _object = new LeafFilter(LogicalOperation.EQ, "", "");
        }

        public FilterWrapperBean(Filter filter) {
            this();
            _object = (LeafFilter)filter;
        }

        public LeafFilter getObject() {
            return _object;
        }

        public void setObject(LeafFilter filter) {
            _object = filter;
        }

        public String getId() {
            return _id;
        }

        public void setId(String id) {
            _id = id;
        }

        public String getOperatorString() {
            String op = "";
            LeafFilter obj = getObject();
            if ( obj != null ) {
                LogicalOperation operation = obj.getOperation();
                if ( operation != null )  {
                    op = operation.getDisplayName();
                    if ( operation.equals(LogicalOperation.LIKE) ) {
                        MatchMode mode = obj.getMatchMode();
                        if ( mode != null ) {
                            if ( mode.equals(MatchMode.START) ) {
                               op = "Start With";                    
                            } else
                            if ( mode.equals(MatchMode.END) ) {
                               op = "Ends With";                    
                            }
                        }
                    }
                }
            } 
            return op;
        }

        public String getOperator() {
            String op = "";
            LeafFilter obj = getObject();
            if ( obj != null ) {
                LogicalOperation operation = obj.getOperation();
                op = operation.name();
                if ( operation.equals(LogicalOperation.LIKE) ) {
                   MatchMode mode = obj.getMatchMode();
                   if ( mode.equals(MatchMode.END) ) {
                      op = "EW";
                   } else
                   if ( mode.equals(MatchMode.START) ) {
                      op = "SW";
                   }
                }
            } 
            return op;
        }

        public void setOperator(String operation) {
            if ( operation != null ) {
                LogicalOperation op = LogicalOperation.EQ;
                LeafFilter obj = getObject();
                if ( obj != null ) {
                    obj.setMatchMode(null);
                    if ( "SW".equals(operation) ) {
                        op = LogicalOperation.LIKE;
                        obj.setMatchMode(MatchMode.START);
                    } else
                    if ( "EW".equals(operation) ) {
                        op = LogicalOperation.LIKE;
		        obj.setMatchMode(MatchMode.END);
                    } else
                    if ( "LIKE".equals(operation) ) {
                        op = LogicalOperation.LIKE;
                        obj.setMatchMode(MatchMode.ANYWHERE);
                    } else
                    if ( "EQ".equals(operation) ) {
                        op = LogicalOperation.EQ;
                    } else
                    if ( "NE".equals(operation) ) {
                        op = LogicalOperation.NE;
                    } else
                    if ( "ISEMPTY".equals(operation) ) {
                        op = LogicalOperation.ISEMPTY;
                    } else 
                    if ( "ISNULL".equals(operation) ) {
                        op = LogicalOperation.ISNULL;
                    } else 
                    if ( "NOTNULL".equals(operation) ) {
                        op = LogicalOperation.NOTNULL;
                    } 
                    obj.setOperation(op);
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // DirectCorrelationBean
    //
    ///////////////////////////////////////////////////////////////////////////

    public class DirectCorrelationBean {

        String _id;
        DirectAssignment _object;
        List<FilterWrapperBean> _filters;
        FilterWrapperBean _newFilter;

        @SuppressWarnings("unchecked")
        public DirectCorrelationBean() {
            _id = Util.uuid();
            _object = new DirectAssignment();
            _newFilter = new FilterWrapperBean();
        }

        public DirectCorrelationBean(DirectAssignment dir) {
            this();
            _object = dir;
            if ( _object != null ) {
                _filters = new ArrayList<FilterWrapperBean>();
                List<Filter> filters = _object.getFilters();
                if ( filters != null ) {
                    for ( Filter f : filters ) {
                        if ( f != null ) {
                            _filters.add(new FilterWrapperBean(f));
                        }
                    } 
                } 
            }
        }

        public DirectAssignment getObject() {
            if ( _object != null ) {
                if ( _filters != null ) {
                    List<Filter> filters = new ArrayList<Filter>();
                    for ( FilterWrapperBean bean : _filters ) {
                        Filter f = bean.getObject();
                        filters.add(f);
                    }
                    _object.setFilters(filters);
                }
            }
            return _object;
        }

        public void setObject(DirectAssignment filter) {
            _object = filter;
        }

        public String getId() {
            return _id;
        }

        public void setId(String id) {
            _id = id;
        }

        public FilterWrapperBean getNewFilter() {
            return _newFilter;
        }

        public void setNewFilter(FilterWrapperBean f) {
            _newFilter = f;
        }

        public List<FilterWrapperBean> getConditions() {
             if ( _filters == null ) {
                 _filters = new ArrayList<FilterWrapperBean>();
             }
             return _filters;
        }

        public void setConditions(List<FilterWrapperBean> conditions) {
             _filters = conditions;
        }

        /**
         * @return String representaion of the object's filter list
         */
        public String getFilterExpression(){

            String expression = "";
            List<Filter> filters = _object.getFilters();
            if (filters != null){
                for(Filter filter : filters){
                    if (!"".equals(expression))
                        expression += " && ";
                    expression += filter.getExpression();
                }
            }

            return expression;
        }
   }

    public boolean isDirty() {
        _dirty = Util.nullSafeEq(getSessionScope().get(ATT_CONFIG_DIRTY), true);
        return _dirty;
    }

    public void setDirty(boolean dirty) {
        this._dirty = dirty;
        getSessionScope().put(ATT_CONFIG_DIRTY, _dirty);
    }
}
