package sailpoint.web.roles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.certification.BulkCertificationHelper;
import sailpoint.web.util.NavigationHistory.Page;

public class RoleMetricsScheduleCertificationBean extends BaseBean implements Page {
    private static final Log log = LogFactory.getLog(RoleMetricsScheduleCertificationBean.class);
    private String roleId;
    private String metric;
    private String selectedIds;
    private List<String> selectedNames;
    private boolean excludeSelected;
    
    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getSelectedIds() {
        return selectedIds;
    }

    public void setSelectedIds(String selectedIds) {
        this.selectedIds = selectedIds;
    }

    public List<String> getSelectedNames() {
        return selectedNames;
    }

    public void setSelectedNames(List<String> selectedNames) {
        this.selectedNames = selectedNames;
    }

    public boolean isExcludeSelected() {
        return excludeSelected;
    }

    public void setExcludeSelected(boolean excludeSelected) {
        this.excludeSelected = excludeSelected;
    }

    /**
     * Schedules a bulk certification for the specified identities
     * @return JSF-based navigation String
     * @throws GeneralException
     */
    public String scheduleCertificationAction() throws GeneralException {
        // Grab identities to certify. This returns a map with two lists:
        // "ids" key is the list of identity IDs
        // "names" key is the list of identity names
        // Both lists contain the same identities, but separated for the benefit of BulkCertificationHelper.
        Map<String, List<String>> identitiesToCertify = getIdentitiesToCertify();
        BulkCertificationHelper certHelper = new BulkCertificationHelper(getSessionScope(), getContext(), this);
        certHelper.setSelectedIdentities(identitiesToCertify.get("ids"));
        certHelper.setSelectedIdentityNames(identitiesToCertify.get("names"));
        certHelper.setCertifyAll(false); // We've already incorporated this logic so there's no need to confuse things further
        return certHelper.scheduleBulkCertificationAction(null, getLoggedInUser());
    }
    
    public Object calculatePageState() {
        // The role modeler already handles its own state
        return null;
    }

    public String getNavigationString() {
        return "viewModeler";
    }

    public String getPageName() {
        return "Role Management";
    }

    public void restorePageState(Object state) {
        // The role modeler already handles its own state
    }

    /*
     * Determine which identities we want to sent to the certification scheduling page
     */
    private Map<String, List<String>> getIdentitiesToCertify() {
        List<String> identityIdsToCertify;
        List<String> identityNamesToCertify;
        
        if (isExcludeSelected()) {
            identityIdsToCertify = new ArrayList<>();
            identityNamesToCertify = new ArrayList<>();
            
            Set<String> identitiesToExclude;
            if (selectedIds == null || selectedIds.trim().length() == 0) {
                identitiesToExclude = new HashSet<String>();
            } else {
                identitiesToExclude = Util.csvToSet(selectedIds, true);
            }
            
            RoleMetricsQueryRegistry queryRegistry = new RoleMetricsQueryRegistry(getContext());
            Filter identityQueryFilter = (Filter) queryRegistry.get(metric, roleId);
            QueryOptions qo = new QueryOptions(identityQueryFilter);
            try {
                Iterator<Object[]> ids = getContext().search(Identity.class, qo, "id, name");
                if (ids != null) {
                    while (ids.hasNext()) {
                        Object[] result = ids.next();
                        String  id = (String) result[0],
                                name = (String) result[1];
                        if (!identitiesToExclude.contains(id)) {
                            identityIdsToCertify.add(id);
                            identityNamesToCertify.add(name);
                        }
                    }
                }
            } catch (GeneralException e) {
                log.error("Failed to retrieve the identities to certify from role metrics", e);
            }
        } else {
            identityIdsToCertify = Util.csvToList(selectedIds, true);
            identityNamesToCertify = selectedNames;
        }

        Map<String, List<String>> identities = new HashMap<>();
        identities.put("ids", identityIdsToCertify);
        identities.put("names", identityNamesToCertify);
        
        return identities;
    }
}
