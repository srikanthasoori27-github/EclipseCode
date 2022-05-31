/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.RoleLifecycler;
import sailpoint.object.ActivityConfig;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.BundleArchive;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleIndex;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SailPointObject;
import sailpoint.reporting.BusinessRoleCompositionReport;
import sailpoint.reporting.BusinessRoleMembershipReport;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleUtil;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class BundleDataSource extends SailPointDataSource<Bundle> {

	private static final Log log = LogFactory.getLog(BundleDataSource.class);

	List<Filter> subReportFilters;
	
	/** On some reports, the distinct query is not working as expected to filter out duplicates.
	 * We'll compare all role ids as we parse through the list to see if we've already processed them. **/
	List<String> roleIds;
	
	String emptyMembership;
	String emptyComposition;
    RoleUtil util;
    Locale locale;
    ObjectConfig roleConfig;
    RoleLifecycler lifeCycler;

    public BundleDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
        super(filters, locale, timezone);
        setScope(Bundle.class);
        
    }
    
    public BundleDataSource(List<Filter> filters, Locale locale, TimeZone timezone, Attributes args) {
		super(filters, locale, timezone);
		qo.setOrderBy("name");
        qo.setDistinct(true);
		setScope(Bundle.class);
		subReportFilters = null;
        util = new RoleUtil();
		this.locale = locale;
		emptyMembership = ((Attributes)args).getString(BusinessRoleMembershipReport.BUSINESS_ROLE_MEMBERSHIP_EXCEPTIONS_ARG);
		emptyComposition = ((Attributes)args).getString(BusinessRoleCompositionReport.BUSINESS_ROLE_COMPOSITION_EXCEPTIONS_ARG);
		
	}

	@Override
	public void internalPrepare() throws GeneralException {
        roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        roleIds = new ArrayList<String>();
        lifeCycler = new RoleLifecycler(getContext());
		updateProgress("Querying for Roles...");
		_objects = getContext().search(Bundle.class, qo);
	}

	public Object getFieldValue(JRField jrField) throws JRException {

		String fieldName = jrField.getName();
		Object value = null;
		if(fieldName.equals("applicationMapList")) {
			List<Map> applicationMapList = null;
			List<Profile> profiles = _object.getProfiles();
			if(profiles!=null) {
				applicationMapList = new ArrayList<Map>();
				for(Profile profile : profiles) {
					Map<String, Object> appNames = new HashMap<String, Object>();
					appNames.put("appName", profile.getApplication().getName());
					appNames.put("name", profile.getName());
                    appNames.put("description", profile.getDescription());

					if(profile.getOwner()!=null) {
						appNames.put("owner", profile.getOwner().getDisplayableName());
					}
					appNames.put("description", profile.getDescription());
					
					if(profile.getConstraints()!=null) {
						/**Handle profile constraint report **/
						List<Map>profileConstraintMapList = new ArrayList<Map>();
                        List<String> filterStrings = new ArrayList<String>();
						for(Filter filter : profile.getConstraints()) {
							Map<String, String> constraints = new HashMap<String, String>();
							constraints.put("elt", filter.toString());
                            filterStrings.add(filter.toString());
							profileConstraintMapList.add(constraints);
						}
						appNames.put("profileConstraintMapList", profileConstraintMapList);
                        
                        appNames.put("filters", Util.listToCsv(filterStrings));

					}

					if(profile.getPermissions()!=null) {
						/**Handle profile constraint report **/
						List<Map>profilePermissionMapList = new ArrayList<Map>();
						for(Permission permission : profile.getPermissions()) {
							Map<String, String> permissions = new HashMap<String, String>();
							permissions.put("target", permission.getTarget());
							permissions.put("rights", permission.getRights());
							profilePermissionMapList.add(permissions);
						}
						appNames.put("profilePermissionMapList", profilePermissionMapList);

					}

					applicationMapList.add(appNames);
				}
			}
			value = applicationMapList;
		} else if(fieldName.equals("activityConfig")) {
			ActivityConfig config = _object.getActivityConfig();
			if(config!=null) {
				value = config.toString();
			} else return null;
		} else if(fieldName.equals("permits")) {
            if(_object.getPermits()!=null && !_object.getPermits().isEmpty()) {
                List<String> roleNames = new ArrayList<String>();
                for(Bundle bundle : _object.getPermits()) {
                    roleNames.add(bundle.getName());
                }
                value = Util.listToCsv(roleNames);
            } else {
                value = "";
            }
        } else if(fieldName.equals("requireds")) {
            if(_object.getRequirements()!=null && !_object.getRequirements().isEmpty()) {
                List<String> roleNames = new ArrayList<String>();
                for(Bundle bundle : _object.getRequirements()) {
                    roleNames.add(bundle.getName());
                }
                value = Util.listToCsv(roleNames);
            } else {
                value = "";
            }
        } else if(fieldName.equals("inheritance") ) {
            if(_object.getInheritance()!=null && !_object.getInheritance().isEmpty()) {
                List<String> roleNames = new ArrayList<String>();
                for(Bundle bundle : _object.getInheritance()) {
                    roleNames.add(bundle.getName());
                }
                value = Util.listToCsv(roleNames);
            } else {
                value = "";
            }
        } 
        else if (fieldName.equals("classifications.id") ||
                fieldName.equals("classificationNames")) {
            value = Util.listToCsv(_object.getClassificationDisplayNames());
        }
        else if (fieldName.equals("extendedAttributes")) {
            if (_object.getExtendedAttributes() != null && !_object.getExtendedAttributes().isEmpty()) {
                List<String> attributes = new ArrayList<String>();
                for (Map.Entry<String, Object> attrEntry : _object.getExtendedAttributes().entrySet()) {
                    if (attrEntry.getValue() != null) {
                        String attrKey = attrEntry.getKey();
                        if(attrKey != null && !attrKey.equals(SailPointObject.ATT_DESCRIPTIONS)) {
                            attributes.add(attrKey + "='" +attrEntry.getValue() + "'");
                        }
                    }
                }
                value = Util.listToCsv(attributes);
                if(value == null) {
                    value = "";
                }
            }
            else{
                value="";
            }
        } else if (fieldName.equals("iiqRights")) {
            Map<String, List<String>> provisioningPlanValues = null;
            try{
                provisioningPlanValues = RoleUtil.getValuesFromProvisioningPlan(_object);
            } catch(GeneralException ge) {
                log.warn("Exception thrown while fetching provisioning plan for bundle: " + _object.getName() + " Exception: " + ge.getMessage());
            }
            
            String valueString = "";
            if (!Util.isEmpty(provisioningPlanValues)) {
                if (provisioningPlanValues.containsKey(ProvisioningPlan.ATT_IIQ_CAPABILITIES) &&
                        !Util.isEmpty(provisioningPlanValues.get(ProvisioningPlan.ATT_IIQ_CAPABILITIES))) {
                    valueString += String.format("%1$s: %2$s", 
                            ProvisioningPlan.ATT_IIQ_CAPABILITIES, 
                            Util.listToCsv(provisioningPlanValues.get(ProvisioningPlan.ATT_IIQ_CAPABILITIES)));
                }
                if (provisioningPlanValues.containsKey(ProvisioningPlan.ATT_IIQ_CONTROLLED_SCOPES) &&
                        !Util.isEmpty(provisioningPlanValues.get(ProvisioningPlan.ATT_IIQ_CONTROLLED_SCOPES))) {
                    if (!Util.isNullOrEmpty(valueString)) {
                        //put scopes on second line, if capabilities are there
                        valueString += "\n";
                    }
                    valueString += String.format("%1$s: %2$s", 
                            ProvisioningPlan.ATT_IIQ_CONTROLLED_SCOPES, 
                            Util.listToCsv(provisioningPlanValues.get(ProvisioningPlan.ATT_IIQ_CONTROLLED_SCOPES)));
                }
            }
            
            value = valueString;
        } else if(fieldName.equals("assignmentRule")) {
            Map selectorInfo = util.getSelectorInfo(_object, this.locale, false);
            if(selectorInfo.get("selectorContents")!=null)
                value = selectorInfo.get("selectorContents");
            else
                value="";
        } else if(fieldName.equals("bundleMembers")) {
			
            // Used to use a single query that OR'd together searching for
            // assigned or detected roles.  This causes outer joins that don't
            // scale with a large number of roles.  Instead, break this up into
            // two separate queries.  Let sorting and uniqueness checking happen
            // with the TreeMap.
			Map<String,Map<String,String>> bundleMembers =
			    new TreeMap<String,Map<String,String>>();
			if (isAssignable(_object)) {
			    addMembers("assignedRoles.id", bundleMembers);
			}
			if (isDetectable(_object)) {
			    addMembers("bundles.id", bundleMembers);
			}
			
			return new ArrayList<Map<String,String>>(bundleMembers.values());
		} else if(fieldName.equals("disabled")) {
		    if(_object.isDisabled())
                value = getMessage(MessageKeys.DISABLED);
            else
                value = getMessage(MessageKeys.ENABLED);
        } else if (fieldName.equals("inactive")) {
            /** used in BusinessRoleMainReport.jrxml **/
            if (_object.isDisabled()) {
                value = new Boolean(true);
            } else {
                value = new Boolean(false);
            }
        } else if(fieldName.startsWith("RoleIndex.")) {
            /** For handling joins to the RoleIndex **/
            fieldName = fieldName.substring(10);            
            
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("bundle.id", _object.getId()));
            qo.setResultLimit(1);
            
            try {
                List<RoleIndex> indexes = getContext().getObjects(RoleIndex.class, qo);
                if(indexes!=null && !indexes.isEmpty()) {
                    RoleIndex index = indexes.get(0);
                    value = PropertyUtils.getNestedProperty(index, fieldName);
                    
                }
            } catch(GeneralException ge) {
                log.warn("Unable to load associated role index.  Exception: " + ge.getMessage());
            } catch(Exception e) {
                log.warn("Unable to get property value from reflection.  Exception: " + e.getMessage());
            }
        } else if(fieldName.equals("version")) {
            BundleArchive archive = getBundleArchive();
            if(archive!=null)
                value = archive.getVersion();
        } else if(fieldName.equals("changeDate")) {
            BundleArchive archive = getBundleArchive();
            if(archive!=null)
                value = archive.getCreated();
        } else if(fieldName.equals("approver.name")) {
            BundleArchive archive = getBundleArchive();
            if(archive!=null)
                value = archive.getCreator();
        } else if (fieldName.equals("type")) {
            String type = _object.getType();
            if (roleConfig == null) {
                value = type;
            } else {
                RoleTypeDefinition roleType = roleConfig.getRoleType(type);
                if (roleType == null) {
                    value = type;
                } else {
                    value = roleType.getDisplayableName();
                }
            }
        }
		if(value==null)
			value = super.getFieldValue(jrField);
        
        /** If the value is still null, try to get it from the object's attributes **/
        if(value==null && roleConfig!=null) {
            value = _object.getAttribute(fieldName);
            
            /** Cast it to it's correct class **/
            if(value!=null) {
                ObjectAttribute attr = roleConfig.getObjectAttribute(fieldName);                
                if(attr!=null && attr.getType()!=null) {
                    if(attr.getType().equals("int")) {
                        value = Integer.parseInt((String)value);
                    }
                }
                
            }
        }
        if ( log.isDebugEnabled() ) {
            log.debug("fieldName: "+ fieldName + " value: " + value);
        }
        if(fieldName.equals("description")) {
            value = (_object).getDescription(locale);
            value = WebUtil.safeHTML((String)value);
        }
        return value;
	}

	/**
	 * Return whether the given role is assignable per its type.
	 */
	private boolean isAssignable(Bundle role) {
	    boolean assignable = false;
	    if (null != role.getType()) {
	        RoleTypeDefinition roleType = roleConfig.getRoleType(role.getType());
	        if (null != roleType) {
	            assignable = roleType.isAssignable();
	        }
	    }
	    return assignable;
	}

    /**
     * Return whether the given role is detectable per its type.
     */
    private boolean isDetectable(Bundle role) {
        boolean detectable = true;
        if (null != role.getType()) {
            RoleTypeDefinition roleType = roleConfig.getRoleType(role.getType());
            if (null != roleType) {
                detectable = roleType.isDetectable();
            }
        }
        return detectable;
    }

    /**
     * Search for role members using the given role ID property (rooted from the
     * identity) and add them to the membersByName map.
     */
    private void addMembers(String bundleIdProp,
                            Map<String,Map<String,String>> membersByName) {

        List<String> props = new ArrayList<String>();
        props.add("name");
        props.add("firstname");
        props.add("lastname");
        
        QueryOptions memberQueryOps = new QueryOptions();
        memberQueryOps.add(Filter.eq(bundleIdProp, _object.getId()));
        memberQueryOps.setOrderBy("name");
        try {
            Iterator<Object[]> results = getContext().search(Identity.class, memberQueryOps, props);
            while (results.hasNext()) {
                Map<String, String> member = new HashMap<String, String>();
                Object[] row = results.next();
                String name = (String) row[0];
                member.put("username", name);
                member.put("firstname",(String)row[1]);
                member.put("lastname",(String)row[2]);
                
                membersByName.put(name, member);
            }
        } catch(GeneralException ge) {
            log.warn("Exception thrown while fetching identities with bundle: " + _object.getName() + " Exception: " + ge.getMessage());
        }
    }
    
	public boolean internalNext() throws JRException {
		boolean hasMore = false;
		if ( _objects != null ) {
			hasMore = _objects.hasNext();
			if ( hasMore ) {
				_object = _objects.next();
				
				if(roleIds.contains(_object.getId()))
					return internalNext();
				
				roleIds.add(_object.getId());
				
				/** If we are filtering on whether the membership of this role is empty...**/
				if(emptyMembership!=null) {
					Boolean membershipEmpty = Boolean.parseBoolean(emptyMembership);
					int count = 0;
					List<Filter> countFilters = new ArrayList<Filter>();

					if (isAssignable(_object)) {
					    countFilters.add(Filter.eq("assignedRoles.id", _object.getId()));
					}
					if (isDetectable(_object)) {
					    countFilters.add(Filter.eq("bundles.id", _object.getId()));
					}
					if (!countFilters.isEmpty()) {
					    QueryOptions countOps = new QueryOptions();
					    countOps.add(Filter.or(countFilters));

					    try {
					        count = getContext().countObjects(Identity.class, countOps);
					    } catch(GeneralException ge) {
					        log.warn("Exception: " + ge.getMessage() + " during internalNext()");
					    }
					}
					
					if(membershipEmpty && count>0)
						return internalNext();
					else if(!membershipEmpty && count==0)
						return internalNext();
				}
				
				/** If we are filtering on whether the membership of this role is empty...**/
				if(emptyComposition!=null) {
					Boolean membershipEmpty = Boolean.parseBoolean(emptyComposition);
					
					if(membershipEmpty && _object.getProfiles().size()>0)
						return internalNext();
					else if(!membershipEmpty && _object.getProfiles().size()==0)
						return internalNext();
				}
				
			} else {
				_object = null;
			}

			if ( _object != null ) {
				updateProgress("Role", _object.getName());
			}
		}
		//log.debug("Getting Next: " + hasMore);
		return hasMore;
	}
    
    private BundleArchive getBundleArchive() {
        try {
            List<BundleArchive> archives = lifeCycler.getArchiveInfo(_object);
            if(archives!=null && !archives.isEmpty()) {
                BundleArchive archive = archives.get(0);
                return archive;
            }
            
        } catch(GeneralException ge) {
            log.warn("Unable to get archives for role: " + _object);
        }
        return null;
    }

}
