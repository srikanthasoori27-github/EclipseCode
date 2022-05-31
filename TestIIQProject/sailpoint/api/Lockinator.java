package sailpoint.api;

import java.util.Date;

import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;

public class Lockinator {

    SailPointContext _context;
    
    public static enum LockType {
        
        Login(Configuration.LOGIN_LOCKOUT_DURATION),
        
        AuthQuestion(Configuration.AUTH_QUESTION_LOCKOUT_DURATION_MILLIS);
        
        private String _configKey;
        
        private LockType(String config) {
            this._configKey = config;
        }
        
        public String getMessageKey() {
            return this._configKey;
        }
    }
    
    
    
    
    
    
    
    public Lockinator(SailPointContext con) {
        _context = con;
    }
    
    /**
     * 
     * @param user  The user to lock
     * @param accountId The accountId to print in the Audit Event if the auditor is enabled for this type of event
     * @param saveAfterUpdate   True to save and commit transaction after lock, false to simple update the user object
     * @throws GeneralException
     */
    public void lockUser(Identity user, String accountId, Boolean saveAfterUpdate) throws GeneralException {
        user.setAuthLockStart(new Date());
        AuditEvent event = new AuditEvent();
        event.setAction(AuditEvent.IdentityLocked);
        event.setTarget(accountId);
        if ( Auditor.isEnabled(event.getAction()) ) {
            Auditor.log(event);
        }
    
        if(saveAfterUpdate) {
            _context.saveObject(user);
            _context.commitTransaction();
        }
    }
    
    

    /**
     * 
     * @param user  The user we want to unlock
     * @param saveAfterUpdate   True to save and commit transaction after unlock, false to update the user object
     * @throws GeneralException
     */
    public void unlockUser(Identity user, Boolean saveAfterUpdate) throws GeneralException {

        if (user.getAuthLockStart() != null) {
            //Only Audit if lockStart was non-null to start
            AuditEvent event = new AuditEvent();
            event.setAction(AuditEvent.IdentityUnlocked);
            event.setTarget(user.getName());
            if ( Auditor.isEnabled(event.getAction()) ) {
                Auditor.log(event);
            }
        }

        user.setAuthLockStart(null);
        user.setFailedAuthQuestionAttempts(0);
        user.setFailedLoginAttempts(0);
        
        if(saveAfterUpdate) {
            _context.saveObject(user);
            _context.commitTransaction();
        }
    }
    
    /**
     * 
     * @param user  User to check lock status of
     * @param lt    Type of lock to examine
     * @return  True if the user is locked, otherwise false
     * @throws GeneralException
     */
    public Boolean isUserLocked(Identity user, LockType lt) throws GeneralException {
        Date now = new Date();
        Date lockStart = user.getAuthLockStart();
        if (lockStart == null) {
            return false;
        }

        /*
         * IIQETN-258 :- Perform lockout validation only if AuthQuestion or Login are turn on.
         */
        boolean enabled = false;
        
        //lockoutPeriod: The amount of time that the user must remain locked.
        long lockoutPeriod = 0;

        /*
         * Validate if lock out by Login is turn on.
         */
        if (Configuration.LOGIN_LOCKOUT_DURATION.equals(lt.getMessageKey()) &&
                _context.getConfiguration().getBoolean(Configuration.ENABLE_AUTH_LOCKOUT)) {

            enabled = true;
            lockoutPeriod = _context.getConfiguration().getLong(Configuration.LOGIN_LOCKOUT_DURATION);
        }

        /*
         * Validate if lock out by AuthQuestion is turn on.
         */
        if (Configuration.AUTH_QUESTION_LOCKOUT_DURATION_MILLIS.equals(lt.getMessageKey()) &&
                _context.getConfiguration().getBoolean(Configuration.AUTH_QUESTIONS_ENABLED)) {

            long lockoutPeriodTem = _context.getConfiguration().getLong(Configuration.AUTH_QUESTION_LOCKOUT_DURATION_MILLIS);
            if (enabled) {
                if (lockoutPeriodTem>lockoutPeriod) {
                    lockoutPeriod = lockoutPeriodTem;
                }
            } else {
                enabled = true;
                lockoutPeriod = lockoutPeriodTem;
            }
        }

        /*
         * if (enabled == true) means that Login or AuthQuestion are turn on and
         * we have to assume that the user could be locked out hence we have to
         * get the duration and make sure that the user is still lockout or not.
         */
        if (enabled) {
            //duration: The time that has passed between the lockout and current time.
            long duration = now.getTime() - lockStart.getTime();

            if (duration > lockoutPeriod) {
                // The current identity was lockout, but the time has passed and
                // now it is unlock it.
                return false;
            } else {
                /*
                 * The identity is still lock out.
                 */
                return true;
            }
        }
        return false;
    }
    
    /**
     * Determine if a user is locked by ANY lock
     */
    public Boolean isUserLocked(Identity user) throws GeneralException {
        /*
         * Here we are going to retrieve the value from the field "auth_lock_start",
         * if it is equals to null means that the user is unlocked
         */
        Date lockStart = user.getAuthLockStart();
        if (lockStart == null) {
            return false;
        }
        
        //Check all locks to see if they are still enabled
        for(LockType l : LockType.values()) {
            /*
             * We need to verify all LockType that were turn on
             * LockType.Login and Login.AuthQuestion
             */
            if (isUserLocked(user,l)) {
                return true;
            }
        }

        return false;
    }
}
