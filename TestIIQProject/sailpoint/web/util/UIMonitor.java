package sailpoint.web.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import sailpoint.api.monitor.GenericMonitor;
import sailpoint.tools.Message;

public class UIMonitor implements GenericMonitor {
    private List<Message> informationalMessages;
    private List<Message> warningMessages;
    private List<Message> errorMessages;
    
    public void info(Message msg) {
        if (informationalMessages == null) {
            informationalMessages = new ArrayList<Message>();
        }
        msg.setType(Message.Type.Info);
        informationalMessages.add(msg);
    }

    public void warn(Message msg) {
        if (warningMessages == null) {
            warningMessages = new ArrayList<Message>();
        }
        msg.setType(Message.Type.Warn);
        warningMessages.add(msg);
    }

    public void error(Message msg) {
        if (errorMessages == null) {
            errorMessages = new ArrayList<Message>();
        }
        msg.setType(Message.Type.Error);
        errorMessages.add(msg);
    }

    public Collection<Message> getInfo() {
        Collection<Message> info;
        if (informationalMessages == null) {
            info = Collections.emptyList();
        } else {
            info = Collections.unmodifiableCollection(informationalMessages);
        }
        return info;
    }

    public Collection<Message> getWarnings() {
        Collection<Message> warnings;
        if (warningMessages == null) {
            warnings = Collections.emptyList();
        } else {
            warnings = Collections.unmodifiableCollection(warningMessages);
        }
        return warnings;
    }

    public Collection<Message> getErrors() {
        Collection<Message> errors;
        if (errorMessages == null) {
            errors = Collections.emptyList();
        } else {
            errors = Collections.unmodifiableCollection(errorMessages);
        }
        return errors;
    }

}
