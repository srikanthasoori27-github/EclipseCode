/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.activity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ActivityDataSource;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.AttributeDefinition;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 *
 *
 */
public class RACFActivityCollector extends LogFileActivityCollector {
    private static Log log = LogFactory.getLog(RACFActivityCollector.class);

    /**
     */
    public RACFActivityCollector(ActivityDataSource ds) {
        super(ds);
    }

    /**
     * Returns a list of the attributes definitions that makeup the settings
     * that are neccessary for the setup of this activity collector.  We start
     * with the settings from the generic LogFileActivityCollector and remove
     * the ones that don't apply for consistency.
     */
    public List<AttributeDefinition> getDefaultConfiguration() {
        List<AttributeDefinition> config = super.getDefaultConfiguration();

        for ( int i = 0; i < config.size(); i++ ) {
            AttributeDefinition attrDef = config.get(i);

            if ( attrDef == null ) continue;

            String attrName = attrDef.getName();
            if ( attrName.equals(CONFIG_REGULAR_EXPRESSION)
                    || attrName.equals(CONFIG_SKIP_PROBLEM_LINES)
                    || attrName.equals(CONFIG_COMMENT_CHARACTER)
                    || attrName.equals(CONFIG_MULTI_LINED) ) {
                config.remove(i);
            }
        }

        return config;
    }  // getDefaultConfiguration()

    /**
     *
     */
    public CloseableIterator<ApplicationActivity>
                iterate(Map<String, Object> options) throws GeneralException {
        RACFActivityIterator iterator =
                                new RACFActivityIterator(getActivityStream());

        int linesToSkip = getIntAttribute(CONFIG_LINES_TO_SKIP);
        iterator.setLinesToSkip(linesToSkip);

        return iterator;
    }  // iterate(Map<String, Object>)

