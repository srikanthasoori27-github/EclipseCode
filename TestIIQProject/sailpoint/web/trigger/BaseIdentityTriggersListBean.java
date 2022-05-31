/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.trigger;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Terminator;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;


/**
 * Base bean for listing IdentityTriggers.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class BaseIdentityTriggersListBean
    extends BaseListBean<IdentityTrigger> {

    private static final Log LOG = LogFactory.getLog(BaseIdentityTriggersListBean.class);
    
    private static final String PARAM_TRIGGER_SEARCH = "triggerSearch";

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public BaseIdentityTriggersListBean() {
        super.setScope(IdentityTrigger.class);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ABSTRACT METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Add a filter to the given QueryOptions to restrict the IdentityTriggers
     * that are returned according to their handler.
     */
    protected abstract void addHandlerFilter(QueryOptions qo);

    /**
     * Return the message key to display when a trigger is deleted.
     */
    protected abstract String getDeletedMessageKey();


    ////////////////////////////////////////////////////////////////////////////
    //
    // BASE LIST BEAN OVERRIDES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get the query options for the list.
     */
    @Override
    public QueryOptions getQueryOptions() throws GeneralException {

        QueryOptions qo = super.getQueryOptions();

        // If we're sorting by the attribute name, strip out start, limit, and
        // ordering from the query options.  We'll need to sort and page in
        // memory.
        if (isAttributeNameSort()) {
            super.clearSortingAndPaging(qo);
        }
        
        // Filter by handler.
        addHandlerFilter(qo);
        
        // Grab the request parameter value for the Ext search field.
        String namePrefix = super.getRequestParameter(PARAM_TRIGGER_SEARCH);
        if (null != Util.getString(namePrefix)) {
            qo.add(Filter.ignoreCase(Filter.like("name", namePrefix, MatchMode.START)));
        }
        
        return qo;
    }

    /**
     * Override to sort and page in-memory when sorting by attribute name.
     */
    @Override
    public List<Map<String,Object>> getRows() throws GeneralException {

        List<Map<String,Object>> rows = super.getRows();

        // If we're sorting by attribute name, we need to manually sort and
        // page the results.
        if (isAttributeNameSort()) {
            rows = sortAndPage(rows);
        }

        return rows;
    }

    /**
     * Change the attributeName column to show the displayable name of the
     * ObjectAttribute.
     */
    @Override
    public Object convertColumn(String name, Object value) {
        
        if ("attributeName".equals(name)) {
            ObjectConfig oc = Identity.getObjectConfig();
            value = oc.getDisplayName((String) value, super.getLocale());
        } else if ("name".equals(name)) {
            value = WebUtil.escapeHTML(value.toString(), false);
        }
        
        return value;
    }

    /**
     * Return whether the list is being sorted by the "attributeName" column.
     */
    private boolean isAttributeNameSort() throws GeneralException {

        // To figure out the sort column, create a new QueryOptions and let the
        // super class plug in the info.  Can't use getQueryOptions() to do this
        // because we strip it out for attribute sort.
        QueryOptions qo = new QueryOptions();
        super.getSortOrdering(qo);
        
        List<Ordering> orderings = qo.getOrderings();
        if (null != orderings) {
            for (Ordering ordering : orderings) {
                if ("attributeName".equals(ordering.getColumn())) {
                    return true;
                }
            }
        }
        
        return false;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Delete the selected trigger.
     */
    public String delete() throws GeneralException {
        String selected = super.getSelectedId();
        if (null == selected)
            throw new GeneralException(MessageKeys.ERR_NO_ITEM_SELECTED);

        IdentityTrigger obj =
            (IdentityTrigger) getContext().getObjectById(IdentityTrigger.class, selected);
        if (obj != null) {
            LOG.info("Deleting identity trigger: " + obj);
            Terminator t = new Terminator(getContext());
            t.deleteObject(obj);
            getContext().commitTransaction();
            addMessage(new Message(Message.Type.Info, getDeletedMessageKey(),
                                   obj.getName()), null);
        }
        return null;
    }
}
