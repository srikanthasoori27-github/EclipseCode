/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A helper object that sits between a JSF page and an Identity to provide
 * a uniform Map interface for all Identity properties.
 * Author: Jeff
 */
package sailpoint.web.identity;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SailPointObject;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

/**
 * A helper object that sits between the JSF page and the Idenity
 * returned by the search.  The main thing this provides is way
 * to consistently access both extensible Identity attributes and
 * some of the first class Java fields as if they were all swimming
 * in the same Map.  Without this the JSF page would have to 
 * have a very complicated set of conditional fields, some of which
 * use Identity property paths (e.g. identity.manager.name) and
 * others which use attribute paths (e.g. identity.attributes['jobcode']).
 * 
 * Hmm, making a Java object look like a Map.  Where have I seen 
 * that before...
 *
 * While we're not actually hosting any data in this class, having
 * it opens up the door toward just maintaing our own model
 * for the rows in the result set.  The only reason we're proxying
 * to an Identity is because Hibernate is creating them to return
 * as the search result.  
 *
 * UPDATE: When using a Project to request only certain attributes,
 * we will get back a List<Object[]> instead of List<Identity>. 
 * When using that model, need to have both the Object[] and 
 * the List<String> that identities the names and order of the 
 * properties that were requested.
 */
public class IdentityProxy extends java.util.AbstractMap<String,Object> {
    private static final Log log = LogFactory.getLog(IdentityProxy.class);

    Identity _identity;
    Object[] _row;
    List<String> _columns;
    private TimeZone _timeZone;
    private Locale _locale;

    public IdentityProxy(Identity id, TimeZone timeZone, Locale locale) {
        _identity = id;
        _timeZone = timeZone;
        _locale = locale;
    }

    public IdentityProxy(Object[] row, List<String> cols, TimeZone timeZone, Locale locale) {
        _row = row;
        _columns = cols;
        _timeZone = timeZone;   
        _locale = locale;
    }
    
    /**
     * Convenience method which formats the date using the standard format used on web
     * pages. Uses instance locale and timeZone values. 
     * 
     * @param date Date to format
     * @return Formatted date
     */	
    private String dateToString(Date date){
    	return Util.dateToString(date, DateFormat.SHORT, DateFormat.SHORT, _timeZone, _locale);
    }

    /**
     * Required by AbstractMap, but we don't allow iteration.
     * !! Turning on JSF tracing will try to use this, need to 
     * generate a real entry set.
     */
    public Set<Map.Entry<String,Object>> entrySet() {
        return new HashSet<Map.Entry<String,Object>>();
    }
    
    public Identity getIdentity() {
        return _identity;
    }

    public Object get(Object key) {

        Object value = null;
        if (_identity != null)
            value = get(_identity, key);

        else if (_row != null && _columns != null) {

            // pseudo properties
            if (key.equals("bundles")) {
            	/* Justification: known to return List<Bundle> */
            	@SuppressWarnings( "unchecked" )
                List<Bundle> bundles = (List<Bundle>)getCell(key);
                if (bundles != null) {
                    StringBuffer b = new StringBuffer();
                    for (Bundle bundle : bundles) {
                        if (b.length() > 0)
                            b.append(",");
                        b.append(bundle.getName());
                    }
                    value = b.toString();
                }
            }
            else {
                Object cell = getCell(key);

                if (cell instanceof Date)
                    value = dateToString((Date)cell);
                else if (cell instanceof SailPointObject)
                    value = ((SailPointObject)cell).getName();
                else if (cell != null)
                    value = cell.toString();
            }
        }

        return value;
    }

    /**
     * Retrieve the value of a column by name.
     * TODO: Since this is going to happen all the time, should
     * be creating a String->Integer map to cache the index.
     */
    private Object getCell(Object key) {

        Object value = null;
        int index = _columns.indexOf(key);

        if (index >= 0 && index < _row.length)
            value = _row[index];

        return value;
    }

