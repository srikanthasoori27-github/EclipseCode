package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.certification.CertificationHelper.Input;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

/**
 * This is a helper class which will contain
 * common code between various certification
 * generation classes. 
 * 
 * Right now Partitioned Cert Generation and 
 * non-partitioned Cert Generation are using it.
 *
 */
public class ManagerCertificationHelper extends CertificationHelper {

    private static final Log log = LogFactory.getLog(ManagerCertificationHelper.class);
    
    
    // is this a global manager cert?
    private boolean global;
    // the apps to be certified
    List<String> includedAppIds;
    // the manger to be certified
    private String managerName;
    // should generate for the hierarchy?
    private boolean generateSubordinateCerts;
    // should flatten the hierarchy
    private boolean flatten;
    
    public ManagerCertificationHelper(Input input) throws GeneralException {
        
        super(input);

        init();
    }

    // initializes before creating partitions
    private void init() throws GeneralException {

        if (input.getCertificationDefinition() == null) {
            throw new IllegalStateException("input not set properly.");
        }
        
        this.global = input.getCertificationDefinition().isGlobal();
        
        if (!global){
            managerName =  input.getCertificationDefinition().getCertifierName();
            if (managerName == null) {
                throw new GeneralException("Non-global manager certification did not specify the certifier");
            }
        }

        this.includedAppIds = ObjectUtil.convertToIds(input.getContext(), 
                Application.class, 
                input.getCertificationDefinition().getIncludedApplicationIds());
        
        generateSubordinateCerts = input.getCertificationDefinition().isSubordinateCertificationEnabled();
        flatten = input.getCertificationDefinition().isFlattenManagerCertificationHierarchy();
        
        if (log.isInfoEnabled()) {
            log.info("generateSubordinateCerts: " + generateSubordinateCerts);
            log.info("flatten: " + flatten);
        }
    }
    
    // fetch the top level managers
    public Iterator<String> fetchFirstLevelManagersIterator() throws GeneralException {
        
        Iterator<String> managerNamesIterator;
        
        if (this.global) {
            Identity defOwner = input.getCertificationDefinition().getOwner();
            try {
                // When we do global mgr certs, we need to make sure that the mgr
                // applied to the cert are within the cert def owner's scope, 
                // so impersonate
                input.getContext().impersonate(defOwner);
                input.getContext().setScopeResults(true);
            
                QueryOptions qo = new QueryOptions();
                qo.addOwnerScope(defOwner);
                
                /* 
                 * If subordinate certifications are disabled:
                 * 1. If flatten hierarchy is not enabled get all the managers
                 * 2. If flatten hierarchy is enabled only get the top level managers
                 *    because we are flattening everything to them and generating certs
                 *    only for them.
                 * Otherwise:  
                 * Get only the top-level ones because the other managers 
                 * will have been included as subordinates.
                 */
                String selectProperty = "name";
                if (this.generateSubordinateCerts || this.flatten) {
                    addTopLevelManagerFilters(qo);
                }
                else {
                    // Optimization - if we're filtering by application, select only
                    // managers that have subordinates on at least one of the requested
                    // applications.  This prevents us from trying to create lots of
                    // empty certs when there are a large number of managers.
                    if ((null != this.includedAppIds) &&
                        !this.includedAppIds.isEmpty()) {
    
                        List<Filter> appConds = new ArrayList<Filter>();
                        for (String app : this.includedAppIds) {
                            appConds.add(Filter.eq("links.application.id", app));
                        }
                        qo.add(Filter.or(appConds));
                        qo.add(Filter.eq("manager.managerStatus", true));
                        qo.setDistinct(true);
                        
                        selectProperty = "manager.name";
                    }
                    else {
                        qo.add(Filter.eq("managerStatus", true));
                    }
                }
    
                managerNamesIterator = fetchManagerNamesIterator(qo, selectProperty);
            } finally {
                // If anything bad should happen or we're done, make sure we reveal our true identity before going on.
                input.getContext().impersonate(null);
                input.getContext().setScopeResults(false);
            }
        }
        else {
            managerNamesIterator = Arrays.asList(managerName).iterator();
        }

        return managerNamesIterator;
    }

    /**
     * This will add the necessary Filters for getting just top-level managers.
     * A top-level manager is described as a manager that has no other manager
     * above him/her.  A manager with a null manager or a self-managed manager
     * would fit this.
     * 
     * @param qo  The QueryOptions to which the filters are added
     */
    public static void addTopLevelManagerFilters(QueryOptions qo) {

        qo.add(Filter.or(Filter.isnull("manager"), Filter.join("manager", "Identity.id")));
        qo.add(Filter.eq("managerStatus", true));
    }
    
    /**
     * Fetches just the manager names (no other properties)
     * for the managers matching the queryoptions.
     */
    private Iterator<String> fetchManagerNamesIterator(QueryOptions qo, String selectProperty) throws GeneralException {
        
        final Iterator<Object[]> iterator = input.getContext().search(Identity.class, qo, Arrays.asList(selectProperty));

        class ManagerNamesIterator implements Iterator<String> {

            @Override
            public boolean hasNext() {

                return iterator.hasNext();
            }

            @Override
            public String next() {

                Object[] row = iterator.next();
                return (String) row[0];
            }

            @Override
            public void remove() {

                throw new UnsupportedOperationException();
            }
        }
        
        return new ManagerNamesIterator();
    }
}
