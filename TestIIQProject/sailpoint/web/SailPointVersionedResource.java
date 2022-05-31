/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import javax.faces.application.Resource;
import javax.faces.application.ResourceWrapper;

import sailpoint.VersionConstants;

/**
 * 
 * Used to tag a revision number on all resources automatically loaded from
 * JSF
 * 
 * Because we do not implicitly include these files, we need a way to tag a
 * revision # to ensure the file is loaded when revision changes instead of
 * getting it from cache.
 * 
 * @author <a href="mailto:ryan.pickens@sailpoint.com">Ryan Pickens</a>
 */
public class SailPointVersionedResource extends ResourceWrapper {

    private Resource resource;

    public SailPointVersionedResource(Resource resource) {
        this.resource = resource;
    }

    @Override
    public Resource getWrapped() {
        return this.resource;
    }

    @Override
    public String getRequestPath() {
        String requestPath = resource.getRequestPath();

        String revision = VersionConstants.REVISION;

        // Currently, we are only using this for resouces loaded via JSF
        // resource loading
        if (requestPath.contains("javax.faces.resource")) {
            if (requestPath.contains("?"))
                requestPath = requestPath + "&rv=" + revision;
            else
                requestPath = requestPath + "?rv=" + revision;
        }

        return requestPath;
    }

    @Override
    public String getContentType() {
        return getWrapped().getContentType();
    }

    @Override
    public String getResourceName() {
        return resource.getResourceName();
    }

    @Override
    public String getLibraryName() {
        return resource.getLibraryName();
    }
}
