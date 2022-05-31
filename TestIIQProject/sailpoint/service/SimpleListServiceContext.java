/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;
import sailpoint.web.util.Sorter;

/**
 * Simple implementation of a BaseListServiceContext that bases all decisions
 * on the provided UserContext. This does not limit results in any way.
 */
public class SimpleListServiceContext implements BaseListServiceContext {

    private UserContext userContext;

    /**
     * Constructor.
     *
     * @param  userContext  The UserContext to delegate to.
     */
    public SimpleListServiceContext(UserContext userContext) {
        this.userContext = userContext;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // UserContext methods
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#getContext()
     */
    @Override
    public SailPointContext getContext() {
        return this.userContext.getContext();
    }

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#getLoggedInUser()
     */
    @Override
    public Identity getLoggedInUser() throws GeneralException {
        return this.userContext.getLoggedInUser();
    }

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#getLoggedInUserName()
     */
    @Override
    public String getLoggedInUserName() throws GeneralException {
        return this.userContext.getLoggedInUserName();
    }

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#getLoggedInUserCapabilities()
     */
    @Override
    public List<Capability> getLoggedInUserCapabilities() {
        return this.userContext.getLoggedInUserCapabilities();
    }

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#getLoggedInUserRights()
     */
    @Override
    public Collection<String> getLoggedInUserRights() {
        return this.userContext.getLoggedInUserRights();
    }

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#getLoggedInUserDynamicScopeNames()
     */
    @Override
    public List<String> getLoggedInUserDynamicScopeNames() throws GeneralException {
        return this.userContext.getLoggedInUserDynamicScopeNames();
    }

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#getLocale()
     */
    @Override
    public Locale getLocale() {
        return this.userContext.getLocale();
    }

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#getUserTimeZone()
     */
    @Override
    public TimeZone getUserTimeZone() {
        return this.userContext.getUserTimeZone();
    }
    
    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#isMobileLogin()
     */
    @Override
    public boolean isMobileLogin() {
        return this.userContext.isMobileLogin();
    }

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#isObjectInUserScope(sailpoint.object.SailPointObject)
     */
    @Override
    public boolean isObjectInUserScope(SailPointObject object) throws GeneralException {
        return this.userContext.isObjectInUserScope(object);
    }

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#isObjectInUserScope(java.lang.String, java.lang.Class)
     */
    @Override @SuppressWarnings("rawtypes")
    public boolean isObjectInUserScope(String id, Class clazz) throws GeneralException {
        return this.userContext.isObjectInUserScope(id, clazz);
    }

    /* (non-Javadoc)
     * @see sailpoint.web.UserContext#isScopingEnabled()
     */
    @Override
    public boolean isScopingEnabled() throws GeneralException {
        return this.userContext.isScopingEnabled();
    }



    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // BaseListServiceContext methods
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see sailpoint.service.BaseListServiceContext#getStart()
     */
    @Override
    public int getStart() {
        return 0;
    }

    /* (non-Javadoc)
     * @see sailpoint.service.BaseListServiceContext#getLimit()
     */
    @Override
    public int getLimit() {
        return 0;
    }

    /* (non-Javadoc)
     * @see sailpoint.service.BaseListServiceContext#getQuery()
     */
    @Override
    public String getQuery() {
        return null;
    }

    /* (non-Javadoc)
     * @see sailpoint.service.BaseListServiceContext#getSorters(java.util.List)
     */
    @Override
    public List<Sorter> getSorters(List<ColumnConfig> columnConfigs) {
        return  null;
    }

    @Override
    public String getGroupBy() {
        return null;
    }

    @Override
    public List<Filter> getFilters() {
        return null;
    }
}
