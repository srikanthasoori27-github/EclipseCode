package sailpoint.web.workitem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.faces.model.SelectItem;

import sailpoint.object.Filter;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.web.certification.CertificationFilter;
import sailpoint.web.messages.MessageKeys;

public class ApprovalItemFilterBean {

    ApprovalItemFilter filter = new ApprovalItemFilter();
    
    public ApprovalItemFilter getFilter() {
        return filter;
    }
    
    public class ApprovalItemFilter {
        private Status status;
        
        public Status getStatus() {
            return status;
        }
        
        public void setStatus( Status status ) {
            this.status = status;
        }
        
        public List<SelectItem> getStatusChoices() {
            List<SelectItem> list = new ArrayList<SelectItem>();
            list.add( new SelectItem( "", getMessage( MessageKeys.SELECT_STATUS ) ) );
            for( Status status : Status.values() ) {
                list.add( new SelectItem( status, getMessage( status.getMessageKey() ) ) );
            }
            return list;
        }
        
        boolean hasDifferences = false;
        public boolean getHasDifferences() {
            return hasDifferences;
        }
        
        public void setHasDifferences( boolean hasDifferences) {
            this.hasDifferences = hasDifferences;
        }
        
        public boolean isAllowAddFilter() {
            return false;
        }
        
        public List<CertificationFilter> getFilters() {
            /* TODO: Dummied up to fake out certificationFilters.xhtml */
            return Collections.emptyList();
        }
        
        public String getPropertyName() {
            return "";
        }
        
        public String getSelectedFilterId() {
            return "";
        }
        public Filter getFilter() {
            return Filter.notnull( "id" );
        }
        public void setSelectedFilterId( String noOp ) {
        }
        
        private String getMessage( String messageKey ) {
            Message message = new Message( messageKey );
            String localizedMessage = message.getLocalizedMessage( Locale.getDefault(), TimeZone.getDefault() );
            return localizedMessage;
        }
        
    }
    
    enum Status implements Localizable{
        Revoked( MessageKeys.REVOKED ),
        Approved( MessageKeys.APPROVED );
        
        private String messageKey;
    
        private Status(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getLocalizedMessage() {
            return getLocalizedMessage( Locale.getDefault(), TimeZone.getDefault() );
        }
        
        public String getMessageKey() {
            return messageKey;
        }

        public String getLocalizedMessage( Locale locale, TimeZone timezone ) {
            Message msg = new Message( messageKey );
            return msg.getLocalizedMessage( locale, timezone );
        }

    }
}
