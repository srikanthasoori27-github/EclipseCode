/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.SelectItemByLabelComparator;

/**
 *
 */
public class BundleListBean extends BaseListBean<Bundle> {

	private static final Log log = LogFactory.getLog(BundleListBean.class);
	boolean uiMaxBundles;
	private List<String> types;
    /**
     *
     */
    public BundleListBean() {
        super();
        setScope(Bundle.class);
    }  // BundleListBean()
    
    @Override
    public String getDefaultSortColumn() {
        return "name";
    }
    
    @Override
    public Map<String, String> getSortColumnMap() {
        HashMap<String, String> columnMap = new HashMap<String, String>();
        columnMap.put("s1", "name");
        columnMap.put("s3", "type");
        columnMap.put("s4", "modified");
        return columnMap;
    }

    public List<String> getTypes() {
    	if(types==null) {
    		QueryOptions qo = new QueryOptions();
    		qo.setDistinct(true);
    		List<String> props = new ArrayList<String>();
    		props.add("type");
    		
    		try {
    			Iterator<Object[]> rows = getContext().search(Bundle.class, qo, props);
    			if(rows.hasNext())
    				types= new ArrayList<String>();
    			while(rows.hasNext()) {
    				Object[] row = rows.next();
    				if(row[0]!=null)
    					types.add((String)row[0]);
    			}
    		} catch (GeneralException ge) {
    			log.info("Unable to fetch bundle types. Exception: " + ge.getMessage());
    		}    		
    	}
    	return types;
    }
    
    public List<SelectItem> getTypeSelections() {
        List<SelectItem> selections = new ArrayList<SelectItem>();
        
        List<String> types = getTypes();
        Map<String, RoleTypeDefinition> roleTypeDefs = null;

        if (types != null && !types.isEmpty()) {
            try {
                ObjectConfig roleConfig = getContext().getObjectByName(ObjectConfig.class, ObjectConfig.ROLE);
                roleTypeDefs = roleConfig.getRoleTypesMap();
            } catch (GeneralException e) {
                log.error("The Bundle list bean failed to obtain a role config when fetching type selections.  Raw type names will be used instead.", e);
            }

            
            for (String type : types) {
                String displayableTypeName = null;
                
                if (roleTypeDefs != null) {
                    RoleTypeDefinition typeDef = roleTypeDefs.get(type);
                    if (typeDef != null) {
                        displayableTypeName = typeDef.getDisplayableName();
                    }
                }
                
                if (displayableTypeName == null) {
                    displayableTypeName = type;
                }
                
                selections.add(new SelectItem(type, displayableTypeName));
            }
        }
        
        Collections.sort(selections, new SelectItemByLabelComparator(getLocale()));
        
        return selections;
    }
    
    public List<SelectItem> getTypeOptions() {
        List<SelectItem> typeOptions = new ArrayList<SelectItem>();
        try {
            ObjectConfig roleConfig = getContext().getObjectByName(ObjectConfig.class, ObjectConfig.ROLE);
            List<RoleTypeDefinition> roleTypes = roleConfig.getRoleTypesList();

            for(RoleTypeDefinition type : roleTypes) {
                typeOptions.add(new SelectItem(type.getName(), type.getDisplayableName()));
            }
        } catch (GeneralException ge) {
            log.info("Unable to fetch bundle types. Exception: " + ge.getMessage());
        }
        return typeOptions;
    }
    
    public boolean isUiMaxBundles() {
        try {
        Configuration config = getContext().getConfiguration();
        if(config!=null) {
            int maxBundles = config.getInt(Configuration.BUNDLES_UI_DISPLAY_LIMIT);
            uiMaxBundles = (maxBundles < getCount());
        }
        } catch (GeneralException ge) {
            log.error("Unable to fetch bundle limit from configuration. Exception: " + ge.getMessage());
        }
        return uiMaxBundles;
    }

}  // class BundleListBean
