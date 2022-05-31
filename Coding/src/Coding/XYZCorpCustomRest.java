package Coding;
import sailpoint.rest.SailPointRestApplication;

class XYZCorpCustomRest extends SailPointRestApplication {
    
    public XYZCorpCustomRest() {
        super();
        register(XYZCorpCustomResource.class);
    }
}