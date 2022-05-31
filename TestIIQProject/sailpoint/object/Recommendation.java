package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.tools.Message;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

@XMLClass
public class Recommendation extends AbstractXmlObject {

    @XMLClass
    public enum RecommendedDecision implements MessageKeyHolder {
        YES(MessageKeys.UI_RECOMMENDATIONS_YES, true),
        NO(MessageKeys.UI_RECOMMENDATIONS_NO, true),
        NOT_FOUND(MessageKeys.UI_RECOMMENDATIONS_NOT_FOUND, false),
        NO_RECOMMENDATION(MessageKeys.UI_RECOMMENDATIONS_NO_REC, false),
        RECOMMENDER_NOT_CONSULTED(MessageKeys.UI_RECOMMENDATIONS_NOT_CONSULTED, false),
        ERROR(MessageKeys.UI_RECOMMENDATIONS_ERROR, false);

        private String messageKey;
        private boolean actionable;  // can one take action based on this value?

        RecommendedDecision(String messageKey, boolean actionable) {
            this.messageKey = messageKey;
            this.actionable = actionable;
        }

        @Override
        public String getMessageKey() {
            return this.messageKey;
        }

        public boolean isActionable() { return this.actionable; }

        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            Message msg = new Message(getMessageKey());
            return msg.getLocalizedMessage(locale, timezone);
        }
    }

    /**
     * The time which the recommendation was received
     */
    private Date timeStamp;

    /**
     * The recommended decision
     */
    private RecommendedDecision recommendedDecision = RecommendedDecision.NO_RECOMMENDATION;

    /**
     * Detailed reasons explaining the recommended decision
     */
    @Deprecated
    private List<String> reasons = new ArrayList<>();
    
    /**
     * Internationalized reasons explaining the recommended decision
     */
    private List<Reason> reasonMessages = new ArrayList<>();

    ////////////////////////////
    /// getters/setters
    ////////////////////////////

    /**
     * @Deprecated use {@link #getReasonMessages()}
     */
    @Deprecated
    @XMLProperty
    public List<String> getReasons() {
        return reasons;
    }

    /**
     * @Deprecated use {@link #setReasonMessages(List)}
     */
    @Deprecated
    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
    
    @XMLProperty
    public List<Reason> getReasonMessages() {
        return reasonMessages;
    }

    public void setReasonMessages(List<Reason> reasonMessages) {
        this.reasonMessages = reasonMessages;
    }

    @XMLProperty
    public RecommendedDecision getRecommendedDecision() {
        return recommendedDecision;
    }

    public void setRecommendedDecision(RecommendedDecision recommendedDecision) {
        this.recommendedDecision = recommendedDecision;
    }

    @XMLProperty
    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }
}
