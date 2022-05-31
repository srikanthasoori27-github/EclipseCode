/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */

package sailpoint.object;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * ApplicationEntitlementWeights holds a collection of EntitlementWeights
 * for the specified application.
 */
@XMLClass
public class ApplicationEntitlementWeights implements Cloneable, Serializable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static final long serialVersionUID = 3222977946063243656L;

    /**
     * The application
     */ 
    private Application _application;

    /**
     * The default account risk.  This will be added to the identity
     * score simply by having the account.
     */
    private int _accountWeight;

    /**
     * Fine grained entitlement weights.
     */
    private List<EntitlementWeight> _weights;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public ApplicationEntitlementWeights() {
        _application = null;
        _weights = null;
    }

    public ApplicationEntitlementWeights(String appNameOrId, List<EntitlementWeight> weights, Resolver r) throws GeneralException{
        _application = r.getObjectByName(Application.class, appNameOrId);
        _weights = weights;
    }

    public ApplicationEntitlementWeights(Application application, List<EntitlementWeight> weights) {
        _application = application; 
        _weights = weights;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public void setApplication(Application app) {
        _application = app;
    }

    @XMLProperty (mode=SerializationMode.REFERENCE, xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    @XMLProperty
    public void setAccountWeight(int i) {
        _accountWeight = i;
    }

    /**
     * The default account risk.  This will be added to the identity
     * score by having the account.
     */
    public int getAccountWeight() {
        return _accountWeight;
    }
    
    public void setWeights(List<EntitlementWeight> weights) {
        _weights = weights;
    }

    /**
     * Fine grained entitlement weights.
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<EntitlementWeight> getWeights() {
        return _weights;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Helper methods
    //
    //////////////////////////////////////////////////////////////////////
    
    public boolean isEmpty() {
        return (((null == _weights) || _weights.isEmpty()) && _accountWeight == 0);
    }

    public void addWeight(EntitlementWeight weight) {
        if (_weights == null) {
            _weights = new ArrayList<EntitlementWeight>();
        }
        
        _weights.add(weight);
    }
    
    public void addWeights(Collection<EntitlementWeight> weights) {
        _weights.addAll(weights);
    }
    
    public void clearWeights() {
        if (_weights != null) {
            _weights.clear();
        }
    }
    
    @Override
    public Object clone() {
        ApplicationEntitlementWeights copy = new ApplicationEntitlementWeights(_application, _weights);
        copy.setAccountWeight(_accountWeight);
        return copy;
    }
}
