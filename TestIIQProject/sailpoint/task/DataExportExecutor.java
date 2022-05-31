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
import sailpoint.object.Certification;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Tag;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.reporting.custom.DataExporter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;


/**
 * Exports denormalized view of certain IIQ objects to an external datasource.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class DataExportExecutor extends AbstractTaskExecutor {

    private static Log log = LogFactory.getLog(DataExportExecutor.class);

    private static final String OUT_IDS_EXPORTED = "identitiesExported";
    private static final String OUT_ACCTS_EXPORTED = "accountsExported";
    private static final String OUT_CERTS_EXPORTED = "certificationsExported";
    private static final String OUT_IDS_PRUNED = "identitiesPruned";
    private static final String OUT_ACCTS_PRUNED = "accountsPruned";
    private static final String OUT_CERTS_PRUNED = "certificationsPruned";

    private SailPointContext context;
    private DataExporter exporter;
    private TaskResult taskResult;

    private boolean terminate = false;;
    
    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args) throws Exception {

        this.taskResult = result;

        int totalIdentities = 0;
        int totalLinks = 0;
        int totalCerts = 0;

        this.context = context;

        String driverClass = args.getString("driverClass");
        String dbUrl = args.getString("dbUrl");
        String dbUser = args.getString("username");
        String dbPassword = args.getString("password");
        String database = args.getString("database");

        DataExporter.DatabaseType dbType =
                DataExporter.DatabaseType.valueOf(database);
        exporter = new DataExporter(context, dbType);

        boolean exportIdentities = args.getBoolean("exportIdentities");
        boolean identitiesFullExport = "full".equals(args.getString("identitiesExportMode"));
        String identityEportFilter = args.getString("identitiesExportFilter");

        boolean exportLinks = args.getBoolean("exportAccounts");
        boolean linksFullExport = "full".equals(args.getString("accountsExportMode"));
        String linkEportFilter = args.getString("accountsExportFilter");

        boolean exportCerts = args.getBoolean("exportCertifications");
        boolean certFullExport = "full".equals(args.getString("certificationsExportMode"));
        String certEportFilter = args.getString("certificationsExportFilter");

        Connection conn = null;
        String unencryptedPass = context.decrypt(dbPassword);
        try {

            conn = JdbcUtil.getConnection(driverClass, null, dbUrl, dbUser, unencryptedPass);

            conn.setAutoCommit(false);

            if (exportIdentities){
                updateProgress(context, result, "Exporting identities");
                totalIdentities = export(conn,  Identity.class, identityEportFilter, identitiesFullExport,
                        "Identities", args);
            }

            if (exportLinks){
                updateProgress(context, result, "Exporting accounts");
                totalLinks = export(conn, Link.class, linkEportFilter, linksFullExport,
                        "Accounts", args);
            }

            if (exportCerts){
                updateProgress(context, result, "Exporting Tags");
                export(conn, Tag.class, null, true, "Tags", args);
                
                updateProgress(context, result, "Exporting certifications");
                totalCerts = export(conn, Certification.class, certEportFilter, certFullExport,
                        "Certifications", args);
            }

        } finally {
            if (conn != null && !conn.isClosed())
                JdbcUtil.closeConnection(conn);
        }

        result.setAttribute(OUT_IDS_EXPORTED, totalIdentities);
        result.setAttribute(OUT_ACCTS_EXPORTED, totalLinks);
        result.setAttribute(OUT_CERTS_EXPORTED, totalCerts);
    }

    /**
     * Adds a warning message for any attributes which are missing columns in the export database.
     * @param conn
     * @param clazz
     * @throws GeneralException
     */
    private void addMissingAttrWarnings(Connection conn, Class clazz) throws GeneralException{
        List<String> missingAttrs = this.exporter.getAttributesMissingFromExport(conn, clazz);
        if (missingAttrs != null && !missingAttrs.isEmpty()){
            String classType = Identity.class.equals(clazz) ? MessageKeys.TASK_DATA_EXPORT_TYPE_IDENTITY :
                    MessageKeys.TASK_DATA_EXPORT_TYPE_LINK;
            for(String attr : missingAttrs){
                this.taskResult.addMessage(Message.warn(MessageKeys.TASK_WARN_MISSING_ATTR_COLUMN, classType, attr));
            }
        }
    }

    public boolean terminate() {
        terminate = true;
        return terminate;
    }

    public int export(Connection conn, Class<? extends SailPointObject> clazz,
                      String filter, boolean fullExport, String friendlyObjName, Attributes<String,Object> args)
            throws GeneralException {

        if (log.isDebugEnabled())
            log.debug("DataExport: Beginning export of object type:" + friendlyObjName);

        String taskDefId = this.taskResult.getDefinition().getId();
        Date startDate = new Date();

        QueryOptions ops = new QueryOptions();
        ops.setScopeResults(false);
        List<Filter> filters = new ArrayList<Filter>();
        if (fullExport) {
            exporter.deleteAll(conn, clazz);
        } else {

            if (log.isDebugEnabled())
                log.debug("DataExport: Pruning deleted objects for type " + friendlyObjName);

            updateProgress(context, taskResult, "Pruning deleted " + friendlyObjName);
            int prunedRecs = exporter.pruneDeletedObjects(conn, clazz);

            String prunedRecAttr = OUT_IDS_PRUNED;
            if (clazz.equals(Link.class))
                prunedRecAttr = OUT_ACCTS_PRUNED;
            else if (clazz.equals(Certification.class))
                prunedRecAttr = OUT_CERTS_PRUNED;
            
            this.taskResult.setAttribute(prunedRecAttr, prunedRecs);

            try {
                Date dt = exporter.calculateLastExport(conn, clazz, taskDefId);
                if (dt != null){
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
        updateProgress(context, taskResult, "Beginning export of a total of " + total + " " + friendlyObjName);
        if (log.isDebugEnabled())
            log.debug("Beginning export of a total of " + total + " " + friendlyObjName);
        Iterator<Object []> iter = context.search(clazz, ops, Arrays.asList("id"));
        int cnt = 0;
        while (iter.hasNext() && !terminate) {
            String id = (String)iter.next()[0];
            boolean success = false;
            success = exporter.export(conn, clazz, id, fullExport, args);
            if (success)
                cnt++;
            if (cnt % 100 == 0) {
                context.decache();
                updateProgress(context, taskResult, "Exporting " + friendlyObjName + ". Last count was " + cnt);
            }
        }
        
        exporter.completeExport(conn, taskDefId, clazz, startDate);

        if (exporter.getMessages() != null && !exporter.getMessages().isEmpty()){
            this.taskResult.addMessages(exporter.getMessages());
        }

        // Add warnings for any missing columns in the export database
        addMissingAttrWarnings(conn, clazz);

        exporter.clearMessages();
        
        if (terminate) {
        	updateProgress(context, taskResult, "Export terminated. " + " Last count was " + cnt);
        	taskResult.setTerminated(true);
        }
        
        return cnt;
    }
 
}
