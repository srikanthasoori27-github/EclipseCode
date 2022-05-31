/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Analyze options for including roles in a certification.
 *
 * Author: Jeff
 *
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;

import sailpoint.object.Bundle;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;

import sailpoint.tools.GeneralException;

public class RoleSelector {

    SailPointContext _context;
    CertificationDefinition _definition;

    Map<String,String> _inclusions;
    boolean _includeAll;
    
    public RoleSelector(SailPointContext con, CertificationDefinition def) {
        _context = con;
        _definition = def;
    }

    public RoleSelector(SailPointContext con, Filter filter) throws GeneralException {
        _context = con;

        buildInclusions(filter);
    }

    public boolean isIncluded(Bundle role)
        throws GeneralException {

        boolean include = true;
        
        // defer this post constructor since it could be time consuming
        if (_inclusions == null) {
            buildInclusions(_definition.getRoleFilter());
        }
        
        if (!_includeAll) {
            include = (_inclusions.get(role.getId()) != null);
        }

        return include;
    }

    /**
     * Calculate inclusions from the definition.
     * If there are no inclusion filters set the includeAll flag.
     * @param filter 
     */
    private void buildInclusions(Filter f)
        throws GeneralException {

        if (_inclusions == null) {
            _inclusions = new HashMap<String,String>();
            if (f == null) {
                _includeAll = true;
            }
            else {
                QueryOptions ops = new QueryOptions();
                ops.add(f);
                List<String> props = new ArrayList<String>();
                props.add("id");
                Iterator<Object[]> result = _context.search(Bundle.class, ops, props);
                while (result.hasNext()) {
                    Object[] row = result.next();
                    String id = (String)(row[0]);
                    _inclusions.put(id, id);
                }
            }
        }
    }
    
}
