/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.event.ActionEvent;

import sailpoint.object.TargetSource;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util.ListElementWrapper;
import sailpoint.web.util.NavigationHistory;


/**
 * JSF bean to list applications.
 */
public class TargetSourceListBean
    extends BaseListBean<TargetSource>
    implements NavigationHistory.Page {

    /**
     * A ListElementWrapper that returns TargetSourceWrappers instead of
     * TargetSources.
     */
    private static class TargetSourceListWrapper
        implements ListElementWrapper<TargetSource> {

        public TargetSource wrap(TargetSource element) {
            return new TargetSourceWrapper(element);
        }
    }

    /**
     * A decorator for an Application that adds a getHost() method.
     */
    public static class TargetSourceWrapper extends TargetSource {
        private static final long serialVersionUID = 1L;

        private TargetSource targetDS;

        public TargetSourceWrapper(TargetSource targetDS) {
            this.targetDS = targetDS;
        }

        public String getId() { return this.targetDS.getId(); }
        public String getName() { return this.targetDS.getName(); }

        public Date getModified() { return this.targetDS.getModified(); }
    }
    
    /**
     *
     */
    public TargetSourceListBean() {
        super();
        setScope(TargetSource.class);
    }  // ApplicationListBean()

    public List<TargetSource> getObjects() throws GeneralException {
        final List<TargetSource> retval = super.getObjects();
        return retval;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * action to transition to the edit screen without setting an id to edit
     *
     * @return "edit"
     */
    public String newApplication() {
        return "edit";
    }

    /**
     * "Delete" action handler for the application list page.
     *
     * We must make sure that this application is not referenced by any
     * business processes before allowing it to be deleted.
     *
     * @throws GeneralException
     */
    public void deleteObject(ActionEvent event) {
        if ( _selectedId == null ) 
            return;
        super.deleteObject(event);
    }  // deleteObject(ActionEvent)


    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseListBean overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "name";
    }

    /**
     * This list page needs special logic to compute the host, so we'll use a
     * decorator that adds this.
     */
    @Override
    public ListElementWrapper<TargetSource> getListElementWrapper() {
        return new TargetSourceListWrapper();
    }

    @Override
    public Map<String, String> getSortColumnMap() {
        HashMap<String, String> columnMap = new HashMap<String, String>();
        columnMap.put("s1", "name");
        columnMap.put("s3", "type");
        columnMap.put("s4", "modified");
        return columnMap;
    }

   
    public Map<String,String> getPassthroughApps()         
        throws GeneralException {

        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("supportsAuthenticate", true));
        return getSortedDisplayableNames(options);
    }
 
    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Target Source List";
    }

    public String getNavigationString() {
        return "targetSourceList";
    }

    public Object calculatePageState() {
        // No page state.
        return null;
    }

    public void restorePageState(Object state) {
        // No page state.
    }
}  // class ApplicationListBean
