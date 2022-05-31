/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * An event that gets fired when a certification schedule is created/update/
 * deleted.
 */
@XMLClass
public class CertificationScheduleChangeEvent
    extends AbstractChangeEvent<CertificationDefinition> {

    private TaskSchedule oldTaskSchedule;
    private TaskSchedule newTaskSchedule;
    

    /**
     * Default constructor - required for XML persistence.
     */
    public CertificationScheduleChangeEvent() {
        super();
    }

    /**
     * Constructor for a deletion event.
     */
    public CertificationScheduleChangeEvent(String deletedId) {
        super(deletedId);
    }

    /**
     * Constructor for a creation event.
     */
    public CertificationScheduleChangeEvent(CertificationDefinition newObject) {
        super(newObject);
    }

    /**
     * Constructor for a modify event.
     */
    public CertificationScheduleChangeEvent(CertificationDefinition oldObject,
                                            CertificationDefinition newObject,
                                            TaskSchedule oldSched,
                                            TaskSchedule newSched) {
        super(oldObject, newObject);
        this.oldTaskSchedule = oldSched;
        this.newTaskSchedule = newSched;
    }

    
    @XMLProperty
    public TaskSchedule getOldTaskSchedule() {
        return this.oldTaskSchedule;
    }

    /**
     * @exclude
     * @deprecated  This field is immutable, but the setter is required for
     *              XML serialization.
     */
    @Deprecated
    public void setOldTaskSchedule(TaskSchedule sched) {
        this.oldTaskSchedule = sched;
    }
    
    @XMLProperty
    public TaskSchedule getNewTaskSchedule() {
        return this.newTaskSchedule;
    }

    /**
     * @exclude
     * @deprecated  This field is immutable, but the setter is required for
     *              XML serialization.
     */
    @Deprecated
    public void setNewTaskSchedule(TaskSchedule sched) {
        this.newTaskSchedule = sched;
    }
}
