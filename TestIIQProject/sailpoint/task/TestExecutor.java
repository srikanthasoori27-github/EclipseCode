/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * TaskExecutor used for testing scheduling, progress, and termination.
 *
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * 
 * A TestExector class, used for testing task and task ui related
 * stuff.
 *
 */
public class TestExecutor extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(TestExecutor.class);

    public static final String ARG_MESSAGE = "message";
    public static final String ARG_WAIT = "wait";
    public static final String ARG_RESULT = "result";
    public static final String ARG_NO_ERRORS = "noErrors";
    public static final String ARG_NO_RESULTS = "noResults";

    public static final String ARG_SLEEP_SECONDS = "sleepForNSeconds";

    private boolean _terminate;

    //////////////////////////////////////////////////////////////////////
    //  
    //  djs:
    //
    //  This implementation is a merge of the original 
    //  sailpoint.task.TestExecutor and sailpoint.report.TestExector
    //
    //////////////////////////////////////////////////////////////////////

    public void execute(SailPointContext ctx, TaskSchedule sched,
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        TaskDefinition def = sched.getDefinition();

        // jsl - for tasks that we just want to sleep, avoid adding
        // all the simulated results
        boolean noResults = args.getBoolean(ARG_NO_RESULTS);
        if (!noResults) {
            Attributes attrs = getTestResultAttributes(args);
            result.setAttributes(attrs);

            String msg = (String)args.get(ARG_MESSAGE);
            if (msg != null)
                println(def, msg);

            msg = (String)args.get(ARG_RESULT);
            if (msg != null)
                result.setAttribute("result", msg);

            List<String> errors = new ArrayList<String>();
            attrs.put("errors", errors);
        }

        // Sleep for specified wait period
        String wait = (String)args.get(ARG_WAIT);
        if (wait != null) {
            // hang for awhile so we can test TaskInstance visibility
            int millis = Util.atoi(wait);
            if (millis > 0) {
                try {
                    println(def, "waiting...");
                    Thread.sleep(millis);
                    println(def, "finished waiting");
                }
                catch (InterruptedException e) {}
            }
        }

        // Sleep for specified seconds looping and updating progress
        boolean longProgress = args.getBoolean("progressOverflow");
        int sleep = args.getInt(ARG_SLEEP_SECONDS);
        int sleepTotal = 0;
        if ( sleep > 0 ) {
           while ( sleepTotal < sleep && !_terminate) {
               int percentDone = Util.getPercentage(sleepTotal, sleep);
               String progress = "[" + new Date() + "] -- Iteration " 
                   + (sleepTotal + 1)
                   + " of " + sleep + ".";
               if ( longProgress) {
                   progress += " " + buildLongProgress();
               }
               updateProgress(ctx, result, progress, percentDone);

               System.out.println(progress);

               Thread.sleep(1000);
               // increment here to avoid scewing the percentDone
               sleepTotal++;
           }
        }

        if (!noResults) {
            result.setAttribute("total", Util.itoa(sleepTotal));

            if (!args.getBoolean(ARG_NO_ERRORS)) {
                result.addMessages(getErrors());
                result.addMessages(getWarnings());
            }
        }

        if (_terminate) {
            result.addMessage(new Message(Message.Type.Error ,
                    MessageKeys.TASK_TERM_BY_USR_REQUEST));
            result.setTerminated(true);
        }

        saveResult(ctx, result);
    }

    private Attributes<String,Object> 
        getTestResultAttributes(Attributes<String, Object> args) {

        // return attributes for testing signatures
        Attributes<String,Object> attrs = new Attributes<String,Object>(args);
        attrs.put("attr1", "value1");
        attrs.put("attr2", "value2");
        attrs.put("attr3", "value3");
        attrs.put("attr4", "value4");
        attrs.put("attr5", new Integer(100));
        attrs.put("attr6", new Long(1000));

        List<String> list = new ArrayList<String>();
        list.add("listValue0");
        list.add("listValue1");
        list.add("listValue2");
        attrs.put("attr7", list);

        return attrs;
    }

    private List<Message> getWarnings() {
        ArrayList<Message> list = new ArrayList<Message>();
        list.add(new Message("Warning1"));
        list.add(new Message("Warning2"));
        list.add(new Message("Warning3"));
        return list;
    }

    private List<Message> getErrors() {
        ArrayList<Message> list = new ArrayList<Message>();
        list.add(new Message("Error1"));
        list.add(new Message("Error2"));
        list.add(new Message("Error3"));
        return list;
    }


    /*
     * Common method save TaskResult.
     */
    private void saveResult(SailPointContext ctx, TaskResult result)
        throws GeneralException {

        Attributes attrs = result.getAttributes();
        if ( attrs != null )  {
            List<String> errors = attrs.getList("errors");
            if ( errors.size() == 0 ) {
                attrs.remove("errors");
            } else {
                attrs.put("hasError", "true");
            }
        }

        if ( _log.isDebugEnabled() ) {
            _log.debug("Result:"  + result.toXml());
        }
        return;
    }

    public boolean terminate() {
        _terminate = true;
        return true;
    }

    public static void println(TaskDefinition def, Object o) {
        System.out.println("Task " + def.getName() + ": " + o);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    private final String TEST_PROGRESS = "This is a test progress string.";
    public String buildLongProgress() {
        StringBuffer sb = new StringBuffer();
        int len = sb.length();
        while ( ( len = sb.length() ) < 255 ) {
             sb.append(TEST_PROGRESS);
        }
        return sb.toString();
    }
}
