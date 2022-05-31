/*  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.QueryOptions;
import sailpoint.object.Scope;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.IdentitySelector.MatchExpression;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 *  A custom task executor for generating roles based on a Business Functional 
 *  Model.  The Business Functional model is governed by HR-backed identity
 *  attributes.  The deterministic values of these attributes can be "mined"
 *  to automatically create Business Functional Roles (BFRs). 
 *  
 *  @author Sean Koontz
 *    
 */
public class BusinessFunctionalRoleGenerator extends AbstractTaskExecutor 
{
	
	//
	// Input Arguments
	//
    public static final String LEGACY_ARG_TIERED_SCOPING_ATTRIBUTES = "tieredScopingAttrs";
	public static final String ARG_TIERED_SCOPING_ATTRIBUTES = "tieredIdentityMiningAttrs";
	public static final String ARG_OWNER = "owner";
	public static final String ARG_GEN_CONTAINER_ROLE= "genContainerRole";
	public static final String ARG_MINIMUM_ROLE_SIZE = "minimumRoleSize";
	public static final String ARG_BFR_PREFIX = "bfrPrefix";
	public static final String ARG_PROFILE_PREFIX = "profilePrefix";
	public static final String ARG_CONTAINER_ROLE = "containerRole";
	public static final String ARG_CREATE_SUBROLES = "createSubRoles";
	public static final String ARG_COMPUTE_COVERAGE = "computeCoverage";
	public static final String ARG_UID_NAMING = "uidNaming";
	public static final String ARG_SIMULATE = "simulate";
	public static final String ARG_MINE_IT_ROLES = "mineITRoles";
	public static final String ARG_APPLICATIONS = "applications";
	public static final String ARG_THRESHOLD = "threshold";
	public static final String ARG_IT_ROLE_ASSOCIATION = "itRoleAssociation";
	public static final String ARG_ATTACH_IT_PROFILES = "attachITProfiles";
	public static final String ARG_ORGANIZATIONAL_ROLE_TYPE = "orgRoleType";
    public static final String ARG_BUSINESS_ROLE_TYPE = "businessRoleType";
    public static final String ARG_IT_ROLE_TYPE = "itRoleType";
		
	//
	// Return Arguments
	//	 
	public static final String RET_ROLES_CREATED = "rolesCreated";
	public static final String RET_ROLES_UPDATED = "rolesUpdated";
	public static final String RET_ROLES_DISCARDED = "rolesDiscarded";
    public static final String RET_COVERAGE = "roleCoverage";
    public static final String RET_SIMULATION = "simulation";
    public static final String RET_MINING_INPUT = "miningInput";
    public static final String RET_ENT_MINING_STATS = "entMiningStats";
    
    private static Log _log = LogFactory.getLog(BusinessFunctionalRoleGenerator.class);

    private SailPointContext _context;
    private TaskSchedule _schedule;
    private TaskResult _result;
    private Identity _owner;
	private List<Application> _applicationList;
    private Map<String, Double> _entMiningAverages;
    DirectRoleMiner _roleMiner;
    
    private String _tieredScopingAttrs;
    // This is the last container role that was created.  This value is not static; it will
    // change with each scoping set that gets processed
    private String _containerRole;
    private String _bfrPrefix;
    private String _applications;
    
    private String _orgRoleType;
    private String _businessRoleType;
    private String _itRoleType;
    
    private boolean _genContainerRole = false;
    private boolean _createSubRoles = false;
    private boolean _computeCoverage = false;
    private boolean _uidNaming = false;
    private boolean _simulate = false;
    private boolean _mineITRoles = false;
    private boolean _requiredAssociation = false;
    private boolean _attachITProfiles = false;
    private boolean _terminate = false;
    
    private int _minimumRoleSize = -1;
    private int _rolesCreated = 0;
    private int _rolesUpdated = 0;
    private int _rolesDiscarded = 0;
    private int _threshold = 100;
    
