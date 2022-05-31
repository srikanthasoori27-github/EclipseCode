/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.group;

import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class GroupNavigationBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static Log log = LogFactory.getLog(GroupNavigationBean.class);

    /**
     *
     */
    private int _currentTab = 0;

    /**
     *
     * @return
     */
    public int getCurrentTab() {
        log.debug(_currentTab);
        return _currentTab;
    }

    /**
     *
     * @param tab
     */
    public void setCurrentTab(int tab) {
        log.debug(tab);
        _currentTab = tab;
    }

    /**
     *
     */
    public String toString() {
        return super.toString() + ": " + _currentTab;
    }

    /**
     *
     * @return
     */
    public String updateCurrentTab() {
            // Nothing to do here.  The real work is done in the setter above
            // which would have already been called.
        return "";
    }

}  // class GroupNavigationBean
