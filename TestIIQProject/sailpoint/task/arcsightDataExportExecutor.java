/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.reporting.custom.arcsightDataExporter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;


public class arcsightDataExportExecutor extends AbstractTaskExecutor {

    private static Log log = LogFactory.getLog(arcsightDataExportExecutor.class);

    private static final String OUT_IDS_EXPORTED = "identitiesExported";
    private static final String OUT_AUDITS_EXPORTED = "auditsExported";
    private static final String OUT_IDS_ARCSIGHT_PRUNED = "identitiesPruned";


    private static final String OBJNAME_IDENTITY = "Identity";
    private static final String OBJNAME_AUDIT = "Audit";


    private SailPointContext context;
    private arcsightDataExporter exporter;
    private TaskResult taskResult;

    private boolean terminate = false;


    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args) throws Exception {

        this.taskResult = result;

        int totalIdentities = 0;
        int totalAudits = 0;


        this.context = context;

        String driverClass = args.getString("driverClass");
        String dbUrl = args.getString("dbUrl");
        String dbUser = args.getString("username");
        String dbPassword = args.getString("password");
        String database = args.getString("database");

        arcsightDataExporter.DatabaseType dbType =
                arcsightDataExporter.DatabaseType.valueOf(database);
        exporter = new arcsightDataExporter(context, dbType);

        boolean exportIdentities = args.getBoolean("exportIdentities");
        boolean identitiesFullExport = "full".equals(args.getString("identitiesExportMode"));
        String identityEportFilter = args.getString("identitiesExportFilter");

        boolean exportAuditEvents = args.getBoolean("exportAudits");
        boolean auditEventsFullExport = "full".equals(args.getString("auditsExportMode"));
        String auditEventsEportFilter = args.getString("auditsExportFilter");

        Connection conn = null;
        String unencryptedPass = context.decrypt(dbPassword);
        try {

            conn = JdbcUtil.getConnection(driverClass, null, dbUrl, dbUser, unencryptedPass);

            conn.setAutoCommit(false);

            if (exportIdentities){
                updateProgress(context, result, "Exporting " + OBJNAME_IDENTITY);
                totalIdentities = export(conn,  Identity.class, identityEportFilter, identitiesFullExport, OBJNAME_IDENTITY, args);
            }

            if (exportAuditEvents){
                updateProgress(context, result, "Exporting " + OBJNAME_AUDIT);                
                totalAudits = export(conn, AuditEvent.class, auditEventsEportFilter, auditEventsFullExport,
                        OBJNAME_AUDIT, args);
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
        result.setAttribute(OUT_AUDITS_EXPORTED, totalAudits);
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
            log.debug("export: Beginning export of object type:" + objName);

        String taskDefId = this.taskResult.getDefinition().getId();
        Date startDate = new Date();

        QueryOptions ops = new QueryOptions();
        ops.setScopeResults(false);
        List<Filter> filters = new ArrayList<Filter>();
        int total = 0, cnt =0;
        Iterator<Object []> iter=null;
        Set<String> identityIds = new HashSet<String>();

            if (fullExport) {
                if(log.isDebugEnabled())
                    log.debug("export: Truncating all the data as full export option is selected");
                exporter.deleteAllarcsight(conn, clazz);
            } else {
                if(clazz.equals(Identity.class)){
                    if (log.isDebugEnabled())
                        log.debug("export: Pruning deleted objects for Identity");
                    updateProgress(context, taskResult, "Pruning deleted Identities");

                    int prunedRecs = exporter.pruneDeletedObjects(conn, clazz, args);

                    if (log.isDebugEnabled())
                        log.debug("export: Pruning deleted objects for Link");

                    updateProgress(context, taskResult, "Pruning deleted Links");
                    
                    prunedRecs += exporter.pruneDeletedObjects(conn, Link.class, args);
                    this.taskResult.setAttribute(OUT_IDS_ARCSIGHT_PRUNED, prunedRecs);
                }
            }
            try {
                Date dt = exporter.calculateLastExport(conn, clazz, taskDefId);
                if (!fullExport && dt != null){
                    if(clazz.equals(AuditEvent.class))
                        filters.add(Filter.gt("created", dt));
                    else
                        filters.add(Filter.gt("modified", dt));
                }
            } catch(SQLException e){
                throw new GeneralException(e);
            }
            if (filter != null && filter.trim().length() > 0) {
                filters.add(Filter.compile(filter.trim()));
            }
            if (!filters.isEmpty()){
                ops.add(Filter.and(filters));
            }

            ops.setCloneResults(true);
            
            total = context.countObjects(clazz, ops);
            updateProgress(context, taskResult, "Beginning export of a total of " + total + " " + objName);
            if (log.isDebugEnabled())
                log.debug("export: Beginning export of a total of " + total + " " + objName);
            iter = context.search(clazz, ops, Arrays.asList("id"));

            //export Audit and Identity
            while (iter.hasNext() && !terminate) {
                String id = (String)iter.next()[0];
                cnt += exporter.exportarcsight(conn, clazz, id, null, fullExport, args);
                identityIds.add(id);
                if (cnt % 100 == 0) {
                    context.decache();
                    updateProgress(context, taskResult, "Exporting " + objName + ". Last count was " + cnt);
                }
            }

            //Link will be exported only if incremental export is selected.
            if(!fullExport && clazz.equals(Identity.class)){
                iter = context.search(Link.class, ops, Arrays.asList("id", "identity"));
                //Incremental export of Link
                while (iter.hasNext() && !terminate) {
                    Object[] entry = iter.next();
                    String id = (String)entry[0];
                    Identity identity = (Identity) entry[1];
                    //We want to export Link only if corresponding Identity is not processed 
                    if(!identityIds.contains(identity.getId())){
                        cnt += exporter.exportarcsight(conn, Link.class, id, identity, fullExport, args);
                        if (cnt % 100 == 0) {
                            context.decache();
                            updateProgress(context, taskResult, "Exporting " + objName + ". Last count was " + cnt);
                        }
                    }
                }
            }

        
        exporter.completearcsightExport(conn, taskDefId, clazz, startDate);

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