    private Map<String, RoleInfo> roleCoverageInfo;
    private Map<Object, String> scopingStringCache;
    
    private Set<String> generatedRoleNames;
    
    private class RoleInfo {
    	private String name;
    	private int size;
    	
    	RoleInfo(String name, int size) {
    		this.name = name;
    		this.size = size;
    	}
    	
    	public String getName() {
    		return name;
    	}
    	
    	public int getSize() {
    		return size;
    	}
    }
    
    public BusinessFunctionalRoleGenerator() 
    {
    	_entMiningAverages = new HashMap<String,Double>();
    	scopingStringCache = new HashMap<Object, String>();
    	generatedRoleNames = new HashSet<String>();
    }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        _terminate = true;
        if (_roleMiner != null) {
            _roleMiner.terminate();
        }
        if (_result != null) {
            _result.addMessage(errorMsg(MessageKeys.BFR_MINING_TERMINATED));
            _result.setTerminated(true);
        }
        return true;
    }
    
    public void execute(SailPointContext ctx, 
    		            TaskSchedule sched,
    		            TaskResult result,
    		            Attributes<String,Object> args)
    	throws Exception 
    {	
        _context = ctx;
        _schedule = sched;
        _result = result;
        
        _tieredScopingAttrs = args.getString(ARG_TIERED_SCOPING_ATTRIBUTES);
        if (_tieredScopingAttrs == null) {
            // This is for backward compatiblity.  If this argument wasn't found check for 
            // the old version of it and use that instead.
            _tieredScopingAttrs = args.getString(LEGACY_ARG_TIERED_SCOPING_ATTRIBUTES);
        }

        roleCoverageInfo = new HashMap<String, RoleInfo>();
        
        String owner = args.getString(ARG_OWNER);
        if (null != owner) {
        	_owner = _context.getObjectByName(Identity.class, owner);
        }
        
        // Until we support identity attributes, the profiles must reference the HR application
        //_hrApplication = args.getString(ARG_HR_APPLICATION);
        //if (null == _hrApplication) {
        //	result.addMessage(errorMsg("Please provide the name of an HR application that backs the scoping attributes."));
        //}        
    	//_application = _context.getObject(Application.class, _hrApplication);
    	//if (null == _application) {
    	//	throw new GeneralException("Specified HR application not found.");
    	//}
            	
        // These are mutually exclusive...
        _containerRole = args.getString(ARG_CONTAINER_ROLE);
    	_genContainerRole = args.getBoolean(ARG_GEN_CONTAINER_ROLE); 
    	_createSubRoles = args.getBoolean(ARG_CREATE_SUBROLES);
    	_computeCoverage = args.getBoolean(ARG_COMPUTE_COVERAGE);
    	_uidNaming = args.getBoolean(ARG_UID_NAMING);
    	_simulate = args.getBoolean(ARG_SIMULATE);
        
        _bfrPrefix = args.getString(ARG_BFR_PREFIX);
        _minimumRoleSize = args.getInt(ARG_MINIMUM_ROLE_SIZE);
        
        // Follow-on directed IT Role Mining parameters
        _mineITRoles = args.getBoolean(ARG_MINE_IT_ROLES);
    	_applications = args.getString(ARG_APPLICATIONS);
    	_applicationList = getApplications(_applications);    	
    	_threshold = args.getInt(ARG_THRESHOLD);
    	
    	// IT Role association flag, permits or required
    	_requiredAssociation = args.getBoolean(ARG_IT_ROLE_ASSOCIATION);

    	// Do we go old school or not (aka, Rikki, don't lose that number....)
    	_attachITProfiles = args.getBoolean(ARG_ATTACH_IT_PROFILES);
    	
    	_orgRoleType = args.getString(ARG_ORGANIZATIONAL_ROLE_TYPE);
    	if (_orgRoleType == null)
    	    _orgRoleType = "organizational";
    	_businessRoleType = args.getString(ARG_BUSINESS_ROLE_TYPE);
    	if (_businessRoleType == null)
    	    _businessRoleType = "business";
    	_itRoleType = args.getString(ARG_IT_ROLE_TYPE);
    	if (_itRoleType == null)
    	    _itRoleType = "it";
    	
        if (null == _tieredScopingAttrs) {
            _result.addMessage(errorMsg(MessageKeys.BFR_REQUIRES_SCOPING_ATTRIBUTES));
        }
                
        List<String> criteria = new ArrayList<String>();

        try {
        	// Parse scoping attribute list and build criteria list
        	String[] attributes = _tieredScopingAttrs.split(",");
        	for (int i = 0; i < attributes.length; i++) {
        		String scopingAttr = attributes[i].trim();
        		criteria.add(scopingAttr);
        	}
        	
        	// Mine the functional populations and return scoping sets
        	List<List<Object>> scopingSets = mineFunctionalPopulations(criteria);
        	Scope assignedScope = null;
        	
        	if (_containerRole != null) {
        	    Bundle container = _context.getObjectById(Bundle.class, _containerRole);
        	    if (container != null) {
        	        assignedScope = container.getAssignedScope();
        	    }
        	}
        	
        	if (scopingSets.size() > 0) {
        		generateBizRoles(scopingSets, criteria, assignedScope);
        	} else {
        		_log.info("No populations found for scoping attributes.");
        	}
        	
        } catch (Throwable t) {
            _result.addMessage(errorMsg(MessageKeys.ERR_BFR_FAILED, t.toString()));
            if (_log.isErrorEnabled())
                _log.error(t.getMessage(), t);
        }
        
        _result.setAttribute(RET_MINING_INPUT, criteria.toString());
        _result.setAttribute(RET_ROLES_CREATED, Util.itoa(_rolesCreated));
        _result.setAttribute(RET_ROLES_UPDATED, Util.itoa(_rolesUpdated));
        
        if (_minimumRoleSize > 0) 
            _result.setAttribute(RET_ROLES_DISCARDED, Util.itoa(_rolesDiscarded) + " (# of identities in role is less than " + _minimumRoleSize + ")");

        if (_computeCoverage || _simulate) {
        	double totalIds = getTotalIdentities();
        	int coverage = getRoleCoverage();
        	double percentage = (coverage / totalIds) * 100.0;
        	Formatter floatFormatter = new Formatter();
        	_result.setAttribute(RET_COVERAGE, floatFormatter.format("%.1f percent", percentage).toString());
        	if (_entMiningAverages.size() > 0) {
        		floatFormatter = new Formatter();
        		StringBuffer miningStats = new StringBuffer();
        		miningStats.append("Entitlement Mining Statistics [threshold = ");
        		miningStats.append(_threshold);
        		miningStats.append(" %]\n\n");
                miningStats.append("Overall average (used/found): ");
        		miningStats.append(floatFormatter.format("%.1f ", computeRunningAverage()).toString());
            	miningStats.append("%");
        		miningStats.append("\n");
        		miningStats.append("\n");
        		miningStats.append("Per mined functional role...");
        		miningStats.append("\n");
            	Iterator<String> it = _entMiningAverages.keySet().iterator();
            	while (it.hasNext()) {
            		String key = it.next();
                	miningStats.append("\n");
                	// there's got to be an easier way to format a float...*shrug*
            		floatFormatter = new Formatter();
                	miningStats.append(key + "  =  " + floatFormatter.format("%.1f ", _entMiningAverages.get(key)).toString());
                	miningStats.append("%");
            	}
            	_result.setAttribute(RET_ENT_MINING_STATS, miningStats.toString()); 
        	}
        }	
        if (_simulate)
            _result.setAttribute(RET_SIMULATION, "true");
    }

    private void generateBizRoles(List<List<Object>> scopingSets, List<String> criteria, Scope assignedScope)
    	throws GeneralException
    {    	    	
    	for (int i = 0; i < scopingSets.size() && !_terminate; i++) {
    		List <Object> scopingSet = scopingSets.get(i);

    		boolean isTopLevel = !_createSubRoles || scopingSet.size() == 1;
    		Bundle role = createRole(scopingSet, criteria, isTopLevel, assignedScope);
    		Bundle container;
    		
    		if (role != null && _genContainerRole) {
    			// Auto-gen the container based on top-tier scoping attribute
    		    Object scopingObj = scopingSet.get(0);
    		    String scopingString = getScopingString(scopingObj);
                container = generateContainerRole(scopingString);
    		} else if (role != null && _containerRole != null) {
    			// Dump 'em all in the same container role
    		    container = _context.getObjectById(Bundle.class, _containerRole);
    		} else {
    		    container = null;
    		}
            
            Bundle roleThatInherits = role;

            if (roleThatInherits != null) {
                // If flagged, create an inheritance hierarchy
                if (_createSubRoles) {
                    List<Object> scopingSubset = scopingSet.subList(0, scopingSet.size()-1);
                    
                    while (scopingSubset.size() > 0) {
                        // build a sub-role from the (n-1) tuple (e.g., Finance.Austin is a sub-role of Finance.Austin.Accountant)
                        Bundle roleToInherit = createRole(scopingSubset, criteria, scopingSubset.size() == 1, roleThatInherits.getAssignedScope());
                        
                        if (roleThatInherits != null) {
                            roleThatInherits.addInheritance(roleToInherit);
                            roleThatInherits.setAssignedScope(roleToInherit.getAssignedScope());
                        }
                        
                        scopingSubset = scopingSubset.subList(0, scopingSubset.size()-1);
                        roleThatInherits = roleToInherit;
                    }
                }
    
                // Connect the new role to container
                if (container != null) {
                    roleThatInherits.addInheritance(container);
                    roleThatInherits.setAssignedScope(container.getAssignedScope());
                }
            }

    		if (null != role && !_simulate) {
    			_context.saveObject(role);
    			_context.commitTransaction();
    		}
    		// Should still be able to simulate
			if (null != role && _mineITRoles) { 
				mineAccessProfiles(role, container);
			}
    	}
    }

    private void mineAccessProfiles(Bundle bfr, Bundle container)
    	throws GeneralException
    {
		List<Filter> filters = new ArrayList<Filter>();
		Map<String, Object> results = null;
		Bundle itRole = null;
    	
		filters.add(getNormalizedFilter(bfr));

		// With 3.0, we can now associate the mined IT access profiles to the BFR 
		// instead of having to co-mingle everything in one Bundle
		//
		if (_attachITProfiles) {
			itRole = bfr;
		} else {
			String itRoleName = "IT Role for " + bfr.getName();
			itRole = _context.getObjectByName(Bundle.class, itRoleName);
			if (null == itRole) {
				itRole = new Bundle();
				itRole.setType(_itRoleType);
				itRole.setName(itRoleName);
				String DATE_FORMAT = "M/d/y H:mm:s a";
				java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(DATE_FORMAT);
				String now = sdf.format(new Date());
				itRole.setDescription("Mined IT role. Date mined: " + now);
				if (container != null) {
				    itRole.addInheritance(container);
				    itRole.setAssignedScope(container.getAssignedScope());
				}
				itRole.setOwner(_owner);
			}
		}
		
		_roleMiner = new DirectRoleMiner(_context, itRole, filters, _applicationList, _threshold, _schedule);
		results = _roleMiner.mineRole();
		
		if (!results.containsKey("errorMsg")) {
		    float examinedEnts = 0;
		    int usedEnts = 0;
		    if(((Integer) results.get(DirectRoleMiner.NUM_CANDIDATE_ENTITLEMENTS))!= null) {
		        examinedEnts = ((Integer) results.get(DirectRoleMiner.NUM_CANDIDATE_ENTITLEMENTS)).intValue();
		    }
		    if(((Integer) results.get(DirectRoleMiner.NUM_USED_ENTITLEMENTS)) != null) {
		        usedEnts = ((Integer) results.get(DirectRoleMiner.NUM_USED_ENTITLEMENTS)).intValue();
		    }
			if (examinedEnts > 0) {
				double avg = (usedEnts / examinedEnts) * 100.0;
				_entMiningAverages.put(bfr.getName(), avg);
			} else {
				_entMiningAverages.put(bfr.getName(), 0.0);				
			}
			_log.info(results.get(DirectRoleMiner.TASK_RESULTS));
			if (usedEnts > 0 && !_simulate) {
				if (_attachITProfiles) {
					_context.saveObject(itRole);
				} else {
					if (_requiredAssociation) {
						bfr.addRequirement(itRole);					
					} else {
						bfr.addPermit(itRole);
					}
					_context.saveObject(itRole);
					_context.saveObject(bfr);
				}
				_context.commitTransaction();
			}
			
			if (!_simulate) {
				// refresh regardless so the BFR will get correlated
			    _roleMiner.refreshIdentities();
			}
		}
    }
    
    private double computeRunningAverage()
    {
    	double totalValue = 0;
    	
    	Iterator<String> it = _entMiningAverages.keySet().iterator();
    	while (it.hasNext()) {
    		String key = it.next();
    		totalValue += _entMiningAverages.get(key);
    	}
    	
    	return (totalValue / _entMiningAverages.size());
    }
    
    private Filter getNormalizedFilter(Bundle bfr) throws GeneralException {
        
        // Since BFRs use assignment rules with match expressions, we need to
        // transform them into a Filter model so that we can pass that on to
        // the Directed Mining class
        //
        return getMatchExpressionFilter(bfr.getSelector().getMatchExpression());
    } 
    
    public static Filter getMatchExpressionFilter(MatchExpression exp) {
        
        Filter theFilter = null;
        Iterator<MatchTerm> it = exp.getTerms().iterator();
        while (it.hasNext()) {
            MatchTerm term = it.next();
            if (null == theFilter) {
                theFilter = getMatchTermFilter(term);
            } else {
                Filter filter = getMatchTermFilter(term);
                if (exp.isAnd()) {
                    theFilter = Filter.and(filter, theFilter);
                } else {
                    theFilter = Filter.or(filter, theFilter);
                }
            }
        }
        return theFilter;
    }
    private static Filter getMatchTermFilter(MatchTerm term) {
        if (term.isContainer()) {
            Filter theFilter = null;
            for (MatchTerm child : term.getChildren()) {
                Filter childFilter = getMatchTermFilter(child);
                if (theFilter == null) {
                    // initialization
                    theFilter = childFilter;
                } else {
                    if (term.isAnd()) {
                        theFilter = Filter.and(childFilter, theFilter);
                    } else {
                        theFilter = Filter.or(childFilter, theFilter);
                    }
                }
            }
            return theFilter;
        } else {
            return Filter.eq(term.getName(), term.getValue());
        }
	}

    /**
	 * Parse a string application IDs separated by ',' and return a List of
	 * Applications
	 */
	private List<Application> getApplications(String apps) 
	{
		ArrayList<Application> appsList = new ArrayList<Application>();

		try {
			RFC4180LineParser parser = new RFC4180LineParser(',');

			if (apps != null) {
				ArrayList<String> tmpList = parser.parseLine(apps);

				for (String appId : tmpList) {
					Application app = _context.getObjectById(Application.class, appId.trim());
					appsList.add(app);
				}
			}
		} catch (Exception e) {
		    if (_log.isErrorEnabled())
		        _log.error(e.getMessage(), e);
		}

		return appsList;
	}
    
    private String generateRoleName(List<Object> scopingSet)
    {
    	String roleName = "BFR";

    	if (null != _bfrPrefix) {
			roleName = _bfrPrefix;
		}
    	
    	if (_uidNaming) {
        	String DATE_FORMAT = "yyyyMd_Hmms_SSS";
        	java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(DATE_FORMAT);
        	String now = sdf.format(new Date());
    		roleName = roleName + "." + now;
    		if (generatedRoleNames.contains(roleName)) {
                StringBuilder roleNameEditor = new StringBuilder(roleName);
        		while (generatedRoleNames.contains(roleName)) {
        		    int end = roleName.length();
        		    int start = end - 3;
        		    int millis = Integer.parseInt(roleName.substring(start, end));
        		    millis++;
        		    String millisString = Integer.toString(millis);
        		    if (millisString.length() == 1) {
        		        roleNameEditor.replace(start, start + 2, "00");
        		        start += 2;
        		    } else if (millisString.length() == 2) {
        		        roleNameEditor.replace(start, start + 1, "0");
        		        start ++;
        		    }     		    
        		    roleNameEditor.replace(start, end, millisString);
        		    roleName = roleNameEditor.toString();
        		} 
    		}
    		generatedRoleNames.add(roleName);
    	} else {
    		for (int j = 0; j < scopingSet.size(); j++) {
    			roleName = roleName + "." + getScopingString(scopingSet.get(j));
    		}
    	}
    	
    	return roleName;
    }
    
    private Bundle createRole(List<Object> scopingSet, List<String> criteria, boolean isTopLevel, Scope assignedScope) 
    	throws GeneralException 
    {
    	Bundle role = null;
    	IdentitySelector selector = null;

    	// check minimumRoleSize parameter to see if we discard this one
    	int roleSize = getPopulationSize(scopingSet, criteria);
    	if (roleSize < _minimumRoleSize) {
    		_rolesDiscarded++;
    		return null;
    	}
    	
		// Generate a role name based on task config parameters
    	String roleName = generateRoleName(scopingSet);
		
    	// if we are simulating, probably want coverage data too
    	if ((_computeCoverage || _simulate) && isTopLevel) {
    		roleCoverageInfo.put(roleName, new RoleInfo(roleName, roleSize));
    	}

    	selector = generateAssignmentRule(scopingSet, 0, criteria);	
		
		
		role = _context.getObjectByName(Bundle.class, roleName);
		if (null == role) {	
			role = new Bundle();
			_rolesCreated++;
		} else {
		    return role;
		}
    	
    	role.setName(roleName);		
    	if (_owner != null) {
    		role.setOwner(_owner);
    	}
    	
    	role.setAssignedScope(assignedScope);

    	String DATE_FORMAT = "M/d/y H:mm:s a";
    	java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(DATE_FORMAT);
    	String now = sdf.format(new Date());
    	role.setDescription("Mined Business Functional Role. Date mined: " + now);
    	
		role.setType(_businessRoleType);
    	
    	// bind the assignment rule to the BFR
    	role.setSelector(selector);

    	// Disable the rule upon creation to force validation through the appropriate workflows
    	role.setDisabled(true);
    	
    	return role;
    }
    
    private IdentitySelector generateAssignmentRule(List<Object> scopingSet, int scopingAttributeOffset, List<String> criteria)
    {
    	IdentitySelector assignmentRule = new IdentitySelector();
		MatchExpression matcher = new MatchExpression();
		
		// like cement, baby
		matcher.setAnd(true);
        
    	// Build an assignment rule using the identity attributes
		for (int i = 0; i < scopingSet.size(); i++) {
			String identityAttribute = criteria.get(i+scopingAttributeOffset);
			String identityAttributeValue = getScopingString(scopingSet.get(i));
			MatchTerm term = new MatchTerm();
			term.setName(identityAttribute);
			term.setValue(identityAttributeValue);
			matcher.addTerm(term);
		}

    	assignmentRule.setMatchExpression(matcher);
    	assignmentRule.setSummary("Identity attribute assignment rule : " + scopingSet.toString());
    	
    	return assignmentRule;
    }
        
    private List<List<Object>> mineFunctionalPopulations(List<String> criteria)
    	throws GeneralException
    {
    	List<List<Object>> filterSets = new ArrayList<List<Object>>();
    	
    	QueryOptions ops = new QueryOptions();
    	ops.setDistinct(true);
    	 
    	List<String> props = new ArrayList<String>();
    	for (int i = 0; i < criteria.size(); i++) {
    		props.add(criteria.get(i));
    	}
    	 
    	Iterator<Object[]> result = _context.search(Identity.class, ops, props);
    	while (result.hasNext() && !_terminate) {
    		Object[] row = result.next();
    		boolean fullSet = true;
    		List<Object> set = new ArrayList<Object>();
    		for (int i = 0; i < criteria.size(); i++) {
    			if (row[i] == null) {
    				fullSet = false;
    				break;
    			} else {
    				set.add(row[i]);
    			}
    		}
    		if (fullSet) {
    			filterSets.add(set);
    		}
    	}
    	
    	return filterSets;
    }
        
    private int getPopulationSize(List<Object> scopingSet, List<String> criteria)
    	throws GeneralException
    {
       	QueryOptions ops = new QueryOptions();
    	ops.setDistinct(true);

    	for (int i=0; i < scopingSet.size(); i++) {
    		Filter filter = Filter.eq(criteria.get(i), scopingSet.get(i));
    		ops.add(filter);
    	}
    	    	 
    	return _context.countObjects(Identity.class, ops);
    }

    private int getTotalIdentities()
    	throws GeneralException
    {
    	QueryOptions ops = new QueryOptions();
    	ops.setDistinct(true);
	    	 
    	return _context.countObjects(Identity.class, ops);
    }
    
    private Bundle generateContainerRole(String name) 
    	throws GeneralException
    { 
    	Bundle container = _context.getObjectByName(Bundle.class, name);
    	if (null == container && name != null) {
    		container = new Bundle();
    		container.setName(name);
    		container.setOwner(_owner);
    		container.setType(_orgRoleType);
            container.setDisabled(true);
    		if (!_simulate) {
    			_context.saveObject(container);
    			_context.commitTransaction();
    		}
    	} 
    	    	
    	return container;
    }
    
	private Message errorMsg(String msg) {
		return new Message(Message.Type.Error, msg);
	}
	
	private Message errorMsg(String msg, Object... params) {
	    return new Message(Message.Type.Error, msg, params);
	}
	
	private int getRoleCoverage() {
		int coverage = 0;
		Collection<RoleInfo> roleSizes = roleCoverageInfo.values();
		
		if (roleSizes != null && !roleSizes.isEmpty()) {
			for (RoleInfo roleSize : roleSizes) {
				coverage += roleSize.getSize();
			}
		}
		
		return coverage;
	}
	
	private String getScopingString(Object scopingObj) {	    
        String scopingString;
        
        if (scopingObj == null) {
            scopingString = "";
        } else {
            scopingString = scopingStringCache.get(scopingObj);
        }
        
        if (scopingString == null) {
            if (scopingObj instanceof Identity) {
                scopingString = ((Identity)scopingObj).getDisplayableName();
            } else if (scopingObj instanceof Boolean){
                scopingString = Boolean.toString((Boolean)scopingObj);
            } else {
                // Try to coerce it and throw an IllegalArgumentException if we still fail
                try {
                    scopingString = scopingObj.toString();
                } catch (Exception e) {
                    throw new IllegalArgumentException("The Business Role Generator cannot handle an attribute of type " + scopingObj.getClass().getName());
                }
            }
            
            scopingStringCache.put(scopingObj, scopingString);
        }
        
        return scopingString;
	}
}
