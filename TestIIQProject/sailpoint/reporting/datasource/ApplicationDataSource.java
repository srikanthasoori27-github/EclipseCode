/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.ApplicationScorecard;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.ApplicationActivity.Action;
import sailpoint.object.ApplicationActivity.Result;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class ApplicationDataSource extends SailPointDataSource<Application> {

	List<Filter> subReportFilters;

	/** A map containing each identity that links to this application. **/
	List<Map> applicationUsers;
	List<Map> applicationUsersMultipleLinks;

	protected static final String IDENTITY_ALIAS = "identityAlias";
	protected static final String LINK_ALIAS = "identity_linksAlias0";
	protected static final String LINK_ALIAS2 = "links2";


	private static final String IDENTITY_MULTIPLE_ACCOUNT_QUERY = 
		"select distinct "+IDENTITY_ALIAS+".id " +
		"from " +
		"       sailpoint.object.Identity "+IDENTITY_ALIAS +
		"       where " +
		"               (select count(*) from sailpoint.object.Link "+LINK_ALIAS2+
		"                   where "+LINK_ALIAS2+".identity.id = "+IDENTITY_ALIAS+".id " +
		"                   and "+LINK_ALIAS2+".application.id = (:applicationId)) > 1";

	private static final Log log = 
		LogFactory.getLog(ApplicationDataSource.class);

    public ApplicationDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
		super(filters, locale, timezone);
		setScope(Application.class);
	}

	public ApplicationDataSource(List<Filter> filters, List<Filter> subFilters, Locale locale, TimeZone timezone) {
		super(filters, locale, timezone);
		subReportFilters = subFilters;
	}

	@Override
	public void internalPrepare() throws GeneralException {
		qo.setOrderBy("name");
		_objects = getContext().search(Application.class, qo);
	}

	/* (non-Javadoc)
	 * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
	 */
	public Object getFieldValue(JRField jrField) throws JRException {

		String fieldName = jrField.getName();
		Object value = null;
		try {
			if(fieldName.equals("ownerShort")) {
				Identity owner = _object.getOwner();
				if(owner!=null) {
					String ownerName = new String(owner.getName());
					value = ownerName;
				}
			} else if(fieldName.startsWith("score.")) { 
				ApplicationScorecard scorecard = _object.getScorecard();
				if(scorecard!=null) {
					value = scorecard.getScore(fieldName.substring(("score.").length()));
				}				
			} else if(fieldName.equals("ownerLong")) {
				Identity owner = _object.getOwner();
				if(owner!=null) {
					String ownerName = owner.getDisplayName()
							+ " (" + owner.getName() + ")";
					value = ownerName;
				}
			} else if(fieldName.equals("secondaryOwners")) {
				List<Map<String,Object>> secondaryOwners = new ArrayList<Map<String, Object>>();
				List<Identity> owners = _object.getSecondaryOwners();
				if(owners!=null) {
					for(Identity owner : owners) {
						Map<String,Object> ownerName = new HashMap<String, Object>();
						ownerName.put("name", new String(owner.getFirstname() + " " + owner.getLastname() 
								+ " (" + owner.getName() + ")"));
						secondaryOwners.add(ownerName);
					}
				}
				if(secondaryOwners.isEmpty()) {
					Map<String,Object> ownerName = new HashMap<String, Object>();
					ownerName.put("name", getMessage(MessageKeys.REPT_APP_NO_SECONDARY_OWNERS_FOR_APP));
					secondaryOwners.add(ownerName);
				}
				value = secondaryOwners;
			}else if(fieldName.equals("secondaryOwnersShort")) {
				List<Map<String,Object>> secondaryOwners = new ArrayList<Map<String, Object>>();
				List<Identity> owners = _object.getSecondaryOwners();
				if(owners!=null) {
					for(Identity owner : owners) {
						Map<String,Object> ownerName = new HashMap<String, Object>();
						ownerName.put("name", owner.getName());
						secondaryOwners.add(ownerName);
					}
				}
				if(secondaryOwners.isEmpty()) {
					Map<String,Object> ownerName = new HashMap<String, Object>();
					ownerName.put("name", getMessage(MessageKeys.REPT_APP_NO_SECONDARY_OWNERS));
					secondaryOwners.add(ownerName);
				}
				value = secondaryOwners;
			} else if(fieldName.equals("APP_USERS_HEADER_PRINTED")) { 
                value = _headerPrinted;
                /** Only set the header printed to true if the supreport will have entries **/
                if(!_headerPrinted && (getApplicationUsers()==null || getApplicationUsers().isEmpty())) {
                    
                } else {
                    _headerPrinted=true;
                }
            
			} else if(fieldName.equals("applicationUsers")) {
				value = getApplicationUsers();                

			} else if (fieldName.equals("applicationSchema")) {
				List<Map> applicationSchemaList = null;
				List<Schema> schemas = _object.getSchemas();
				if(schemas!=null) {
					applicationSchemaList = new ArrayList<Map>();
					for(Schema schema : schemas) {
						Map<String, Object> schemaMap = new HashMap<String, Object>();
						schemaMap.put("native_object_type", schema.getNativeObjectType());
						schemaMap.put("identity_attribute", schema.getIdentityAttribute());
						schemaMap.put("display_attribute", schema.getDisplayAttribute());
						applicationSchemaList.add(schemaMap);
					}
				}
				value = applicationSchemaList;
			} else if(fieldName.equals("applicationActivity")) {
				List<Map> activityMapList = null;

				QueryOptions ops = new QueryOptions();
				ops.add(Filter.eq("sourceApplication", _object.getName()));
				//Use the passed in sub report filters for filtering the application activity objects
				if(subReportFilters!=null) {
					for(Filter filter : subReportFilters) {
						ops.add(filter);
					}
				}

				List<String> props = new ArrayList<String>();            
				props.add("timeStamp");
				props.add("identityName");
				props.add("target");
				props.add("action");
				props.add("result");

				Iterator<Object[]> it = getContext().search(ApplicationActivity.class, ops, props);
				if(it.hasNext()){
					activityMapList = new ArrayList<Map>();
					while(it.hasNext()) {
						Object[] row = it.next();
						Map<String, Object> activityMap = new HashMap<String, Object>();
						activityMap.put("timeStamp", (java.util.Date)row[0]);
						activityMap.put("identityName", (String)row[1]);
						activityMap.put("target", (String)row[2]);
						activityMap.put("action", ((Action)row[3]).name());
						activityMap.put("result", ((Result)row[4]).name());
						activityMapList.add(activityMap);
					}
				}                
				value = activityMapList;
			}
			/** This is where things get interesting, we want to provide information on 
			 * the identities that are linked to this app so that people can trust
			 * that we loaded their systems correctly. PH
			 */

			/** This returns a list of maps of users who only have a single link
			 * and that link is to this application 
			 */
			else if(fieldName.equals("applicationUsersMultipleLinks")) {
				if(applicationUsersMultipleLinks==null) {
					applicationUsersMultipleLinks = new ArrayList<Map>();

					/** get the identities with multiple links to this app **/
					Map<String,Object> queryArgs = new HashMap<String,Object>();
					queryArgs.put("applicationId", _object.getId());

					Iterator it = getContext().search(IDENTITY_MULTIPLE_ACCOUNT_QUERY, queryArgs, null);
					while (it.hasNext()) {
						Object o = it.next();
						String id = (String)o;

						Identity identity = getContext().getObjectById(Identity.class, id);
						if(identity!=null) {
							for(Link link : identity.getLinks()) {
								if(link.getApplication().getId().equals(_object.getId())) {
									Map<String, Object> userMap = new HashMap<String, Object>();
									userMap.put("account", link.getNativeIdentity());
									userMap.put("identity", identity.getName());
									userMap.put("identityFullName", identity.getDisplayableName());
									userMap.put("id", id);
									applicationUsersMultipleLinks.add(userMap);
								}
							}
						}
						getContext().decache(identity);
					}
				}
				value = applicationUsersMultipleLinks;
			}
		} catch (GeneralException ge){
			log.error("Exception thrown while getting value for JRField [" + jrField.getName() + "]. " 
					+ "Exception [" + ge.getMessage() + "].");
			value = null;
		}

		if(value==null)
			value = super.getFieldValue(jrField);
		if(fieldName.equals("description") && value!=null && value instanceof String) {
		    value = WebUtil.stripHTML((String)value);
		}
		return value;

	}

	public boolean internalNext() throws JRException {
		boolean hasMore = false;
		if ( _objects != null ) {
			hasMore = _objects.hasNext();
			if ( hasMore ) {
				_object = _objects.next();
				applicationUsers = null;
				applicationUsersMultipleLinks = null;
			} else {
				_object = null;
			}

			if ( _object != null ) {
				updateProgress("Application", _object.getName());
			}
		}
		//log.debug("Getting Next: " + hasMore);
		return hasMore;
	}

	public List<Map> getApplicationUsers() throws GeneralException{
		if(applicationUsers==null) {
			QueryOptions ops = new QueryOptions();
			ops.add(Filter.eq("application.id", _object.getId()));
            ops.addOrdering("identity.name", true);
			List<String> props = new ArrayList<String>();
			props.add("nativeIdentity");
			props.add("identity.name");
			props.add("identity.id");
			props.add("identity.attributes");

			Iterator<Object[]> it = getContext().search(Link.class, ops, props);
			if(it.hasNext()){
				applicationUsers = new ArrayList<Map>();
				while(it.hasNext()) {
					Object[] row = it.next();
					Map<String, Object> userMap = new HashMap<String, Object>();
					userMap.put("account", (String)row[0]);
					userMap.put("identity", (String)row[1]);
					userMap.put("id", (String)row[2]);

					String fullName = null;
					if (row[3] != null){
						Attributes identityAttrs = (Attributes)row[3];
						String fName = identityAttrs.getString(Identity.ATT_FIRSTNAME);
						String lName = identityAttrs.getString(Identity.ATT_LASTNAME);

						fullName = fName;
						if (lName != null && lName.length() > 0)
							fullName = fName + " " + lName; 

					}

					userMap.put("identityFullName", fullName);

					applicationUsers.add(userMap);
				}
			}
		}
		return applicationUsers;
	}

}
