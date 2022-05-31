/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 * @author <a href="mailto:peter.holcomb@sailpoint.com">Peter Holcomb</a>
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLProperty;

/**
 * An ExtState holds information about how an ext-based component should be displayed.  
 * This is used when transitioning in the UI to remember how an ext-based component
 * was previously displayed so it can be displayed in the same manner again when the
 * user comes back to the page in which the component was shown. This is used in 
 * conjunction with the SailPoint.State javascript object by converting this object 
 * to JSON and passing the JSON-created object to the SailPoint.State.
 */
public class ExtState extends AbstractXmlObject {
    private static final long serialVersionUID = -783989636106929649L;
    private String name;
    private String state;

    /**
     * Default constructor.
     */
    public ExtState() {
    }

    @XMLProperty
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	@XMLProperty
	public String getState() {
		return state;
	}
	
	public void setState(String state) {
		this.state = state;
	}
    
}
