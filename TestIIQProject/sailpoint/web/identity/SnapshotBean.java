package sailpoint.web.identity;

import java.io.Serializable;
import java.util.Date;

public class SnapshotBean implements Serializable {
    // TODO: replace with generated
    private static final long serialVersionUID = 1L;

    String _id;
    Date _created;
    String _summary;
    private boolean _pendingDelete;

    public SnapshotBean(String id, Date created) {
        _id = id;
        _created = created;
    }

    public void setSummary(String s) {
        _summary = s;
    }

    public String getId() {
        return _id;
    }

    public Date getCreated() {
        return _created;
    }

    public String getSummary() {
        return _summary;
    }
    
    public boolean isPendingDelete() {
        return _pendingDelete;
    }
    
    public void setPendingDelete(boolean val) {
        _pendingDelete = val;
    }
}