    /**
     * An iterator that parses through a stream using a BufferedReader.  It
     * reads the file as hasNext() is called to prevent memory problems with
     * large files.
     */
    private class RACFActivityIterator
                           implements CloseableIterator<ApplicationActivity> {
        private BufferedReader _reader;
        private ApplicationActivity _nextElement;
        private int _linesToSkip = 0;
        
        public int getLinesToSkip() { return _linesToSkip; }
        public void setLinesToSkip(int lines) { _linesToSkip = lines; }

        /**
         *
         * @param stream
         * @throws GeneralException
         */
        public RACFActivityIterator(InputStream stream) throws GeneralException {
            try {
                _reader = new BufferedReader(new InputStreamReader(stream));
                skipLines();
            } catch ( Exception e ) {
                throw new GeneralException(e);
            }
        }  // LineIterator(InputStream)
        
        /**
         *
         * @return
         */
        private int skipLines() {
            int i = 0;
            while ( i < _linesToSkip ) {
                String line = null;
                try {
                    line = _reader.readLine();
                } catch ( IOException ex ) {
                }
                if ( line == null ) break;
                i++;
            }
            return i;
        }  // skipLines

        /**
         *
         */
        public boolean hasNext() {
            boolean hasNext = false;
            try {
                String line = null;
                while ( ( line = _reader.readLine() ) != null ) {
                    ApplicationActivity activity = buildActivity(line);
                    if ( activity != null && ! shouldFilter(activity) ) {
                        _nextElement = activity;
                        hasNext = true;
                        break;
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return hasNext;
        } 

        /**
         *
         */
        public ApplicationActivity next() {
            if (_nextElement == null) {
                throw new NoSuchElementException("No more matching elements");
            }
            updateLastProcessed(_nextElement.getTimeStamp());
            return _nextElement;
        } 

        /**
         *
         */
        public void close() {
            if ( _reader != null ) {
                try {
                    _reader.close();
                    _reader = null;
                } catch(IOException e) {
                    if (log.isErrorEnabled())
                        log.error("Error closing reader:" + e.getMessage(), e);
                }
            }
        } 

        /**
         *
         * @param line
         * @return
         */
        private ApplicationActivity buildActivity(String line) {
            if ( line == null ) return null;

                // for the majority of the event types, there are quite a
                // a few common fields in similar positions.  For some
                // event types, these values will be overwritten.
            String eventType = getPosition(line,0,8),    // _EVENT_TYPE
                   eventQual = getPosition(line,9,17),   // _EVENT_QUAL
                   time = getPosition(line,18,26),       // _TIME_WRITTEN
                   date = getPosition(line,27,37),       // _DATE_WRITTEN
                   system = getPosition(line,38,42),     // _SYSTEM_SMFID
                   account = getPosition(line,58,66),    // _EVT_USER_ID
                   terminal = getPosition(line,170,178), // _TERM
                   jobName = getPosition(line,179,187);  // _JOB_NAME

                // change date format from yyyy-mm-dd to mm/dd/yyyy and
                // convert it to a Date
            String tokens[] = date.split("-");
            date = tokens[1] + "/" + tokens[2] + "/" + tokens[0];
            Date eventDate = null;
            try {
                eventDate = Util.stringToDate(date + " " + time);
            } catch ( ParseException ex ) {
                if (log.isWarnEnabled())
                    log.warn("Unable to parse date: " + date + " " + time, ex);
            }

                // set default values for other fields
                // target and action MUST be filled in by all event types below
            String target = null;
            ApplicationActivity.Action action = null;
            ApplicationActivity.Result result =
                                           ApplicationActivity.Result.Success;
            String info = "_TERM='" + terminal + "'";

                // for each event type, parse specific data for that event type
                // adjusting the default activity values set above as necessary
            if ( "ACCESS".equals(eventType) ) {
                target = getPosition(line,281,536);  // ACC_RES_NAME
                result = simpleIsSuccess(eventQual);
                action = ApplicationActivity.Action.Read;
            }
            else if ( "ACCR".equals(eventType) ) {
                target = getPosition(line,571,1594);  // ACCR_PATH_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "ADDGROUP".equals(eventType) ) {
                target = getPosition(line,493,501);  // AG_GRP_ID
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "ADDSD".equals(eventType) ) {
                target = getPosition(line,519,563);  // AD_DS_NAME
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "ADDUSER".equals(eventType) ) {
                target = getPosition(line,503,511);  // AU_USER_ID
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "ADDVOL".equals(eventType) ) {
                target = getPosition(line,281,536);  // ADV_RES_NAME
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "ALTDSD".equals(eventType) ) {
                target = getPosition(line,519,563);  // ALD_DS_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "ALTGROUP".equals(eventType) ) {
                target = getPosition(line,493,501);  // ALG_GRP_ID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "ALTUSER".equals(eventType) ) {
                target = getPosition(line,517,525);  // ALU_USER_ID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "APPCLU".equals(eventType) ) {
                target = getPosition(line,281,536);  // APPC_RES_NAME
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "CHAUDIT".equals(eventType) ) {
                target = getPosition(line,571,1594);  // CAUD_PATH_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "CHDIR".equals(eventType) ) {
                target = getPosition(line,571,1594);  // CDIR_PATH_NAME
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "CHKFOWN".equals(eventType) ) {
                target = getPosition(line,571,1594);  // CFOW_PATH_NAME
                action = ApplicationActivity.Action.Read;
                if ( eventQual != null
                        && ! "OWNER".equals(eventQual)
                        && ! "NOTOWNER".equals(eventQual) ) {
                    result = ApplicationActivity.Result.Failure;
                }
            }
            else if ( "CHKPRIV".equals(eventType) ) {
                target = account;
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "CHMOD".equals(eventType) ) {
                target = getPosition(line,571,1594);  // CMOD_PATH_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "CHOWN".equals(eventType) ) {
                target = getPosition(line,571,1594);  // COWN_PATH_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "CKOWN2".equals(eventType) ) {
                target = getPosition(line,571,1594);  // CKO2_PATH_NAME
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "CLASNAME".equals(eventType) ) {
                account = system;
                target = getPosition(line,43,51);  // RINC_CLASS_NAME
                action = ApplicationActivity.Action.Create;
                terminal = null;
                info = null;
            }
            else if ( "CLRSETID".equals(eventType) ) {
                target = getPosition(line,571,1594);  // CSID_PATH_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "CONNECT".equals(eventType) ) {
                target = getPosition(line,493,501);  // CON_USER_ID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "DACCESS".equals(eventType) ) {
                target = getPosition(line,571,1594);  // DACC_PATH_NAME
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "DEFINE".equals(eventType) ) {
                target = getPosition(line,281,536);  // DEF_RES_NAME
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);

                String className = getPosition(line,548,556),  // DEF_CLASS
                       model = getPosition(line,557,812);      // DEF_MODEL_NAME
                info += " DEF_CLASS='" + className +
                                           "' DEF_MODEL_NAME='" + model + "'";
            }
            else if ( "DELDSD".equals(eventType) ) {
                target = getPosition(line,519,563);  // DELD_DS_NAME
                action = ApplicationActivity.Action.Delete;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "DELGROUP".equals(eventType) ) {
                target = getPosition(line,493,501);  // DELG_GRP_ID
                action = ApplicationActivity.Action.Delete;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "DELRES".equals(eventType) ) {
                target = getPosition(line,281,536);  // DELR_RES_NAME
                action = ApplicationActivity.Action.Delete;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "DELUSER".equals(eventType) ) {
                target = getPosition(line,493,501);  // DELU_USER_ID
                action = ApplicationActivity.Action.Delete;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "DELVOL".equals(eventType) ) {
                target = getPosition(line,281,536);  // DELV_RES_NAME
                action = ApplicationActivity.Action.Delete;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "DIRSRCH".equals(eventType) ) {
                target = getPosition(line,571,1594);  // DSCH_PATH_NAME
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "DSAF".equals(eventType) ) {
                account = getPosition(line,75,83);     // DSAF_EVT_USER_ID
                terminal = getPosition(line,187,195);  // DSAF_TERM
                jobName = getPosition(line,196,204);   // DSAF_JOB_NAME

                String dataset = getPosition(line,268,312), // DSAF_DATA_SET
                       seclLink = getPosition(line,43,59);  // DSAF_SECL_LINK

                target = dataset;
                action = ApplicationActivity.Action.Update;
                info = "_TERM='" + terminal +
                                        "' DSAF_JOB_NAME='" + jobName +
                                        "' DSAF_SECL_LINK='" + seclLink + "'";
            }
            else if ( "EXESETID".equals(eventType) ) {
                target = getPosition(line,582,592);  // ESID_NEW_EFF_UID
                action = ApplicationActivity.Action.Authenticate;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "FACCESS".equals(eventType) ) {
                target = getPosition(line,571,1594);  // FACC_PATH_NAME
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "GENERAL".equals(eventType) ) {
                target = getPosition(line,281,289);  // GEN_CLASS
                action = ApplicationActivity.Action.Update;
            }
            else if ( "GETPSENT".equals(eventType) ) {
                target = getPosition(line,604,614);  // GPST_TGT_PID
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "INITACEE".equals(eventType) ) {
                target = account;
                if ( "SUCCESSDER".equals(eventQual) ) {
                    action = ApplicationActivity.Action.Delete;
                } else {
                    action = ApplicationActivity.Action.Create;
                }
                if ( "SUCCESSREG".equals(eventQual)
                        || "SUCCESSDER".equals(eventQual)
                        || "SUCCESSRCA".equals(eventQual) ) {
                    result = ApplicationActivity.Result.Success;
                } else {
                    result = ApplicationActivity.Result.Failure;
                }
            }
            else if ( "INITOEDP".equals(eventType) ) {
                target = getPosition(line,516,526);  // IOEP_OLD_EFF_UID
                action = ApplicationActivity.Action.Start;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "IPCCHK".equals(eventType) ) {
                target = getPosition(line,646,656);  // ICHK_ID
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "IPCCTL".equals(eventType) ) {
                target = getPosition(line,816,826);  // ICTL_ID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "IPCGET".equals(eventType) ) {
                target = getPosition(line,662,672);  // IGET_ID
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "JOBINIT".equals(eventType) ) {
                target = jobName;

                action = ApplicationActivity.Action.Start;
                if ( "TERM".equals(eventQual)
                        || "SUCCESST".equals(eventQual)
                        || "RACINITD".equals(eventQual) ) {
                    action = ApplicationActivity.Action.Stop;
                }

                if ( eventQual != null
                        && ! "SUCCESS".equals(eventQual)
                        && ! "TERM".equals(eventQual)
                        && ! "SUCCESSI".equals(eventQual)
                        && ! "SUCCESST".equals(eventQual)
                        && ! "RACINITI".equals(eventQual)
                        && ! "RACINITD".equals(eventQual)
                        && ! "SUCCESSP".equals(eventQual) ) {
                    result = ApplicationActivity.Result.Failure;
                }
            }
            else if ( "KILL".equals(eventType) ) {
                target = getPosition(line,604,614);  // KILL_TGT_PID
                action = ApplicationActivity.Action.Stop;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "LINK".equals(eventType) ) {
                target = getPosition(line,571,1594);  // LINK_PATH_NAME
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);

                String requestPath2 = getPosition(line,1650,2673);  // LINK_REQUEST_PATH2
                info += " LINK_REQUEST_PATH2='" + requestPath2 + "'";
            }
            else if ( "MKDIR".equals(eventType) ) {
                target = getPosition(line,571,1594);  // MDIR_PATH_NAME
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "MKNOD".equals(eventType) ) {
                target = getPosition(line,571,1594);  // MNOD_PATH_NAME
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "MNTFSYS".equals(eventType) ) {
                target = getPosition(line,571,1594);  // MFS_PATH_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "OPENFILE".equals(eventType) ) {
                target = getPosition(line,571,1594);  // OPEN_PATH_NAME
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "OPENSTTY".equals(eventType) ) {
                target = getPosition(line,604,614);  // OSTY_TGT_PID
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "PASSWORD".equals(eventType) ) {
                target = getPosition(line,281,289);  // PWD_OWN_ID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "PERMIT".equals(eventType) ) {
                target = getPosition(line,502,757);  // PERM_RES_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "PTRACE".equals(eventType) ) {
                target = getPosition(line,637,647);  // PTRC_TGT_PID
                action = ApplicationActivity.Action.Read;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "RACDCERT".equals(eventType) ) {
                target = getPosition(line,979,1023);  // RACD_CERT_DS
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "RACFINIT".equals(eventType) ) {
                account = system;  // the system initializes RACF

                String datasetName = getPosition(line,43,87),  // RINI_DATASET_NAME
                       datasetVol = getPosition(line,88,94),   // RINI_DATASET_VOL
                       datasetUnit = getPosition(line,95,98);  // RINI_DATASET_UNIT

                target = datasetName + " UNIT=" + datasetUnit +
                                                         ",VOL=" + datasetVol;
                action = ApplicationActivity.Action.Start;
                terminal = null;
                info = null;
            }
            else if ( "RACLINK".equals(eventType) ) {
                target = getPosition(line,506,514);  // RACL_SOURCE_ID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);

                String tgtId = getPosition(line,524,532);  // RACL_TGT_ID
                info += " RACL_TGT_ID='" + tgtId + "'";
            }
            else if ( "RALTER".equals(eventType) ) {
                target = getPosition(line,511,766);  // RALT_RES_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "RDEFINE".equals(eventType) ) {
                target = getPosition(line,511,766);  // RDEF_RES_NAME
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "RDELETE".equals(eventType) ) {
                target = getPosition(line,511,766);  // RDEL_RES_NAME
                action = ApplicationActivity.Action.Delete;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "REMOVE".equals(eventType) ) {
                target = getPosition(line,493,501);  // REM_USER_ID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "RENAMEDS".equals(eventType) ) {
                target = getPosition(line,281,536);  // REN_RES_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);

                String newResName = getPosition(line,537,792);  // REN_NEW_RES_NAME
                info += " REN_NEW_RES_NAME='" + newResName + "'";
            }
            else if ( "RENAMEF".equals(eventType) ) {
                target = getPosition(line,571,1594);  // RENF_PATH_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);

                String path2 = getPosition(line,537,792);  // RENF_PATH2
                info += " RENF_PATH2='" + path2 + "'";
            }
            else if ( "RMDIR".equals(eventType) ) {
                target = getPosition(line,571,1594);  // RDIR_PATH_NAME
                action = ApplicationActivity.Action.Delete;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "RVARY".equals(eventType) ) {
                target = system;
                action = ApplicationActivity.Action.Update;

                String rvarSpecified = getPosition(line,484,1508);  // RVAR_SPECIFIED
                info += " RVAR_SPECIFIED='" + rvarSpecified + "'";

                result = simpleIsSuccess(eventQual);
            }
            else if ( "SETEGID".equals(eventType) ) {
                target = getPosition(line,604,614);  // SEGI_GID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "SETEUID".equals(eventType) ) {
                target = getPosition(line,604,614);  // SEUI_UID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "SETGID".equals(eventType) ) {
                target = getPosition(line,604,614);  // SGI_GID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "SETGROUP".equals(eventType) ) {
                target = getPosition(line,516,526);  // SETG_OLD_EFF_UID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "SETROPTS".equals(eventType) ) {
                target = system;
                action = ApplicationActivity.Action.Update;

                String setrSpecified = getPosition(line,484,1508);  // SETR_SPECIFIED
                info += " SETR_SPECIFIED='" + setrSpecified + "'";

                result = simpleIsSuccess(eventQual);
            }
            else if ( "SETUID".equals(eventType) ) {
                target = getPosition(line,604,614);  // SUI_UID
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "SYMLINK".equals(eventType) ) {
                target = getPosition(line,571,1594);  // SYML_PATH_NAME
                action = ApplicationActivity.Action.Create;
                result = simpleIsSuccess(eventQual);

                String symlinkData = getPosition(line,1650,2673);  // SYML_SYMLINK_DATA
                info += " SYML_SYMLINK_DATA='" + symlinkData + "'";
            }
            else if ( "TERMOEDP".equals(eventType) ) {
                target = getPosition(line,516,526);  // TOEP_OLD_EFF_UID
                action = ApplicationActivity.Action.Stop;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "UMNTFSYS".equals(eventType) ) {
                target = getPosition(line,571,1594);  // MFS_PATH_NAME
                action = ApplicationActivity.Action.Update;
                result = simpleIsSuccess(eventQual);
            }
            else if ( "UNLINK".equals(eventType) ) {
                target = getPosition(line,571,1594);  // UFS_PATH_NAME
                action = ApplicationActivity.Action.Delete;
                result = simpleIsSuccess(eventQual);
            }
            else {
                if (log.isWarnEnabled())
                    log.warn("Unknown event type: " + eventType);
            }

            ApplicationActivity activity = new ApplicationActivity();
            activity.setUser(account);
            activity.setTimeStamp(eventDate);
            activity.setDataSource(system);
            activity.setAction(action);
            activity.setTarget(target);
            activity.setResult(result);
            activity.setInfo(info);

            return activity;
        }

        /**
         *
         * @param line
         * @param start
         * @param end
         * @return
         */
        private String getPosition(String line, int start, int end) {
            if ( line == null || line.length() < end ) return "";

            return line.substring(start, end).trim();
        }

        /**
         *
         * @param value
         * @return
         */
        private ApplicationActivity.Result simpleIsSuccess(String value) {
            if ( value == null || value.length() == 0 || "SUCCESS".equals(value) )
                return ApplicationActivity.Result.Success;
            else
                return ApplicationActivity.Result.Failure;
        }
    }  // class LineIterator

}  // class RACFActivityCollector
