package sailpoint.web.tags;

import javax.faces.component.html.HtmlCommandLink;

/**
 * Overrides the <h:commandLink> tag to add the ability to specify target on the
 * generated anchor tag.
 *
 * The target attribute defaults to _self
 *
 * @see SpCommandLinkRenderer
 */
public class CommandLink extends HtmlCommandLink{
    public CommandLink() {
        super();
        setRendererType("sailpoint.web.tags.CommandLink");
    }
}
