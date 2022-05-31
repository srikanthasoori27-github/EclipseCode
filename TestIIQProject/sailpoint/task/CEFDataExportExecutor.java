/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.SyslogEvent;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.reporting.custom.CEFDataExporter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;


public class CEFDataExportExecutor extends AbstractTaskExecutor {

    private static Log log = LogFactory.getLog(CEFDataExportExecutor.class);

    private static final String OUT_IDS_EXPORTED = "identitiesExported";
    private static final String OUT_ACCTS_EXPORTED = "accountsExported";
    private static final String OUT_AUDITS_EXPORTED = "auditsExported";
    private static final String OUT_SYDLOGS_EXPORTED = "syslogsExported";
    private static final String OUT_IDS_CEF_PRUNED = "identitiesPruned";
    private static final String OUT_ACCTS_CEF_PRUNED = "accountsPruned";
    private static final String OUT_AUDIT_CEF_PRUNED = "auditsPruned";
    private static final String OUT_SYSLOG_CEF_PRUNED = "syslogsPruned";
    
    private static final String OBJNAME_IDENTITY = "Identity";
    private static final String OBJNAME_LINK = "Link";
    private static final String OBJNAME_AUDIT = "Audit";
    private static final String OBJNAME_SYS_LOG = "Syslog";    

    private SailPointContext context;
    private CEFDataExporter exporter;
    private TaskResult taskResult;

