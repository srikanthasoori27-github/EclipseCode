/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


@XMLClass
public class CertificationSnapshot extends SailPointObject {
    
    private Certification.Type type;
    private List<String> certifiers;
    private List<String> totalEntities;
    private List<String> completedEntities;
    private List<String> delegatedEntities;
    
    
    public CertificationSnapshot() {}


    public CertificationSnapshot(List<String> certifiers) 
    {
        this.certifiers = certifiers;
    }
    
    public CertificationSnapshot(String certifier) 
    {
        certifiers = new ArrayList<String>();
        certifiers.add(certifier);
    }

    /**
     * Only persisted as XML.
     */
    @Override
    public boolean isXml() {
        return true;
    }
    
    /**
     * @return the certifier
     */
    @XMLProperty
    public List<String> getCertifiers() {
        return certifiers;
    }


    /**
     * @param certifiers the certifiers to set
     */
    public void setCertifiers(List<String> certifiers) {
        this.certifiers = certifiers;
    }


   

    /**
     * @return the completedEntities
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getCompletedEntities() {
        return completedEntities;
    }


    /**
     * @param completedEntities the completedEntities to set
     */
    public void setCompletedEntities(List<String> completedEntities) {
        this.completedEntities = completedEntities;
    }


    /**
     * @return the delegatedEntities
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getDelegatedEntities() {
        return delegatedEntities;
    }


    /**
     * @param delegatedEntities the delegatedEntities to set
     */
    public void setDelegatedEntities(List<String> delegatedEntities) {
        this.delegatedEntities = delegatedEntities;
    }


    /**
     * @return the totalEntities
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getTotalEntities() {
        return totalEntities;
    }


    /**
     * @param totalEntities the totalEntities to set
     */
    public void setTotalEntities(List<String> totalEntities) {
        this.totalEntities = totalEntities;
    }


    /**
     * @return the type
     */
    @XMLProperty
    public Certification.Type getType() {
        return type;
    }


    /**
     * @param type the type to set
     */
    public void setType(Certification.Type type) {
        this.type = type;
    }

}
