/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.ui.helpers;

public class ClientIdWriterTag extends javax.faces.webapp.UIComponentTag
{

	@Override
	public String getComponentType() {
		return "sailpoint.ui.helpers.ClientIdWriterComponent";
	}

	@Override
	public String getRendererType() {
		return "sailpoint.ui.helpers.ClientIdWriterRenderer";
	}

}
