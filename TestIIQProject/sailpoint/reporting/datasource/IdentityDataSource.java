/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Permission;
import sailpoint.reporting.ResourceBundleProxy;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.identity.IdentityProxy;
import sailpoint.web.messages.MessageKeys;

/**
 * @author peter.holcomb
 *
 */
public class IdentityDataSource extends SailPointDataSource<Identity> {

	private static final Log log = LogFactory.getLog(IdentityDataSource.class);
	Attributes<String,Object> inputs;
    private ResourceBundle resourceBundle;
    
    /** Bug 4088 - For some reason, setDistinct(true) does not appear to work in some instances
     * on Oracle.  To fix this, we are going to keep a set of ids that we've already processesed and
     * only print those that we haven't seen.
     */
    Set<String> processedIds;


    public IdentityDataSource(List<Filter> filters, Locale locale, TimeZone timezone,
			Attributes<String,Object> inputs) {
		super(filters, locale, timezone);
		qo.setOrderBy("name");
		qo.setDistinct(true);
        processedIds = new HashSet<String>();
		this.inputs = inputs;
		setScope(Identity.class);
        resourceBundle = inputs.get(JRParameter.REPORT_RESOURCE_BUNDLE) != null ?
            (ResourceBundle)inputs.get(JRParameter.REPORT_RESOURCE_BUNDLE) : new ResourceBundleProxy(Locale.getDefault());
    }

	@Override
	public void internalPrepare() throws GeneralException {
		try {
			_objects = getContext().search(Identity.class, qo);
		} catch (GeneralException ge) {
			log.error("GeneralException caught while executing search for identities: " + ge.getMessage());
			_objects = null;
		}
	}

