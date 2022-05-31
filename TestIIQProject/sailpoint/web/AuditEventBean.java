/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import sailpoint.object.Attributes;
import sailpoint.object.AuditConfig;
import sailpoint.object.AuditEvent;
import sailpoint.object.Identity;
import sailpoint.object.AuditConfig.AuditAction;
import sailpoint.tools.GeneralException;

/**
 * This is a simple bean that does nothing more than give 
 * access to the values of an AuditEvent object.
 * 
 * @author derry.cannon
 *
 */
public class AuditEventBean extends BaseEditBean<AuditEvent> {
    
    private Date created;
    
    private String source;

    private String clientHost;

    private String serverHost;
    
    private String action;
    
    private String target;
    
    private String application;
    
    private String instance;
    
    private String accountName;
    
    private String attributeName;

    private String attributeValue;
    
    private String string1;
    
    private String string2;
    
    private String string3;
    
    private String string4;
    
    private String attributes;
    
    /**
     * The full Identity object for the source (assuming the source
     * is, in fact, an Identity and not a process) 
     */
    private String sourceDisplayName;

    /**
     * The full Identity object for the target (assuming the target
     * is, in fact, an Identity and not a process) 
     */
    private String targetDisplayName;

    
    public Date getCreated() throws GeneralException {
        return getObject().getCreated();
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    /**
     * Finds the Identity object associated with the source and 
     * returns its display name.  If no Identity is found, 
     * returns the raw source.
     * 
     * @return
     * @throws GeneralException
     */
    public String getSource() throws GeneralException {
        if (sourceDisplayName == null)
            sourceDisplayName = getDisplayName(getObject().getSource());
        
        return sourceDisplayName;
    }

    /**
     * Find the Identity with the given idName.
     * 
     * @return The display name of the Identity if found; the idName otherwise
     * @throws GeneralException
     */
    private String getDisplayName(String idName) throws GeneralException {
        Identity identity = getContext().getObjectByName(Identity.class, idName);
        
        if (identity != null)
            return identity.getDisplayName();
        else
            return idName;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getClientHost() throws GeneralException {
        return getObject().getClientHost();
    }

    public void setClientHost(String host) {
        // jsl - what is the purpose of even having setters here
        // we're not editing anything
        clientHost = host;
    }

    public String getServerHost() throws GeneralException {
        return getObject().getServerHost();
    }

    public void setServerHost(String host) {
        serverHost = host;
    }

    /**
     * Pulls the action's displayable name from the AuditConfig 
     * 
     * @return The displayable name of the given audit action
     * 
     * @throws GeneralException
     */
    public String getAction() throws GeneralException {
        String displayName = getObject().getAction();

        AuditConfig auditConfig = AuditConfig.getAuditConfig();

        AuditAction ae = auditConfig.getAuditAction(getObject().getAction());
        if (ae != null)
            displayName = ae.getDisplayableName();
        
        return displayName;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTarget() throws GeneralException {
        if (targetDisplayName == null)
            targetDisplayName = getDisplayName(getObject().getTarget());
        
        return targetDisplayName;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getApplication() throws GeneralException {
        return getObject().getApplication();
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getInstance() throws GeneralException {
        return getObject().getInstance();
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getAccountName() throws GeneralException {
        return getObject().getAccountName();
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getAttributeName() throws GeneralException {
        return getObject().getAttributeName();
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeValue() throws GeneralException {
        return getObject().getAttributeValue();
    }

    public void setAttributeValue(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    public String getString1() throws GeneralException {
        return getObject().getString1();
    }

    public void setString1(String string1) {
        this.string1 = string1;
    }

    public String getString2() throws GeneralException {
        return getObject().getString2();
    }

    public void setString2(String string2) {
        this.string2 = string2;
    }

    public String getString3() throws GeneralException {
        return getObject().getString3();
    }

    public void setString3(String string3) {
        this.string3 = string3;
    }

    public String getString4() throws GeneralException {
        return getObject().getString4();
    }

    public void setString4(String string4) {
        this.string4 = string4;
    }

    public Attributes<String, Object> getAttributes() throws GeneralException {
        return getObject().getAttributes();
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }
        
    public List<String> getAttributeKeys() throws GeneralException {
        List<String> list = new ArrayList<String>();
        if (getObject().getAttributes() != null) {
            for (String key : getObject().getAttributes().keySet()) {
                list.add(key);
            }     
            
            Collections.sort(list);
        }
            
        return list;
    }
    
    @Override
    protected Class<AuditEvent> getScope() {
        return AuditEvent.class;
    }

    @Override
    public boolean isStoredOnSession() {
        return false;
    }    
}
