/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Backing bean for the IdentitySnapshot view page.
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Attributes;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.LinkSnapshot;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;
import sailpoint.web.identity.IdentityDTO;

/**
 * Backing bean for the Identity view page.
 * @deprecated This has been replaced by IdentityHistoryDTO
 */
public class HistoryBean extends BaseObjectBean<IdentitySnapshot>
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Cached display attributes.
     */
    List<ObjectAttribute> _attributes;
    public static final String HISTORY_ID = "editForm:historyId";

    /**
     * Derived value set to true if the identity has links
     * that include template instance identifiers.  Used to conditionalize
     * the display of an instance column since not all identities will have them.
     */
    Boolean _linkInstances;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public HistoryBean()
    {
        super();
        setScope(IdentitySnapshot.class);
        
        String id = (String)getSessionScope().get(HISTORY_ID);
        setObjectId(id);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Derive the list of attributes to display for this object.
     * Currently showing everything in the snapshot but could
     * have a filter list in UIConfig.
     *
     * Hmm, a consequence of doing it this way is that the attributes
     * come out in random order.  Would be better to have a UIConfig list.
     */
    public List<ObjectAttribute> getAttributes() throws GeneralException {
        if (_attributes == null) {
            _attributes = new ArrayList<ObjectAttribute>();
            ObjectConfig idConfig = getIdentityConfig();
            IdentitySnapshot id = getObject();
            if (id != null && idConfig != null) {
                Attributes<String,Object> atts = id.getAttributes();
                if (atts != null) {
                    for (String name : atts.keySet()) {
                        ObjectAttribute ida = idConfig.getObjectAttribute(name);
                        if (ida == null) {
                            // fake one up for display
                            ida = new ObjectAttribute();
                            ida.setName(name);
                            ida.setDisplayName(name);
                        }
                        _attributes.add(ida);
                    }
                }
            }
        }
        return _attributes;
    }

    /**
     * A derived property telling the link page to 
     * display a column for application template instance identifiers.
     */
    public boolean isLinkInstances() throws GeneralException {
        if (_linkInstances == null) {
            _linkInstances = new Boolean(false);
            IdentitySnapshot ident = getObject();
            List<LinkSnapshot> links = ident.getLinks();
            if (links != null) {
                for (LinkSnapshot link : links) {
                    if (link.getInstance() != null) {
                        _linkInstances = new Boolean(true);
                        break;
                    }
                }
            }
        }
        return _linkInstances.booleanValue();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Anything special we want to add here?  
     */
    public String okAction() throws GeneralException {
        getSessionScope().put(IdentityDTO.VIEWABLE_IDENTITY, getObject().getIdentityId());
        return "identity";
    }

}



