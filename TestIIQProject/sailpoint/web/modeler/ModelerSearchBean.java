/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.GeneralException;
import sailpoint.web.search.IdentitySearchBean;
import sailpoint.web.search.SearchBean;

/**
 * The IdentitySearchBean is not a4j-friendly because it caches the contexts.
 * This subclass overrides that behavior so that the IdentitySearchBean can work 
 * in an a4j environment
 * @author Bernie Margolis
 */
public class ModelerSearchBean extends IdentitySearchBean {
    private static final long serialVersionUID = 5772206598079150053L;
    private static final Log log = LogFactory.getLog(ModelerSearchBean.class);

    public static final String SEARCH_BY_ATTRIBUTES = "searchByAttributes";
    public static final String SEARCH_BY_IPOP = "searchByIpop";
    
    private String _searchBy;
    
    public ModelerSearchBean() {
        super();
        _searchBy = SEARCH_BY_ATTRIBUTES; 
    }
    
    @Override
    public FacesContext getFacesContext() {
        return FacesContext.getCurrentInstance();
    }
    
    @Override
    public SailPointContext getContext() {
        SailPointContext context;
        
        try {
            context = SailPointFactory.getCurrentContext();
        } catch (GeneralException e) {
            context = null;
            log.error("The ModelerSearchBean could not get a SailPointContext.", e);
        }
        
        return context;
    }
    
    public String getSearchBy() {
        return _searchBy;
    }
    
    public void setSearchBy(String searchBy) {
        _searchBy = searchBy;
    }

    @Override
    protected String getSearchItemId() {
        return "mining" + SearchBean.ATT_SEARCH_ITEM;
    }
}
