package sailpoint.service;

import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.List;

public class WorkgroupService {
    private Identity requester;

    public WorkgroupService(Identity requester) {
        this.requester = requester;
    }

    public List<String> getLoggedInUsersWorkgroupNames()
            throws GeneralException {
        List<String> names = new ArrayList<String>();
        if ( requester != null ) {
            List<Identity> groups = requester.getWorkgroups();
            if  ( groups != null )  {
                for ( Identity wg : groups ) {
                    String wgName = wg.getName();
                    if  ( wgName != null ) {
                        names.add(wgName);
                    }
                }
            }
        }
        return names;
    }
}
