package sailpoint.web.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.primefaces.model.UploadedFile;

import sailpoint.api.ManagedAttributeImporter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.UIMonitor;

public class ManagedAttributeImportBean extends BaseBean {
    private static Log log = LogFactory.getLog(ManagedAttributeImportBean.class);        
    private UploadedFile importFile;
    
    
    public UploadedFile getImportFile() {
        return importFile;
    }


    public void setImportFile(UploadedFile importFile) {
        this.importFile = importFile;
    }


    /**
     * Import entitlements from a CSV file.
     * 
     * @throws GeneralException 
     */
    public String importEntitlements() throws GeneralException {
        String result;
        
        if (importFile == null) {
            addMessage(new Message(Message.Type.Error, 
                                   MessageKeys.EXPLANATION_IMPORT_NO_IMPORT_FILE));
            
            result = "failure";
        } else {
            ManagedAttributeImporter importer = new ManagedAttributeImporter(getContext());
            UIMonitor monitor = new UIMonitor();
            importer.setMonitor(monitor);
            List<Message> messagesToDisplay = new ArrayList<Message>();
            try {
                importer.importUpload(getImportFile());
                result = "success";
                int attributesCreated = importer.getAttributesCreated();
                // TMI right now -- If we're going to dump info we need to be more judicious about 
                // the info we're monitoring.  Right now it's basically trace-level output
                // messagesToDisplay.addAll(monitor.getInfo());
                messagesToDisplay.addAll(monitor.getWarnings());
                messagesToDisplay.addAll(monitor.getErrors());
                messagesToDisplay.add(new Message(Message.Type.Info, MessageKeys.EXPLANATION_IMPORT_SUCCESSFUL, attributesCreated));
            } catch (Exception e) {
                log.error("The Entitlements Catalog failed to import a file", e);
                result = "failure";
                messagesToDisplay.addAll(monitor.getWarnings());
                messagesToDisplay.addAll(monitor.getErrors());
                messagesToDisplay.add(new Message(Message.Type.Error, MessageKeys.EXPLANATION_IMPORT_FAILED));
            } finally {
                displayMessages(messagesToDisplay);
            }
        }
        
        return result;
    }
    
    private void displayMessages(Collection<Message> msgs) {
        if (msgs != null && !msgs.isEmpty()) {
            for (Message msg : msgs) {
                addMessage(msg);
            }
        }
    }
}
