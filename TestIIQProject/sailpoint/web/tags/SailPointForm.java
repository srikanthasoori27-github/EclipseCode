package sailpoint.web.tags;

import javax.faces.component.UIForm;


public class SailPointForm extends UIForm {
    
    public SailPointForm() {
        super();
        setRendererType("sailpoint.web.tags.Form");
    }
}