    private boolean terminate = false;

    
    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args) throws Exception {

        this.taskResult = result;

        int totalIdentities = 0;
        int totalLinks = 0;
        int totalAudits = 0;
        int totalSyslogs = 0;

        this.context = context;

        String driverClass = args.getString("driverClass");
        String dbUrl = args.getString("dbUrl");
        String dbUser = args.getString("username");
        String dbPassword = args.getString("password");
        String database = args.getString("database");

        CEFDataExporter.DatabaseType dbType =
        CEFDataExporter.DatabaseType.valueOf(database);
        exporter = new CEFDataExporter(context, dbType);

        boolean exportIdentities = args.getBoolean("exportIdentities");
        boolean identitiesFullExport = "full".equals(args.getString("identitiesExportMode"));
        String identityEportFilter = args.getString("identitiesExportFilter");

        boolean exportLinks = args.getBoolean("exportAccounts");
        boolean linksFullExport = "full".equals(args.getString("accountsExportMode"));
        String linkEportFilter = args.getString("accountsExportFilter");

        boolean exportAuditEvents = args.getBoolean("exportAudits");
        boolean auditEventsFullExport = "full".equals(args.getString("auditsExportMode"));
        String auditEventsEportFilter = args.getString("auditsExportFilter");

        boolean exportSysLogsEvents = args.getBoolean("exportSysLogs");
        boolean sysLogsEventsFullExport = "full".equals(args.getString("syslogsExportMode"));
        String sysLogsEventsEportFilter = args.getString("syslogsExportFilter");
        
        Connection conn = null;
        String unencryptedPass = context.decrypt(dbPassword);
        try {

            conn = JdbcUtil.getConnection(driverClass, null, dbUrl, dbUser, unencryptedPass);

            conn.setAutoCommit(false);

            if (exportIdentities){
                updateProgress(context, result, "Exporting " + OBJNAME_IDENTITY);
                totalIdentities = export(conn,  Identity.class, identityEportFilter, identitiesFullExport, OBJNAME_IDENTITY, args);
            }

            if (exportLinks){
                updateProgress(context, result, "Exporting " + OBJNAME_LINK);
                totalLinks = export(conn, Link.class, linkEportFilter, linksFullExport,
                        OBJNAME_LINK, args);
            }

            if (exportAuditEvents){
                updateProgress(context, result, "Exporting " + OBJNAME_AUDIT);                
                totalAudits = export(conn, AuditEvent.class, auditEventsEportFilter, auditEventsFullExport,
                        OBJNAME_AUDIT, args);
            }
            
            if (exportSysLogsEvents){
                updateProgress(context, result, "Exporting " + OBJNAME_SYS_LOG);                
                totalSyslogs = export(conn, SyslogEvent.class, sysLogsEventsEportFilter, sysLogsEventsFullExport,
                        OBJNAME_SYS_LOG, args);
            }
        } catch(Throwable t){
            if(t.getCause() instanceof ClassNotFoundException)
                throw new ClassNotFoundException(((GeneralException)t).getMessageInstance().toString(),t);
            else
                throw new GeneralException(t);
        }
        finally {
            if (conn != null && !conn.isClosed())
                JdbcUtil.closeConnection(conn);
        }

        result.setAttribute(OUT_IDS_EXPORTED, totalIdentities);
        result.setAttribute(OUT_ACCTS_EXPORTED, totalLinks);
        result.setAttribute(OUT_AUDITS_EXPORTED, totalAudits);
        result.setAttribute(OUT_SYDLOGS_EXPORTED, totalSyslogs);
    }

    public boolean terminate() {
        terminate = true;
        return terminate;
    }

    /**
     * @param conn
     * @param clazz
     * @param filter
     * @param fullExport
     * @param objName
     * @param args
     * @return
     * @throws GeneralException
     */
    
    public int export(Connection conn, Class<? extends SailPointObject> clazz,
                      String filter, boolean fullExport, String objName, Attributes<String,Object> args)
            throws GeneralException {

        if (log.isDebugEnabled())
            log.debug("CEFDataExport: Beginning export of object type:" + objName);

        String taskDefId = this.taskResult.getDefinition().getId();
        Date startDate = new Date();

        QueryOptions ops = new QueryOptions();
        ops.setScopeResults(false);
        List<Filter> filters = new ArrayList<Filter>();
        if (fullExport) {
            log.debug("Truncating all the data as full export option is selected");
            exporter.deleteAllCEF(conn, clazz);
        } else {

            if (log.isDebugEnabled())
                log.debug("CEFDataExport: Pruning deleted objects for type " + objName);

            updateProgress(context, taskResult, "Pruning deleted " + objName);
        
            int prunedRecs = exporter.pruneDeletedObjects(conn, clazz);

            String prunedRecAttr = OUT_IDS_CEF_PRUNED;
            if (clazz.equals(Link.class))
                prunedRecAttr = OUT_ACCTS_CEF_PRUNED;
            else if (clazz.equals(AuditEvent.class))
                prunedRecAttr = OUT_AUDIT_CEF_PRUNED;
            else if (clazz.equals(SyslogEvent.class))
                prunedRecAttr = OUT_SYSLOG_CEF_PRUNED;            
            
            this.taskResult.setAttribute(prunedRecAttr, prunedRecs);

            try {
                Date dt = exporter.calculateLastExport(conn, clazz, taskDefId);
                if (dt != null){
                    if(clazz.equals(AuditEvent.class) || clazz.equals(SyslogEvent.class))
                        filters.add(Filter.gt("created", dt));
                    else
                        filters.add(Filter.gt("modified", dt));
                }
            } catch (SQLException e) {
                throw new GeneralException(e);
            }
        }
        if (filter != null && filter.trim().length() > 0) {
            filters.add(Filter.compile(filter.trim()));
        }
        
        if (!filters.isEmpty()){
            ops.add(Filter.and(filters));
        }

        ops.setCloneResults(true);

        int total = context.countObjects(clazz, ops);
        updateProgress(context, taskResult, "Beginning export of a total of " + total + " " + objName);
        if (log.isDebugEnabled())
            log.debug("Beginning export of a total of " + total + " " + objName);
        Iterator<Object []> iter = context.search(clazz, ops, Arrays.asList("id"));
        int cnt = 0;
        while (iter.hasNext() && !terminate) {
            String id = (String)iter.next()[0];
            boolean success = false;
            success = exporter.exportCEF(conn, clazz, id, fullExport, args, objName);
            if (success)
                cnt++;
            if (cnt % 100 == 0) {
                context.decache();
                updateProgress(context, taskResult, "Exporting " + objName + ". Last count was " + cnt);
            }
        }
        
        exporter.completeCEFExport(conn, taskDefId, clazz, startDate);

        if (exporter.getMessages() != null && !exporter.getMessages().isEmpty()){
            this.taskResult.addMessages(exporter.getMessages());
            }

        exporter.clearMessages();
        
        if (terminate) {
        	updateProgress(context, taskResult, "Export terminated. " + " Last count was " + cnt);
        	taskResult.setTerminated(true);
        	}
        return cnt;
        }
    }