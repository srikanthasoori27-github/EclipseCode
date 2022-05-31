/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.Date;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class WindowsEventLogEntry {

    private String _id;
    private String _log;
    private String _category;
    private String _categoryString;
    private String _computerName;
    private String _message;
    private String _user;
    private String _sourceName;
    private String _eventCode;
    private String _eventType;
    private String _recordNumber;
    private String _timeWritten;
    private String _timeWrittenGMT;

    public WindowsEventLogEntry() {
    }

    /**
     * Translation of the sub-category. The translation is source-specific. 
     */
    @XMLProperty
    public String getCategoryString() {
        return _categoryString;
    }

    public void setCategoryString(String categoryString) {
        _categoryString = categoryString;
    }

    /**
     * Name of the computer that generated this event. 
     */
    @XMLProperty
    public String getComputerName() {
        return _computerName;
    }

    public void setComputerName(String computerName) {
        _computerName = computerName;
    }

    /** 
     *  Name of Windows NT event log file. This is used together with 
     *  RecordNumber to uniquely identify an instance of this class.
     */
    @XMLProperty
    public String getLogFile() {
        return _log;
    }

    public void setLogFile(String log) {
        _log = log;
    }

    /**
     * User name of the logged-on user when the event occurred. If the 
     * user name cannot be determined, this will be NULL.
     */
    @XMLProperty
    public String getUser() {
        return _user;
    }

    public void setUser(String user) {
        _user = user;
    }

    /**
     * Name of the source (application, service, driver, subsystem) that generated the entry. 
     * It is used, together with EventIdentifier to uniquely identify an Windows NT event type.
     */
    @XMLProperty
    public String getSourceName() {
        return _sourceName;
    }

    public void setSourceName(String source) {
        _sourceName = source;
    }

    /**
     * Sub-category for this event. This sub-category is source-specific. 
     */
    @XMLProperty(xmlname="category")
    public String getCategoryStr() {
        return _category;
    }

    public void setCategoryStr(String category) {
        _category = category;
    }

    public Integer getCategory() {
        return ( _category != null ) ? new Integer(_category) : null;
    }


    /** 
     * Value of the lower 16-bits of the EventIdentifier property. 
     * It is present to match the value displayed in the Windows NT 
     * Event Viewer. Note that two events from the same source may 
     * have the same value for this property but can have 
     * different severity and EventIdentifier values.
     */
    @XMLProperty(xmlname="eventCode")
    public String getEventCodeString() {
        return _eventCode;
    }

    public void setEventCodeString(String eventCode) {
        _eventCode = eventCode;
    }

    public Integer getEventCode() {
        return ( _eventCode != null ) ? new Integer(_eventCode) : null;
    }

    /** 
     * Identifier of the event. This is specific to the source that 
     * generated the event log entry and is used, together with SourceName, 
     * to uniquely identify a Windows NT event type.
    */
    @XMLProperty(xmlname="eventIdentifier")
    public String getEventIdentifier() {
        return _id;
    }

    public void setEventIdentifier(String id) {
        _id = id;
    }

    public Integer getEventId() {
        return ( _id != null ) ? new Integer(_id) : null;
    }


    /**
     * Identifies the event within the Windows NT event log file. This is 
     * specific to the log file and is used together with the log file name 
     * to uniquely identify an instance of this class.
     */
    @XMLProperty(xmlname="recordNumber")
    public String getRecordNumberStr() {
        return _recordNumber;
    }

    public void setRecordNumberStr(String str) {
        _recordNumber = str;
    }

    public Integer getRecordNumber() {
        return (_recordNumber != null ) ? new Integer(_recordNumber) : null;
    }

    /** 
     * 1 Error
     * 2 Warning
     * 3 Information
     * 4 Security audit success
     * 5 Security audit failure
    */
    @XMLProperty(xmlname="eventType") 
    public String getEventTypeString() {
        return _eventType;
    }

    public void setEventTypeString(String eventType) {
        _eventType = eventType;
    }

    public Integer getEventType() {
        return (_eventType != null ) ? new Integer(_eventType) : null;
    }


    /**
     *  Event message as it appears in the Windows NT event log. This is 
     *  a standard message with zero or more insertion strings supplied by 
     *  the source of the Windows NT event. The insertion strings are 
     *  inserted into the standard message in a predefined format. If there 
     *  are no insertion strings or there is a problem inserting the           
     *  insertion strings, only the standard message will be present in 
     *  this field.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT,xmlname="LogMessage")
    public String getMessage() {
        return _message;
    }

    public void setMessage(String message) {
        _message = message;
    }

    /**
     * Time Event was written to the log.
     */
    @XMLProperty(xmlname="timeWritten")
    public String getTimeWrittenString() {
        return _timeWritten;
    }

    public void setTimeWrittenString(String written) {
        _timeWritten = written; 
    }

    @XMLProperty(xmlname="timeWrittenGMT")
    public String getTimeWrittenGMT() {
        return _timeWrittenGMT;
    }

    public void setTimeWrittenGMT(String gmtWritten) throws Exception{
        _timeWrittenGMT = gmtWritten;
    }

    /**
     * Take the string we get that is in GMT and make a Date object out of it.
     * SimpleDateFormat helps here, main idea it to make sure we have a GMT 
     * version of the timestamp.
     */
    public Date getTimeWrittenDate() throws GeneralException {

        Date date = null;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEEE, dd MMM yyyy HH:mm:ss z");
            date = sdf.parse(getTimeWrittenGMT()); 
        } catch(java.text.ParseException e) {
            throw new GeneralException(e);
        }
        return date;
    }
}
