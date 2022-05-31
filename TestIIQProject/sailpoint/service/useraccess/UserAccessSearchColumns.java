package sailpoint.service.useraccess;

/**
 * Class to hold static column names for User Access search
 */
public class UserAccessSearchColumns {
    
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_DISPLAYABLE_NAME = "displayableName";
    public static final String COLUMN_OBJECT_TYPE = "objectType";
    public static final String COLUMN_POP_STATS = "populationStatistics";
    public static final String COLUMN_APP_NAME = "application.name";
    public static final String COLUMN_RISK_SCORE = "riskScoreWeight";
    
    /* Current Access */
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_DISPLAYABLE_STATUS = "statusLabel";
    public static final String COLUMN_SUNRISE = "sunrise";
    public static final String COLUMN_SUNSET = "sunset";
    public static final String COLUMN_REMOVABLE = "removable";
    //Entitlement
    public static final String COLUMN_NATIVE_IDENTITY = "nativeIdentity";
    public static final String COLUMN_ACCOUNT_NAME = "accountName";
    public static final String COLUMN_INSTANCE = "instance";
    public static final String COLUMN_VALUE = "value";
    public static final String COLUMN_ATTRIBUTE = "attribute";
    //Role
    public static final String COLUMN_ASSIGNMENT_ID = "assignmentId";
    public static final String COLUMN_ASSIGNMENT_NOTE = "assignmentNote";
    public static final String COLUMN_ROLE_TARGETS = "roleTargets";
    public static final String COLUMN_ROLE_LOCATION = "roleLocation";
    public static final String COLUMN_PERMITTED_ROLE = "permitted";
    public static final String COLUMN_REMOVE_PENDING = "removePending";
}