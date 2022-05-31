/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author Bernie Margolis
 */

package sailpoint.object;

import java.util.List;

/**
 * This simple interface is implemented by those SailPointObjects that contain roles.
 * Currently these include Bundle and Process.
 */
public interface RoleContainer {
    void add(Bundle role);
    boolean remove(Bundle role);
    List<Bundle> getRoles();
}
