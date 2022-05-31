/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.certifications;

import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.Certification;
import sailpoint.rest.BaseListResource;
import sailpoint.service.suggest.SuggestAuthorizerContext;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.certification.CertificationItemSuggestService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

/**
 * Resource for certification item specific suggests
 */
public class CertificationItemSuggestResource extends SuggestResource implements CertificationItemSuggestService.CertificationItemSuggestContext {
    
    private Certification certification;
    
    public CertificationItemSuggestResource(Certification certification, SuggestAuthorizerContext suggestContext, BaseListResource parent) {
        super(parent, suggestContext);
        
        this.certification = certification;
    }
    
    public Certification getCertification() {
        return this.certification;
    }

    /**
     * Get suggest results for the entitlement names, combining attributes and permissions
     * @param application Application name
     * @return ListResult
     * @throws GeneralException
     */
    @GET
    @Path("entitlements/names")
    public ListResult getNames(@QueryParam("application") String application) throws GeneralException {
        if (Util.isNullOrEmpty(application)) {
            throw new InvalidParameterException("application");
        }
        
        return new CertificationItemSuggestService(this).getEntitlementNames(application);
    }

    /**
     * Get suggest results for the entitlement values for the given attribute/permission name
     * @param application Application name
     * @param name Attribute/permission name/target
     * @param isPermission If true, "name" refers to a permission. If false, "name" refers to an attribute            
     * @return ListResult
     * @throws GeneralException
     */
    @GET
    @Path("entitlements/values")
    public ListResult getValues(@QueryParam("application") String application, @QueryParam("name") String name, @QueryParam("isPermission") boolean isPermission)
            throws GeneralException {
        if (Util.isNullOrEmpty(application)) {
            throw new InvalidParameterException("application");
        }
        if (Util.isNullOrEmpty(name)) {
            throw new InvalidParameterException("name");
        }
        
        return new CertificationItemSuggestService(this).getEntitlementValues(application, name, isPermission);
    }
}
