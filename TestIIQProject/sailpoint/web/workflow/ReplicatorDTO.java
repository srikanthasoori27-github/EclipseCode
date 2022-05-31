package sailpoint.web.workflow;

import sailpoint.object.Workflow.Replicator;
import sailpoint.web.BaseDTO;

/**
 * Created by ryan.pickens on 5/26/15.
 */
public class ReplicatorDTO extends BaseDTO {


    String _items;
    String _arg;


    public ReplicatorDTO() { }

    public ReplicatorDTO(Replicator replicator) {
        _items = replicator.getItems();
        _arg = replicator.getArg();
    }

    public Replicator commit() {
        Replicator repl = new Replicator();
        repl.setItems(_items);
        repl.setArg(_arg);

        return repl;
    }

    public String getItems() {
        return _items;
    }
    public void setItems(String items) {
        _items = items;
    }

    public String getArg() {
        return _arg;
    }
    public void setArg(String arg) {
        _arg = arg;
    }
}
