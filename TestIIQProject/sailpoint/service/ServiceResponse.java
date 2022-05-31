package sailpoint.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.tools.Message;

@SuppressWarnings("serial")
public class ServiceResponse extends HashMap<String, Object> {

    private Locale locale;
    private TimeZone timezone;
    
    public ServiceResponse(Locale locale, TimeZone timezone) {
        this.locale = locale;
        this.timezone = timezone;
        
        put("errorMessages", new ArrayList<String>());
    }

    @SuppressWarnings("unchecked")
    public List<String> getErrorMessages() {
        return (List<String>) get("errorMessages");
    }
    
    public ServiceResponse addErrorText(String msg) {
        getErrorMessages().add(msg);
        setSuccess(false);
        return this;
    }

    public ServiceResponse addErrorMessage(String key, Object... vals) {
        return addErrorMessage(new Message(Message.Type.Error, key, vals));
    }

    public ServiceResponse addErrorMessage(Message message) {
        return addErrorText(message.getLocalizedMessage(locale, timezone));
    }
    
    public ServiceResponse setSuccess(boolean success) {
        put("success", success);
        return this;
    }
    
    public boolean isSuccess() {
        Object succObj = get("success");
        if (succObj == null) {
            return false;
        }
        
        return (Boolean) succObj;
    }
}
