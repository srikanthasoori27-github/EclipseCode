package sailpoint.messaging.sms;

import sailpoint.messaging.MessageResult;

/**
 * Please see {@link MessageResult} doc.
 * 
 * In addition to the super methods we add SMS specific method here.
 * 
 * @author tapash.majumder
 *
 */
public class SMSMessageResult extends MessageResult<SMSMessage> {

    private String sid;
    
    /**
     * On successfully sending an SMS the service is supposed to return this.
     */
    public String getSid() {
        return sid;
    }
    
    public void setSid(String val) {
        sid = val;
    }
}