    /**
     * Retrieve a property from an Identity, with various
     * value transformations.
     * 
     * @param identity the identity to get the value for
     * @param key the key of the desired attribute
     * 
     * @return String or a List of Strings
     */
    public static Object get(Identity identity, Object key) {
        return get(identity, key, false);
    }
    /**
     * Retrieve a property from an Identity, with various
     * value transformations.
     * 
     * @param identity the identity to get the value for
     * @param key the key of the desired attribute
     * @param getDisplayName whether to use display name or user name for identities in extended attributes
     * 
     * @return String or a List of Strings
     */
    public static Object get(Identity identity, Object key, boolean getDisplayName) {

        Object value = null;

        if (key == null) {
            // can happen in theory
        }
        else if (key.equals("id"))
            value = identity.getId();

        else if (key.equals("name") || key.equals("userName")){
            value = identity.getName();
        }
        else if(key.equals("bundleSummary") || key.equals("businessRoles")) {
            value = identity.getBundleSummary();
        }
        else if (((String)key).toLowerCase().equals("lastname")) {
            value= identity.getLastname();
        }
        else if (((String)key).toLowerCase().equals("firstname")) {
            value = identity.getFirstname();
        }
        else if (key.equals("activityMonitoring")) {
            boolean identityMonitoring = (identity.getActivityConfig()!=null);
            if(identityMonitoring)
                value = new String("True");
            else {
                List<Bundle> bundles = identity.getBundles();
                if(bundles!=null) {
                    for(Bundle bundle : bundles) {
                        boolean bundleMonitoring = (bundle.getActivityConfig()!=null);
                        if(bundleMonitoring)
                            return ("True");
                    }
                }
                value = new String("False");
            }
        }
        else if (key.equals("created")) {
            if(identity.getCreated()!=null)
                value = Util.dateToString(identity.getCreated());
        }
        else if (key.equals("modified")) {
            value = Util.dateToString(identity.getModified());
        }
        else if (key.equals("lastLogin")) {
        	if( identity.getLastLogin() != null ) {
        		value = Util.dateToString( identity.getLastLogin() );
        	}
        }
        else if (key.equals("manager")) {
            Identity mid = identity.getManager();
            if (mid != null)
                value = mid.getDisplayableName();
        }
        else if (key.equals("managerId")) {
            Identity mid = identity.getManager();
            if (mid != null)
                value = mid.getId();
        }
        else if (key.equals("managerStatus")) {
            value = ((identity.getManagerStatus()) ? "true" : "false");
        }
        else if (key.equals("lastRefresh")) {
            // This is a pseudo property that will have the value
            // of the most recent aggregation refresh. 
            Date last = null;
            List<Link> links = identity.getLinks();
            if (links != null) {
                for (Link link : links) {
                    Date d = link.getLastRefresh();
                    if (d != null && (last == null || d.after(last)))
                        last = d;
                }
            }
            if (last != null)
                value = Util.dateToString(last);
        }
        else if (key.equals("bundles") || key.equals("businessRole")) {
            // a summary of the assigned business roles
            // TODO: Will want to constrain the length
            // and add elipses
            List<Bundle> bundles = identity.getBundles();
            Collections.sort(bundles, new Comparator<Bundle>() {
                public int compare(Bundle a, Bundle b) {                        
                    return a.getName().compareToIgnoreCase(b.getName());                                                  
                }}  
            );
            if (bundles != null) {
                StringBuffer b = new StringBuffer();
                for (Bundle bundle : bundles) {
                    if (b.length() > 0)
                        b.append(", ");
                    if(bundle!=null)
                        b.append(bundle.getDisplayableName());
                }
                value = b.toString();
            }
        }
        else if (key.equals("applications") || key.equals("application")) {
            // a summary of the application
            // TODO: Will want to constrain the length
            // and add elipses
            List<Link> links = identity.getLinks();
            Collections.sort(links, new Comparator<Link>() {
                public int compare(Link a, Link b) {
                    if (a!=null & b!=null) {
                        Application appA = a.getApplication();
                        Application appB = b.getApplication();
                        if(appA!=null && appB!=null)
                            return appA.getName().compareToIgnoreCase(appB.getName());  
                    }
                    return 0; 
                }}
            );
            if(links!=null) {
                StringBuffer b = new StringBuffer();
                for(Link link : links) {
                    if(link!=null) {
                        Application app = link.getApplication();
                        if (b.length() > 0)
                            b.append(", ");
                        if(app!=null)
                            b.append(app.getName());
                    }
                }
                value = b.toString();
            }
        }
        else if(key.equals("compositeScore") && identity.getScorecard()!=null)
            value = Integer.toString(identity.getScorecard().getCompositeScore());
        else if(key.equals("businessRoleScore") && identity.getScorecard()!=null)
            value = Integer.toString(identity.getScorecard().getBusinessRoleScore());
        else if(key.equals("businessRoleRawScore") && identity.getScorecard()!=null)
            value = Integer.toString(identity.getScorecard().getRawBusinessRoleScore());
        else if(key.equals("entitlementScore") && identity.getScorecard()!=null)
            value = Integer.toString(identity.getScorecard().getEntitlementScore());
        else if(key.equals("entitlementRawScore") && identity.getScorecard()!=null)
            value = Integer.toString(identity.getScorecard().getRawEntitlementScore());
        else if(key.equals("policyScore") && identity.getScorecard()!=null)
            value = Integer.toString(identity.getScorecard().getPolicyScore());
        else if(key.equals("certificationScore") && identity.getScorecard()!=null)
            value = Integer.toString(identity.getScorecard().getCertificationScore());
        else if (key.equals(Identity.ATT_TYPE)) {
            IdentityTypeDefinition typeDef = identity.getIdentityTypeDefinition();
            value = typeDef == null ? null : WebUtil.localizeMessage(typeDef.getDisplayName());
        } else if (key.equals(Identity.ATT_ADMINISTRATOR)) {
            Identity administrator = identity.getAdministrator();
            if (administrator != null) {
                if (getDisplayName) {
                    value = administrator.getDisplayableName();
                } else {
                    value = administrator.getName();
                }
            }
        } else {
            
            ObjectConfig config = Identity.getObjectConfig();
            ObjectAttribute attribute = config.getObjectAttribute(key.toString());
            if (attribute != null) {
                if (ObjectAttribute.TYPE_IDENTITY.equals(attribute.getType()) && attribute.getExtendedNumber() > 0) {
                    Identity otherIdentity = identity.getExtendedIdentity(attribute.getExtendedNumber());
                    if (otherIdentity != null) {
                        if (getDisplayName){
                            value = otherIdentity.getDisplayableName();
                        } else {
                            value = otherIdentity.getName();
                        }
                    }
                } else {
                    // anything else is assumed to be extensible
                    Attributes<String, Object> atts = identity.getAttributes();
                    if (atts != null) {
                        Object o = atts.get(key);
                        if (o != null) {
                            if (o instanceof List<?>)
                                value = o;
                            else
                                value = o.toString();
                        }
                    }
                }
            }
            
        }

        return value;
    }
}

