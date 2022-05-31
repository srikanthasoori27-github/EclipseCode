/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.ui.helpers;

import java.io.IOException;

import javax.faces.component.UIComponent;
import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;

public class ClientIdWriterComponent extends UIOutput {

	private String lhs;
	
	private String var;
	
	private String path;
	
	/**
	 * @return Returns the expression.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @param expression The expression to set.
	 */
	public void setPath(String expression) {
		this.path = expression;
	}

	/**
	 * @return Returns the var.
	 */
	public String getVar() {
		return var;
	}

	/**
	 * @param var The var to set.
	 */
	public void setVar(String var) {
		this.var = var;
	}

	/**
	 * @return Returns the lhs.
	 */
	public String getLhs() {
		return lhs;
	}

	/**
	 * @param lhs The lhs to set.
	 */
	public void setLhs(String lhs) {
		this.lhs = lhs;
	}

	/* (non-Javadoc)
	 * @see javax.faces.component.UIComponentBase#encodeBegin(javax.faces.context.FacesContext)
	 */
	@Override
	public void encodeBegin(FacesContext context) throws IOException {
		UIComponent component = this;
		
		if (this.path.startsWith("/")) {
            // Absolute searches start at the root of the tree
            while (component.getParent() != null) {
                component = component.getParent();
            }
		}

		String eraseme = "asdf";
		String[] pathParts = this.path.split("/");
		for(String part : pathParts) {
			if (".".equals(part))
				continue;
			if ("..".equals(part))
				component = component.getParent();
			else
				component = component.findComponent(part);
		}
		
//		ResponseWriter writer = context.getResponseWriter();
//		writer.startElement("script", this);
//		writer.writeText( this.lhs + " = '" + component.getClientId(context) + "'", null);
//		writer.endElement("script");
		String s = component.getClientId(context);
		context.getExternalContext().getRequestMap().put(this.var, s);
	}

	/* (non-Javadoc)
	 * @see javax.faces.component.UIComponentBase#encodeChildren(javax.faces.context.FacesContext)
	 */
	@Override
	public void encodeChildren(FacesContext context) throws IOException {
		// TODO Auto-generated method stub
		super.encodeChildren(context);
	}

	/* (non-Javadoc)
	 * @see javax.faces.component.UIComponentBase#encodeEnd(javax.faces.context.FacesContext)
	 */
	@Override
	public void encodeEnd(FacesContext context) throws IOException {
		// TODO Auto-generated method stub
		super.encodeEnd(context);
	}

	public ClientIdWriterComponent() {
		super();
		// TODO Auto-generated constructor stub
	}

}
