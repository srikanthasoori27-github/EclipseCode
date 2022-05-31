/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object used to maintain various statistics related
 * to role mining.  One of these may be attached to 
 * a Bundle object if it was defined by mining.  I'm
 * keeping this as an XML blob for awhile so we can
 * add things in here without facing schema upgrades
 * of integer columns which are especially painful.
 * 
 * Author: Jeff
 * 
 * UPDATE: With the addition of CandidiateRole we may no
 * longer need this hanging off Bundle though it could
 * be a useful statistic to maintain.
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.AbstractXmlObject;


/**
 * An object used to maintain various statistics related
 * to role mining. One of these might be attached to 
 * a Bundle object if it was defined by mining.  
 */
@XMLClass
public class MiningStatistics extends AbstractXmlObject implements Cloneable {

    int _matches;
    int _matchPercentage;


    public MiningStatistics() {
    }
    
    @XMLProperty
    public int getMatches() {
        return _matches;
    }

    public void setMatches(int i) {
        _matches = i;
    }

    @XMLProperty
    public int getMatchPercentage() {
        return _matchPercentage;
    }

    public void setMatchPercentage(int i) {
        _matchPercentage = i;
    }


}
