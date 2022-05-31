/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.util.List;

import sailpoint.web.FilterSelectBean;

public interface ProfileFilterEditor {
    public List<FilterSelectBean> getProfileFilters();
    public void setProfileFilters(final List<FilterSelectBean> profileFilters);
    public ProfileDTO getProfile();
}
