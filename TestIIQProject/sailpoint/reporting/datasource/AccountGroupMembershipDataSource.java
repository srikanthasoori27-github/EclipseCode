/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.AccountGroupService;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * AccountGroupMembershipDataSource which is used by the AccountGroupReport.
 *
 * There is one switch that will filter groups that have members so we can report 
 * which groups don't have any members.
 * 
 */
public class AccountGroupMembershipDataSource extends SailPointDataSource<ManagedAttribute> {

    private static final Log log = LogFactory.getLog(RemediationDataSource.class);

    /** Membes for the current _object */
    List<String> _members;

    /** Report args, so we can check the "mode"Membes for the current _object */
    Attributes<String,Object> _args;

    /** flag to indicate if we are returning only groups with no members */
    boolean _onlyGroupsWithoutMembers;

    /** service to get us the members, etc */
    AccountGroupService _acctGroupService;

    /** name of the attribute to display for each member. Default to identityName, but can
        support ANY of the links attributes. */
    String _memberAttribute;

    public AccountGroupMembershipDataSource(List<Filter> filters, Locale locale, TimeZone timezone, Attributes<String,Object> args) {
        super(filters, locale, timezone);
        setScope(ManagedAttribute.class);
        _args = args;
        if ( _args == null ) 
            _args = new Attributes<String,Object>();

    }

    @Override
    public void internalPrepare() throws GeneralException {
        _acctGroupService = new AccountGroupService(getContext());

        _onlyGroupsWithoutMembers = false;
        _memberAttribute = _args.getString("memberAttribute");
        if  ( _memberAttribute == null ) 
            _memberAttribute = "identityName";

        String mode = _args.getString("executionMode");
        if ( ( mode != null ) && ( "noMembers".compareTo(mode) == 0 ) ) {
            _onlyGroupsWithoutMembers = true;
        }

        updateProgress("Querying for Account Groups...");
        qo.setOrderBy("value");
        _objects = getContext().search(ManagedAttribute.class, qo);
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    public Object getFieldValue(JRField jrField) throws JRException {

        String fieldName = jrField.getName();

        Object value = null;
        if (fieldName.equals("name")) {
            value = _object.getDisplayableName();
        } else 
        if (fieldName.equals("accountGroupMembersMapList")) {
           if ( log.isDebugEnabled() ) {
               if ( _members == null ) {
                   log.debug("Returned" + XMLObjectFactory.getInstance().toXml(_members));
               } else {
                   log.debug("Returned" + XMLObjectFactory.getInstance().toXml(_members));
               }
           }
           value = mapifyList(_members);
        }
        return value;

    }

    private List<Map> mapifyList(List<String> members) {
        List<Map> maps = new ArrayList<Map>();
        if ( ( members != null ) && ( members.size() > 0 ) ) {
            for ( String member : members ) {
                Map<String, String> map = new HashMap<String,String>();
                map.put("memberName", member);
                maps.add(map);
            }
        }
        return maps;
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#next()
     */
    public boolean internalNext() throws JRException {
        boolean hasMore = false;
        if ( _objects != null ) {
            _object = null;
            hasMore = _objects.hasNext();
            if ( hasMore ) {
                _object = _objects.next();
            }

            while ( ( _object != null ) && ( filter(_object) ) ) {
                _members = null;
                hasMore = _objects.hasNext();
                if ( hasMore ) {
                    _object = _objects.next();
                } else {
                    _object = null;
                }
            }

            if ( _object != null ) {
                updateProgress("AccountGroup", _object.getName());
            }
            
            if ( _object == null ) hasMore = false;
        }
        return hasMore;
    }

    private boolean filter(ManagedAttribute group) 
        throws JRException {

        boolean filter = false;
        try {
            if ( group != null ) {
                _members = _acctGroupService.getMemberNames(group, _memberAttribute);
            }
            if ( _onlyGroupsWithoutMembers ) {
                if ( ( _members != null ) && ( _members.size() > 0 ) ) {
                    return true;
                }
            }
        } catch(Exception e ){
            throw new JRException(e);
        }
        return filter;
    }
}
