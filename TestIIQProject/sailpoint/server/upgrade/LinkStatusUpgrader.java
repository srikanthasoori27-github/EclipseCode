package sailpoint.server.upgrade;

import sailpoint.api.SailPointContext;
import sailpoint.object.Link;
import sailpoint.tools.GeneralException;

/**
 * Link status upgrader. Transfers IIQLocked and IIQDisabled values from the attributes map to the new columns.
 *
 * <TaskDefinition name='LinkStatusUpgrader' executor="sailpoint.task.IdentityRefreshExecutor">
 * <Attributes>
 *      <Map>
 *      <entry key="refreshLinkStatuses" value="true" />
 *      <entry key="refreshThreads" value="5" />
 *      </Map>
 *      </Attributes>
 *  <Description>TaskDefinition for the upgrader that updates new the link status db values.</Description>
 * </TaskDefinition>
 *
 * @author patrick.jeong
 */
public class LinkStatusUpgrader extends TaskBasedUpgrader {

    public LinkStatusUpgrader() {
        setMaxReportTimeInterval(1200);
    }

    /**
     * Don't run if there are no link objects.
     */
    public void performUpgrade(Context context) throws GeneralException {
        SailPointContext sailpointContext = context.getContext();
        if (sailpointContext.countObjects(Link.class, null) > 0) {
            super.performUpgrade(context);
        }
        else {
            info("No links found, skipping.");
        }
    }
}
