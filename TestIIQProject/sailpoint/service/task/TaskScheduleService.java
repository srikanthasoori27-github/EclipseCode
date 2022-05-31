package sailpoint.service.task;

import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.TaskSchedule;
import sailpoint.service.BaseObjectService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by ryan.pickens on 6/16/17.
 */
public class TaskScheduleService extends BaseObjectService<TaskSchedule> {

    private TaskSchedule taskSchedule;
    private SailPointContext context;
    private UserContext userContext;


    public TaskScheduleService(String taskScheduleName, UserContext userContext)
        throws GeneralException {

        if (Util.isNullOrEmpty(taskScheduleName)) {
            throw new InvalidParameterException("taskScheduleName");
        }

        initContext(userContext);

        this.taskSchedule = context.getObjectByName(TaskSchedule.class, taskScheduleName);
        if (this.taskSchedule == null) {
            throw new ObjectNotFoundException(TaskSchedule.class, taskScheduleName);
        }
    }

    /**
     * Initialize the context
     * @param userContext UserContext
     * @throws GeneralException
     */
    private void initContext(UserContext userContext) throws GeneralException {
        if (userContext == null) {
            throw new InvalidParameterException("userContext");
        }

        this.userContext = userContext;
        this.context = userContext.getContext();

    }

    public TaskScheduleDTO getTaskScheduleDTO() throws GeneralException {
        return new TaskScheduleDTO(this.taskSchedule);
    }

    public void deleteSchedule() throws GeneralException {
        Terminator ahnold = new Terminator(getContext());
        ahnold.deleteObject(this.taskSchedule);
    }

    @Override
    protected TaskSchedule getObject() {
        return this.taskSchedule;
    }

    @Override
    protected SailPointContext getContext() {
        return context;
    }

    public static String PATCH_FIELD_RESUME_DATE = "resumeDate";

    @Override
    protected List<String> getAllowedPatchFields() {
        List<String> fields = new ArrayList();
        fields.add(PATCH_FIELD_RESUME_DATE);
        return fields;
    }

    @Override
    protected boolean validateValue(String field, Object value) {
        if (PATCH_FIELD_RESUME_DATE.equals(field)) {
            if (value instanceof Date) {
                return true;
            } else if (value instanceof Long) {
                return true;
            } else if (value == null) {
                //Allow null to clear out value
                return true;
            }

        }
        return false;
    }

    @Override
    protected void patchValue(String field, Object value) {
        if (PATCH_FIELD_RESUME_DATE.equals(field)) {
            Date d = null;
            if (value != null) {
                 d = (value instanceof Date) ? (Date) value : new Date((Long) value);
            }
            taskSchedule.setResumeDate(d);
        }
    }


}
