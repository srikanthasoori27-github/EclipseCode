package sailpoint.web.tags;

import javax.faces.component.html.HtmlInputText;

/**
 * 
 * Overrides the <h:inputText> tag functionality to 
 * add SailPoint specific functionality.
 * 
 * @see InputTextRenderer 
 *
 */
public class InputText extends HtmlInputText {

    public InputText() {
        super();
        setRendererType("sailpoint.web.tags.InputText");
    }
    
}