	/* 
	 * On some special fields that are passed to sub-reports, we have to build a list of map objects.
	 * These lists are passed to a subreport for each row in the main report and the map key
	 * is used by the report to get the value.  
	 * 
	 * On fields that don't end up in subreports, we can use the IdentityProxy to pass a string
	 * representation of the requested identity property.
	 */
	public Object getFieldValue(JRField jrField) throws JRException {
		String fieldName = jrField.getName();

		Object value = null;
		
			if(fieldName.equals("businessRoleMapList")) {
				List<Map> bundleMapList = new ArrayList();
				
				Set<Bundle> bundles = new HashSet<Bundle>();
				if(_object.getBundles()!=null)
					bundles.addAll(_object.getBundles());
				
				if(_object.getAssignedRoles()!=null)
					bundles.addAll(_object.getAssignedRoles());
				
				for(Bundle bundle : bundles) {
					Map<String, Object> bundleNames = new HashMap<String, Object>();
					Boolean detected = (_object.getBundles()!=null && _object.getBundles().contains(bundle));
					Boolean assigned = (_object.getAssignedRoles()!=null && _object.getAssignedRoles().contains(bundle));

                    bundleNames.put("bundle", bundle.getName());
					bundleNames.put("detected", resourceBundle.getObject(detected ? MessageKeys.TXT_TRUE :MessageKeys.TXT_FALSE));
					bundleNames.put("assigned", resourceBundle.getObject(assigned ? MessageKeys.TXT_TRUE : MessageKeys.TXT_FALSE));
			
					bundleMapList.add(bundleNames);
				}
				value = bundleMapList;
			}
			else if(fieldName.equals("applicationMapList")) {
				List<Map<String,String>> applicationMapList = null;
				List<Link> links = _object.getLinks();
				if(links!=null) {                
					applicationMapList = new ArrayList<Map<String,String>>();

					//Need to pull the filters on application out of the filter list
					//and limit the applications that are returned to the user based on
					//what was searched for.
					String applicationIds = null;
					if(inputs!=null) {
						applicationIds = inputs.getString("applications");
					}

					for(Link link : links) {
						Map<String, String> appNames = new HashMap<String, String>();

						if(applicationIds!=null && !applicationIds.equals("")) {
							if(applicationIds.contains(link.getApplication().getId())) {
								appNames.put("appName", link.getApplication().getName());
							}
						}
						else {
							appNames.put("appName", link.getApplication().getName());
						}
						applicationMapList.add(appNames);
					}
				}
				value = applicationMapList;
			}
			else if(fieldName.equals("extraEntitlementMapList"))
			{
				List<Map> entitlementList = null;
				List<EntitlementGroup> eGroups = _object.getExceptions();

				String applicationIds = null;
				if(inputs!=null) {
					applicationIds = inputs.getString("applications");
				}

				if(eGroups!=null) {
					entitlementList = new ArrayList<Map>();
					for(EntitlementGroup eGroup : eGroups) {
						Map<String, Object> eMap = new HashMap<String, Object>();
						Application app = eGroup.getApplication();

						//Only show the extra entitlements if the application is included in the
						//application filters
						if(applicationIds==null || applicationIds.contains(app.getId())) {
							if(app!=null)
								eMap.put("egAppName", app.getName());
							Attributes attrs = eGroup.getAttributes();
							if(attrs!=null)
								eMap.put("egAttributes", XMLObjectFactory.getInstance().toXml(attrs, false));


							//Now build the list of permission maps to send to sub report
							List<Permission> perms = eGroup.getPermissions();
							List<Map> permissionList = null;
							if(perms!=null) {
								permissionList = new ArrayList<Map>();
								for(Permission perm : perms) {
									Map<String, String> permMap = new HashMap<String, String>();
									permMap.put("target", perm.getTarget());
									permMap.put("rights", perm.getRights());
									permissionList.add(permMap);
								}
							}
							eMap.put("egPermissions", permissionList);

							entitlementList.add(eMap);
						}
					}
				}
				value = entitlementList;
			}
            /** For compatibility with the applications column on the identity search grid **/
            else if(fieldName.equals("links.application.id")) {
                List<String> applications = new ArrayList<String>();
                List<Link> links = _object.getLinks();
                for(Link link: links) {
                    applications.add(link.getApplicationName());
                }
                
                value = Util.listToCsv(applications);
            }
			else if(fieldName.equals("attributes")) {
				value = XMLObjectFactory.getInstance().toXml(_object.getAttributes(),false);
			} else if(fieldName.equals("workgroups.id")) {
                List<String> workgroups = new ArrayList<String>();
                List<Identity> wgs = _object.getWorkgroups();
                /* If there are no workgroups, just return an empty string */
                if(wgs!=null && !wgs.isEmpty()) {
                    for(Identity wg: wgs) {
                        workgroups.add(wg.getName());
                    }
                
                    value = Util.listToCsv(workgroups);
                } else {
                    value = "";
                }
            } else {
				value = IdentityProxy.get(_object, fieldName);
				// Maintain backward compatibility due to changes in bug 4469 -- Bernie
				if (value != null && _object instanceof Identity) {
				    value = value.toString();
				}
			}
		if(value==null) {
			value = super.getFieldValue(jrField);
		}
        
        log.debug("Field: " + fieldName + " Value: " + value);
		return value;
	}

	/* (non-Javadoc)
	 * @see net.sf.jasperreports.engine.JRDataSource#next()
	 */
	public boolean internalNext() throws JRException {
		boolean hasMore = false;
		if ( _objects != null ) {
			hasMore = _objects.hasNext();
			if ( hasMore ) {
				try {
					// clear the last one...
					if ( _object != null ) {
						getContext().decache(_object);
					}
				} catch (Exception e) {
					log.warn("Unable to decache identity." + e.toString());
				}
				_object = _objects.next();
				if ( _object != null ) {                    
                    String id = _object.getId();
                    if(processedIds.contains(id)) {
                        boolean found = false;
                        while(!found && hasMore){
                            hasMore = _objects.hasNext();
                            if(hasMore) {
                                _object = _objects.next();
                                found = !processedIds.contains(_object.getId());
                            } 
                            processedIds.add(_object.getId());
                        }

                    } else {
                        processedIds.add(id);
                        updateProgress("Identity", _object.getName());
                    }
				}
			} 
		}
		return hasMore;
	}
}
