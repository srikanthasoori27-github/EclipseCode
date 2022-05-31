/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A common model for the results of tasks and requests.
 * The concrete subclasses are TaskResult and Request.
 * 
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import sailpoint.api.MessageRepository;
import sailpoint.object.TaskItemDefinition.Type;
import sailpoint.object.TaskResult.CompletionStatus;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

public abstract class TaskItem
    extends SailPointObject
    implements Cloneable, MessageRepository {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of an attribute in the _attributes map that holds
     * an ElectronicSignature for signed results.
     */
    public static final String ATT_SIGNATURE = "taskResultSignature";

    /**
     * The name of the user that launched the task.
     * This is not an Identity reference so
     * pseudo-users like "System" can be supported to avoid a gratuitous
     * foreign key constraint.
     */
    String _launcher;

    /**
     * The date the task was launched.
     * If this is null, it means that the result was created in 
     * advance and Quartz or the request processor has not started
     * the task yet. This technique is used in a few places where
     * an immediate handle to a TaskResult is needed when the
     * task is scheduled.
     */
    Date _launched;

    /**
     * The name of the host the task is running on if it is still running.
     * The value is undefined if the task is complete though it
     * should try to be set to null.
     *
     * This is used by Environment to terminate orphaned tasks
     * that think they are still running when the system is started.
     * For running tasks this will be the value of Util.getHostName().
     *
     * This may also be used for Requests to indtifiy the host we want
     * to process the request.  In that case, _launched will be null.
     */
    String _host;

    /**
     * The date of completion.      
     * This could be the same as last mod time, but if these are allowed
     * to be edited the times have to be different.
     */
    Date _completed;

    /**
     * Status of this item when completed: Success, Failure,
     * Warning or Terminated.
     *
     * Note: This property should generally not be set by 
     * Executor code since the TaskManager or RequestProcessor 
     * will evaluate the completion status once execution is completed. 
     * To get the TaskResult to a terminated CompletionStatus for example,
     * set _terminated to true.
     * @ignore
     * Note that the definition of the CompletionStatus property
     * has bee moved down to TaskItem so that it can be shared
     * by both TaskResult and Request.  But the definition of the enumeration
     * had to stay up here for backward compatibility.  It is awkward
     * to move enums.
     */
    TaskResult.CompletionStatus _completionStatus;

    /**
     * A flag that can be set by the Executor to indicate that
     * the execution was terminated. The Executor normally
     * sets this rather than _completionStatus, TaskManager
     * will set _completionStatus.
     * @ignore
     * jsl - I don't really like this, could just allow
     * the Executor to set _completionStatus to Terminated?
     * System code has been inconsistent about using one or the
     * other so this has to be kept in sync with _completionStatus.
     */
    boolean _terminated;

    /**
     * The date this item expires and will be deleted.
     * If null the item never expires.
     */
    Date _expiration;

    /**
     * For TaskResult these represent the result attributes, what was
     * left behind by the executor.
     *
     * For Requests these are state of the request held between retries.
     *
     * For "event" Requests, these are the input arguments for the executor.
     *
     * For partitioned task results, the attribute "partitions" may contain
     * a List of Maps with each Map representing the result of one partition.
     * The UI will display this as a list of result tables.
     */
    Attributes<String,Object> _attributes;
    
    /**
     * A list of messages accumulated during execution.
     * This is the standard way for a result to convey an overall failure
     * status. If list does not contain any errors then the task is considered
     * a success.
     * 
     * More complex tasks might need to build up a more complex error structure,
     * with the messages associated with other data. If that happens they
     * can be left in the _attributes map, with an overall error status
     * indicated with a single "task completed with errors" message in this list.
     * 
     * @ignore
     * !! Need to think more about this, even if we allow a complex
     * error structure, the UI won't be able to display it if it isn't
     * formally defined or we have a custom result page for each task.
     */
    List<Message> _messages;

    /**
     * The stack trace from an exception thrown by the executor.
     * This is handy for diagnostics, but it cannot be in the
     * _errors list which is intended for display in the UI.
     * Assuming that this is used only for the single unexpected
     * exception that terminates the executor.  
     *
     * This is currently used only by the RequestProcessor, TaskManager
     * does not save stacks.
     *
     * Starting with 7.2 this is also used to dynamically capture
     * the stack trace of a running executor.  We could have a different
     * property for that, but this was already here, it is rarely used, and 
     * it won't conflict.  When the RP finishes a request, this will usually
     * get nulld out if there was no exception, but at that point we don't
     * need old stack traces.
     * 
     * @ignore
     * If the task is doing something that needs to be tolerant of
     * exceptions, then we may want a list of these?
     */
    String _stack;

    /**
     * The type of the result, this is inherited from the TaskItemDefinition.
     */
    Type _type;

    /**
     * Execution Progress a string to give the user some
     * indication of what is happening during long running tasks. 
     * NOTE: TaskExecutors can use the TaskMonitor object to update  
     * this property on the interval defined on the TaskItemDefinition.
     */
    String _progress;

    /**
     * More detailed progress information, for executors that can
     * report the percentage completed. 
     * An integer from 0 to 100 to indicate what percentage complete
     * the task is at the current execution point.
     * NOTE: TaskExecutors can use the TaskMonitor object to update  
     * this property on the interval defined on the TaskItemDefinition.
     */ 
    int _percentComplete;


    boolean _live;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public TaskItem() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    @XMLProperty
    public String getLauncher() {
        return _launcher;
    }

    public void setLauncher(String s) {
        _launcher = s;
    }

    @XMLProperty
    public Date getLaunched() {
        return _launched;
    }

    public void setLaunched(Date d) {
        _launched = d;
    }

    @XMLProperty
    public String getHost() {
        return _host;
    }

    public void setHost(String s) {
        _host = s;
    }

    @XMLProperty
    public boolean isLive() {
        return _live;
    }

    public void setLive(boolean  live) {
        _live = live;
    }

    @XMLProperty
    public Date getCompleted() {
        return _completed;
    }

    public void setCompleted(Date d) {
        _completed = d;
    }

    public long getDuration() {
        long duration = 0;
        if (_launched != null) {
            // compare with the current time if it hasn't completed yet
            Date end = ((_completed != null) ? _completed : new Date());
            duration = end.getTime() - _launched.getTime();
        }
        return duration;
    }

    @XMLProperty
    public Date getExpiration() {
        return _expiration;
    }

    public void setExpiration(Date d) {
        _expiration = d;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String,Object> atts) {
        _attributes = atts;
    }

    public void addAttribute(String attr, Object val){
        if (_attributes == null)
            _attributes = new Attributes<String, Object>();

        _attributes.put(attr, val);
    }

    /**
     * Return any messages created during execution of this task.
     */
    public List<Message> getMessages() {
        return _messages;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public void setMessages(List<Message> messages) {
        this._messages = messages;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getStack() {
        return _stack;
    }

    public void setStack(String stack) {
        _stack = stack;
    }

    @XMLProperty
    public void setType(Type type) {
        _type = type;
    }

    public Type getType() {
        return _type;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getProgress() {
        return _progress;
    }

    public void setProgress(String progress) {
        _progress = progress;
    }

    @XMLProperty
    public int getPercentComplete() {
        return _percentComplete;
    }

    public void setPercentComplete(int percentComplete) {
        _percentComplete = percentComplete;
    }

    @XMLProperty
    public TaskResult.CompletionStatus getCompletionStatus() {
        return _completionStatus;
    }

    public void setCompletionStatus(TaskResult.CompletionStatus completionStatus) {
        this._completionStatus = completionStatus;
    }

    @XMLProperty
    public boolean isTerminated() {
        return _terminated;
    }

    public void setTerminated(boolean b) {
        this._terminated = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience accessors
    //
    //////////////////////////////////////////////////////////////////////

    public boolean isComplete() {
        return (_completed != null);
    }

    /**
     * Check for successful task
     * @return True if the task is complete and if there are no errors in the
     * messages list.
     */
    public boolean isSuccess() {
    	return isComplete() && (getMessagesByType(Message.Type.Error) == null
                || getMessagesByType(Message.Type.Error).isEmpty());
    }
   
    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public void setAttribute(String name, Object value) {
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();
        _attributes.put(name, value);
    }

    // most things support map-style accessors too
    public Object get(String name) {
        return getAttribute(name);
    }

    public void put(String name, Object value) {
        setAttribute(name, value);
    }

    public void remove(String name) {
        if (_attributes != null) _attributes.remove(name);
    }

    public String getString(String name) {
        return (_attributes != null) ? _attributes.getString(name) : null;
    }

    public boolean getBoolean(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : false;
    }

    public Date getDate(String name) {
        return (_attributes != null) ? _attributes.getDate(name) : null;
    }

    public int getInt(String name) {
        return (_attributes != null) ? _attributes.getInt(name) : 0;
    }

    /**
     * Convenience setter for tasks that keep lots of numeric
     * statistics. Convert to the int to a String but only store
     * it if it is non-zero to avoid cluttering up the result.
     * If it is zero and a value for it exists, remove the value.
     */
    public void setInt(String name, int value) {
        if (value != 0) {
            setAttribute(name, Util.itoa(value));
        } else if (_attributes != null && _attributes.containsKey(name)) {
            _attributes.remove(name);
        }
    }

    /**
     * Add to the possible previous value of an attribute.
     * This must be used by tasks that maintain worker threads
     * with their own statistics that are merged at the end.
     */
    public void addInt(String name, int value) {
        if (value != 0)
            setInt(name, getInt(name) + value);
    }

    /**
     * Set our attributes map by merging attributes from a TaskItemDefinition
     * and another map.
     */
    public void setAttributes(TaskItemDefinition def, Map<String,Object> args) {

        // start by flattening the definition args
        if (def != null)
            _attributes = def.getEffectiveArguments();
        else
            _attributes = new Attributes<String,Object>();

        // then add the overrides
        if (args != null)
            _attributes.putAll(args);
    }

    //////////////////////////////////////////////////////////////////////
    //
    //  Messages
    //
    //////////////////////////////////////////////////////////////////////

    public void addMessage(String message) {
        if (message != null) {
            if (_messages == null)
                _messages =  new ArrayList<Message>();
            _messages.add(new Message(message));
        }  
    }

    public void addMessage(Message message) {
        if (message != null) {
            if (_messages == null)
                _messages =  new ArrayList<Message>();
            _messages.add(message);
        }  
    }

    public void addMessages(List<Message> messages) {
        if (messages != null) {
            initMessages();
            _messages.addAll(messages);
        }
    }

    /**
     * Add a message for an exception.
     * This mutation is what TaskManager has historically done
     * for exceptions thrown from the executor.
     */
    public void addException(Throwable t) {

        Message msg;

        // If it's one of ours, take off the class prefix to make
        // message in the UI look cleaner.
        if (t instanceof GeneralException) {
            msg = ((GeneralException)t).getMessageInstance();
            // Override the type set when the exception was created
            msg.setType(Message.Type.Error);
        }
        else {
            msg = new Message(Message.Type.Error,
                              MessageKeys.ERR_EXCEPTION, t);
        }

        addMessage(msg);
    }

    /**
     * Get error messages 
     * @return List of LocalizedMessages where message type='Error', or null.
     */
    public List<Message> getErrors(){
       return getMessagesByType(Message.Type.Error);
    }

    /**
     * Check if there are any error messages 
    * @return True if this TaskItem has accumulated any
    * error messages.
    */
    public boolean hasErrors(){
        return getErrors() != null;
    }

    /**
     * Get warning messages
     * @return List of LocalizedMessages where message type='Warn', or null.
     */
    public List<Message> getWarnings(){
         return getMessagesByType(Message.Type.Warn);
    }

    /**
     * Check if there are any warning messages
     * @return True if this TaskItem has accumulated any
     * warning messages.
     */
    public boolean hasWarnings(){
        return getWarnings() != null;
    }

    /**
     * Get messages for a specific type
     * @param type Type of message to retrieve
     * @return List of messages matching the given type, or null if none were found.
     */
    public List<Message> getMessagesByType(Message.Type type) {
        if (_messages == null || _messages.isEmpty() || type==null)
            return null;

        List<Message> matchingMsgs = new ArrayList<Message>();
        for(Message msg : _messages){
            if (msg.isType(type))
                matchingMsgs.add(msg);
        }

        return !matchingMsgs.isEmpty() ? matchingMsgs : null;
    }

    public boolean isError(){
        return internalIsStatus(Message.Type.Error, CompletionStatus.Error);
    }

    public boolean isWarning(){
        return internalIsStatus(Message.Type.Warn, CompletionStatus.Warning);
    }
    
    private boolean internalIsStatus(Message.Type type, CompletionStatus completionStatus) {
        List msgs = getMessagesByType(type);
        boolean isStatus = msgs != null && !msgs.isEmpty();
        if (!isStatus) {
            // messages are a fine indicator, but maybe we've completed
            // in a more subtle way
            isStatus = _completionStatus == completionStatus;
        }
        return isStatus;
    }

    /**
     * Sets message list to null.
     */
    public void clear(){
        if (_messages != null)
            _messages = null;
    }

    /**
     * Convenience to initialize messages list if it's null.
     */
    private void initMessages(){
        if (_messages == null)
            _messages = new ArrayList<Message>();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Clone this object.
     * <p>
     * For the Cloneable interface.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
