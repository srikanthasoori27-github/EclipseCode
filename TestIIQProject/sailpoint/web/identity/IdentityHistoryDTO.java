/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Backing bean for the IdentitySnapshot view page.
 */
package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.object.Attributes;
import sailpoint.object.BundleSnapshot;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.LinkSnapshot;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleAssignmentSnapshot;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseDTO;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.system.ObjectAttributeDTO;

/**
 * Backing DTO for the Identity History Page. 
 * This is different from the IdentityHistoryBean which deals with certification history.
 * This deals with SNAPSHOTS.
 * Maybe this should be called IdentitySnapshotDTO or something. 
 */
public class IdentityHistoryDTO extends BaseDTO {

    private static final long serialVersionUID = 1L;
    public static final String HISTORY_ID = "editForm:historyId";
    public static final String NAV_STRING_IDENTITY = "identity";

    /**
     * Cached display attributes.
     */
    private List<ObjectAttributeDTO> attributes;
    /**
     * Derived value set to true if the identity has links
     * that include template instance identifiers.  Used to conditionalize
     * the display of an instance column since not all identities will have them.
     */
    private Boolean linkInstances;

    private String snapshotId;
    private IdentitySnapshot snapshot;
    @SuppressWarnings("unchecked")
    private BaseObjectBean pageDelegate; 
    
    @SuppressWarnings("unchecked")
    public IdentityHistoryDTO()
        throws GeneralException {

        super();
        
        String id = (String)getSessionScope().get(HISTORY_ID);
        if (id == null) {
            throw new IllegalStateException();
        }
        
        this.snapshotId = id;
        this.snapshot = getContext().getObjectById(IdentitySnapshot.class, this.snapshotId);
        
        this.pageDelegate = new BaseObjectBean();
    }

    public String getIdentityName() {
        return this.snapshot.getIdentityName();
    }
    
    public Date getCreated() {
        return this.snapshot.getCreated();
    }
    
    public Attributes<String, Object> getSnapshotAttributes() {
        return this.snapshot.getAttributes();
    }
    
    public List<BundleSnapshot> getBundles() {
        return this.snapshot.getBundles();
    }
    
    public List<RoleAssignmentSnapshot> getAssignedRoles() {
        return this.snapshot.getAssignedRoles();
    }
    
    public List<LinkSnapshot> getLinks() {
        return this.snapshot.getLinks();
    }
    
    public List<EntitlementSnapshot> getExceptions() {
        return this.snapshot.getExceptions();
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
    public List<ObjectAttributeDTO> getAttributes() 
        throws GeneralException {
        
        if (this.attributes == null) {
            this.attributes = new ArrayList<ObjectAttributeDTO>();
            ObjectConfig idConfig = this.pageDelegate.getIdentityConfig();
            IdentitySnapshot snapshot = this.snapshot;
            if (snapshot != null && idConfig != null) {
                Attributes<String,Object> atts = snapshot.getAttributes();
                if (atts != null) {
                    for (String name : atts.keySet()) {
                        ObjectAttribute ida = idConfig.getObjectAttribute(name);
                        ObjectAttributeDTO dto;
                        if (ida == null) {
                            dto = new ObjectAttributeDTO(ObjectConfig.IDENTITY);
                            dto.setName(name);
                            dto.setDisplayName(name);
                        } else {
                            dto = new ObjectAttributeDTO(ObjectConfig.IDENTITY, ida);
                        }
                        this.attributes.add(dto);
                    }
                }
            }
        }
        return this.attributes;
    }

    /**
     * A derived property telling the link page to 
     * display a column for application template instance identifiers.
     */
    public boolean isLinkInstances() throws GeneralException {

        if (this.linkInstances == null) {
            this.linkInstances = new Boolean(false);
            IdentitySnapshot ident = this.snapshot;
            List<LinkSnapshot> links = ident.getLinks();
            if (links != null) {
                for (LinkSnapshot link : links) {
                    if (link.getInstance() != null) {
                        this.linkInstances = new Boolean(true);
                        break;
                    }
                }
            }
        }
        return this.linkInstances.booleanValue();
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
        
        getSessionScope().put(IdentityDTO.VIEWABLE_IDENTITY, this.snapshot.getIdentityId());//granting view permission here
        
        return IdentityDTO.createNavString(NAV_STRING_IDENTITY, snapshot.getIdentityId());
    }

}



