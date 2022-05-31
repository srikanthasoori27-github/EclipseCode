/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;

/**
 * A wrapper implementation of the Sun JSF RI InputSecretTag that overrides
 * the renderer and component types to point to our implementation.
 *
 * @see sailpoint.web.tags.SecretRenderer
 * @see sailpoint.web.tags.HtmlInputSecret
 */
public class InputSecretTag
                      extends com.sun.faces.taglib.html_basic.InputSecretTag {

    public String getRendererType() { return "sailpoint.web.tags.Secret"; }
    public String getComponentType() { return "sailpoint.web.tags.Secret"; }

}  // class InputSecretTag
