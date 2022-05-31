package sailpoint.web.identity;

import java.util.Collections;
import java.util.List;

import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.Util;
import sailpoint.tools.LazyLoad.ILazyLoader;
import sailpoint.tools.Util.IConverter;

/**
 * IdentityDTO delegates to this class
 * for View Identity -> User Rights
 * Workgroup related things
 *
 */
public class WorkgroupHelper {

    private IdentityDTO parent;
    private LazyLoad<List<WorkgroupInfo>> workgroups;

    public WorkgroupHelper(IdentityDTO parent) {
        this.parent = parent;

        this.workgroups = new LazyLoad<List<WorkgroupInfo>>(new ILazyLoader<List<WorkgroupInfo>>(){

            public List<WorkgroupInfo> load() throws GeneralException{
                return Util.convert(getWorkgroupIdentities(), new IConverter<Identity, WorkgroupInfo>() {
                    public WorkgroupInfo convert(Identity identity) throws GeneralException {
                        WorkgroupInfo info = new WorkgroupInfo();
                        info.setId(identity.getId());
                        info.setName(identity.getName());
                        info.setDescription(identity.getDescription());
                        return info;
                    }
                });
            }
        });
    }

    public List<WorkgroupInfo> getWorkgroups() throws GeneralException {
        return this.workgroups.getValue();
    }
    
    private List<Identity> getWorkgroupIdentities() throws GeneralException {
        List<Identity> wgs = null;
        Identity ident = this.parent.getObject();
        if (ident != null) {
            wgs = ident.getWorkgroups();
            if (Util.size(wgs) > 0)
                Collections.sort(wgs, Identity.getByNameComparator());
        }
        return wgs;
    }

    public static class WorkgroupInfo {
        private String id;
        private String name;
        private String description;

        public String getId() {
            return this.id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return this.description;
        }

        public void setDescription(String desription) {
            this.description = desription;
        }

    }
}
