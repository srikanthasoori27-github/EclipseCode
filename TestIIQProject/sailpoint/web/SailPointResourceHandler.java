/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import javax.faces.application.Resource;
import javax.faces.application.ResourceHandler;
import javax.faces.application.ResourceHandlerWrapper;

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
public class SailPointResourceHandler extends ResourceHandlerWrapper {

    private ResourceHandler wrapped;

    public SailPointResourceHandler(ResourceHandler wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public ResourceHandler getWrapped() {
        return this.wrapped;
    }

    @Override
    public Resource createResource(String resourceName, String libraryName) {
        Resource resource = super.createResource(resourceName, libraryName);

        if (resource == null) {
            return null;
        }

        return new SailPointVersionedResource(resource);
    }
}
