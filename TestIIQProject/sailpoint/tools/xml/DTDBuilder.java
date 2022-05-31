/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The interface of an object that can build XML DTDs using
 * a more convenient programatic interface.  There is currently only
 * one implementation, DTDBuilderImpl.  
 *
 * One of these will be created and used by XMLObjectFactory after all the
 * serializers have been registered and compiled.
 * 
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools.xml;

import sailpoint.tools.xml.DTDConstraints.ElementConstraints;

interface DTDBuilder
{

    /**
     * Add an element definition to the DTD.
     */
   public boolean defineElement(String elementName, 
                                ElementConstraints constraints);


    /**
     * Return the generated DTD text.
     */
    public String getDTD();

}
