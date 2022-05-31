package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.IdentityArchiver;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.tools.GeneralException;

/**
 * View Identity => History Tab 
 * @author Tapash
 *
 */
public class SnapshotsHelper {

    private IdentityDTO parent;
    /**
     * Snapshot id used for sending to the history bean when viewing a
     * snapshot from the identity page.
     */
    private String snapshotId;
    private List<ColumnConfig> identityHistoryColumns;
    
    public SnapshotsHelper(IdentityDTO parent) {
        this.parent = parent;
    }
    
    public String getSnapshotId() {
        return this.snapshotId;
    }

    public void setSnapshotId(String id) {
        this.snapshotId = id;
    }

    public Map<String, Boolean> getSnapshotSelections() {
        return this.parent.getState().getSnapshotSelections();
    }
    
    public List<SnapshotBean> getSnapshots()
        throws GeneralException {

        List<SnapshotBean> snapshots = this.parent.getState().getSnapshots();

        if (snapshots == null) {
            snapshots = new ArrayList<SnapshotBean>();
            Identity id = this.parent.getObject();

            if (null != id) {
                List<String> columns = new ArrayList<String>();
                columns.add("id");
                columns.add("created");
                columns.add("summary");
                // not actually calculating this yet
                //columns.add("differences");

                // We arguably shouldn't be showing this at all here since
                // it won't scale.  Could use a LiveGrid, but may want
                // a more specialized UI for snapshot management.
                // Put a result cap on it in case it gets out of hand.
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("identityName", id.getName()));
                ops.setResultLimit(100);
                ops.setOrderBy("created");
                ops.setOrderAscending(false);

                Iterator<Object []> result =
                    this.parent.getContext().search(IdentitySnapshot.class, ops, columns);

                if (result != null) {
                    while (result.hasNext()) {
                        Object[] row = result.next();
                        SnapshotBean sb = new SnapshotBean((String)row[0], (Date)row[1]);
                        // we're not actually calculating the differences yet,
                        // so just show the summary
                        sb.setSummary((String)row[2]);

                        snapshots.add(sb);
                    }
                }

                this.parent.getState().setSnapshots(snapshots);
            }
        }
        
        return snapshots;
    }
    
    public GridState getIdentityHistoryGridState() {
        return this.parent.loadGridState(IdentityDTO.GRID_STATE_IDENTITY_HISTORY);
    }

    public String getIdentityHistoryColumnJSON() throws GeneralException {
        return this.parent.getColumnJSON("decision", getIdentityHistoryColumns());
    }
    
    private List<ColumnConfig> getIdentityHistoryColumns() throws GeneralException {
        if (null == this.identityHistoryColumns) 
            this.identityHistoryColumns = this.parent.getUIConfig().getIdentityHistoryTableColumns();

        return this.identityHistoryColumns;
    }       
    
    void addDeletedSnapshotsToRequest(AccountRequest account) {

        if (!this.parent.getState().isAnySnapshotForDelete()) {
            return;
        }
        
        AttributeRequest req = new AttributeRequest();
        req.setName(ProvisioningPlan.ATT_IIQ_SNAPSHOTS);
        req.setOperation(Operation.Remove);
        account.add(req);

        List<String> toRemove = new ArrayList<String>();
        for (SnapshotBean snapshot : this.parent.getState().getSnapshots()) {
            if (snapshot.isPendingDelete()) {
                toRemove.add(snapshot.getId());
            }
        }
        req.setValue(toRemove);
    }

    
    
    //////////////////////////////////////////////////////////////////////
    //
    // Snapshot Actions
    //
    //////////////////////////////////////////////////////////////////////
    public String snapshotViewAction() throws GeneralException {

        // HistoryBean will look for "historyid" in the request parameters
        this.parent.saveSession();

        //Put the id on the session
        this.parent.getSessionScope().put(IdentityHistoryDTO.HISTORY_ID, getSnapshotId());
        return "history";
    }

    /**
     * Just for testing/demos, allow a button that can generate
     * an identity snapshot without having to run the aggregator.
     * NOTE: Unlike most edit page actions we will go ahead and
     * commit the snapshot now.  This saves having to maintain
     * another "toBeCreated" list in IdentityEditNew since this
     * is just for testing anyway.
     */
    public String snapshotAction() throws GeneralException {

        Identity id = this.parent.getObject();
        if (id != null) {
            IdentityArchiver archiver = new IdentityArchiver(this.parent.getContext());
            IdentitySnapshot arch = archiver.createSnapshot(id);

            // do this in another session/transaction so we don't
            // commit the partially modified identity
            // !! We would like SailPointFactory to throw if you
            // try to create two contexts in the same thread,
            // but that isn't possible here unless we launch another
            // thread just to do the commit
            SailPointContext c = SailPointFactory.createPrivateContext();
            try {
                c.saveObject(arch);
                c.commitTransaction();

                // and add it to our snapshot list, at the front
                // since we're in descending order
                SnapshotBean sb = new SnapshotBean(arch.getId(), arch.getCreated());
                this.parent.getState().add(sb);
            }
            finally {
                SailPointFactory.releaseContext(c);
            }
        }

        this.parent.saveSession();
        return null;
    }

    public String deleteSnapshots() throws GeneralException {

        Identity ident = this.parent.getObject();
        if (ident != null) {
            Map<String,Boolean> selections = this.parent.getState().getSnapshotSelections();
            if (selections != null) {
                for (String key : selections.keySet()) {
                    if (selections.get(key)) {
                        this.parent.getState().removeSnapshot(key);
                    }
                }
                this.parent.getState().clearSnapshotSelections();
            }
        }

        this.parent.saveSession();
        return null;
    }

    
}
