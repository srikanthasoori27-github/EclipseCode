/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.request;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Certificationer;
import sailpoint.api.Certificationer.CertificationStartResults;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.api.certification.ManagerCertificationBuilder;
import sailpoint.api.certification.ManagerCertificationBuilder.PartitionedManagerCertificationContext;
import sailpoint.api.certification.ManagerCertificationPartitioner.ManagerCertificationPartition;
import sailpoint.api.certification.ManagerCertificationPartitioner.ManagerCertificationPartition.OneManagerInfo;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationGroup.Status;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskResult.CompletionStatus;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * It will take a partition and create
 * one ore more manager certifications.
 * (depending on managers per partition).
 * 
 * TODO: Rename to CertificationPartitionRequestExecutor and make a generalized class that will handle
 * all cert types.
 *
 * @author tapash.majumder
 */
public class ManagerCertificationPartitionRequestExecutor extends AbstractRequestExecutor {

    private static Log log = LogFactory.getLog(ManagerCertificationPartitionRequestExecutor.class);

    /**
     * This is used to intercept any behavior
     * during the execution of a phase.
     * 
     * Currently it is used to inject test code
     * behavior.
     */
    public interface Interceptor {
        /**
         * This method will be called while executing a phase.
         * 
         * @param phaseExecutor the current phase that is being executed.
         */
        void intercept(PhaseExecutor phaseExecutor) throws GeneralException;
    }
    
    /**
     * List of arguments coming from request definition etc.
     */
    public static final String ARG_PARTITION = "partition";
    public static final String ARG_PARTITION_RESULT = "partitionResult";
    public static final String ARG_CERT_GROUP_ID = "certGroupId";
    public static final String ARG_NUM_PARTITIONS = "numPartitions";

    private static Interceptor interceptor = null;
    
    public static void setInterceptor(Interceptor val) {
        interceptor = val;
    }
    
    /**
     * Different phases of request executor.
     */
    public enum Phase {
        
        GenerateCerts(1, GenerateCertsPhaseExecutor.class),// generates and starts the certs
        CreateParentRelationships(2, CreateParentRelationshipsPhaseExecutor.class), // creates parent-child relationships between certs
        StartCerts(3, StartCertsPhaseExecutor.class), // starts the generated certs
        Finish(4, FinishPhaseExecutor.class); // updates stastistics etc.
        
        // phase number
        private int number;
        // the class that will execute this phase
        private Class<? extends PhaseExecutor> executorClass;
        
        private Phase(int number, Class<? extends PhaseExecutor> clazz) {
            this.number = number;
            this.executorClass = clazz;
        }
        
        // the phase number
        public int getNumber() {
            return number;
        }
        
        // creates an instance of the executor for a phase.
        static PhaseExecutor getExecutor(int phaseNumber, ManagerCertificationPartitionRequestExecutor parent) {
            
            try {
                for (Phase phase : Phase.values()) {
                    if (phase.number == phaseNumber) {
                        
                        return  Reflection.newInstance(phase.executorClass, new Class<?>[] {ManagerCertificationPartitionRequestExecutor.class, Phase.class} , parent, phase);
                    }
                }
                throw new IllegalStateException("No executor found for phaseNumber: " + phaseNumber);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        
    }
    
    // when set to true, means we are terminating so should not proceed further.
    private boolean terminate;
    
    // the real executor depending on the phase
    private PhaseExecutor currentPhaseExecutor;
    
    private SailPointContext context;
    // for status updates etc.
    private TaskMonitor monitor;

    @Override
    public boolean terminate() {

        terminate = true;

        try {
            saveErrorInCertGroup();
        } catch (Exception ex) {
            throw new IllegalStateException("Error Saving certgroup", ex);
        }
        
        return terminate;
    }

    /**
     * Provide an accessor for test classes not in the request package.
     */
    public boolean isTerminated() {
        
        return terminate;
    }

    /**
     * Overrides the execute class in super
     * See{@link AbstractRequestExecutor#execute(SailPointContext, Request, Attributes)}
     */
    public void execute(SailPointContext context, Request request, Attributes<String, Object> args) throws RequestPermanentException, RequestTemporaryException {

        this.context = context;
        this.monitor = new TaskMonitor(context, request);
        
        try {
            initializePhaseExecutor(request, args);
            
            Meter.enterByName(currentPhaseExecutor.getClass().getSimpleName() + ".execute");
            
            if (log.isInfoEnabled()) {
                log.info("Host: " + Util.getHostName() + ", Starting phase: " + currentPhaseExecutor.getClass().getSimpleName() + ", request: " + request.getName());
            }
            
            currentPhaseExecutor.execute();
            
            if (log.isInfoEnabled()) {
                log.info("Host: " + Util.getHostName() + ", Ending phase: " + currentPhaseExecutor.getClass().getSimpleName() + ", request: " + request.getName());
            }

            Meter.exitByName(currentPhaseExecutor.getClass().getSimpleName() + ".execute");
        }
        catch (Throwable t) {
            
            logDbStatus();
            
            addMessageToMasterResult(new Message(Message.Type.Error ,MessageKeys.ERR_EXCEPTION, t));
            
            addStackTraceToRequest(request, t);
            
            throw new RequestPermanentException(t);
        }
    }

    private void initializePhaseExecutor(Request request, Attributes<String, Object> args) {

        currentPhaseExecutor = Phase.getExecutor(request.getPhase(), this);

        if (currentPhaseExecutor == null) {
            throw new IllegalStateException("Could not get current phase executor");
        }
        
        currentPhaseExecutor.setRequest(request);
        currentPhaseExecutor.setArguments(args);
    }

    /**
     * Tries to get more information from db regarding what is going on.
     * Currently it will only dump innodb status.
     * We can add stuff for other dbs to this method in the future.
     */
    private void logDbStatus() {
    
        try {
            boolean logDbStatus = context.getConfiguration().getBoolean("logDbStatus");
            if (!logDbStatus) {
                return;
            }
            
            Connection connection = context.getJdbcConnection();
            if (JdbcUtil.isMySQL(connection)) {
                logInnodbStatus(connection);
            }
        } catch (Throwable t) {
            log.error(t);
        }
    }

    /**
     * will get the innodb status from db.
     */
    private void logInnodbStatus(Connection connection) throws SQLException {
        
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("show engine innodb status");
        resultSet.next();

        log.error("INNODB STATUS");
        log.error(resultSet.getString("status"));
    }
    
    
    // puts entire stack trace of the exception. to the request.
    // this is useful to track down NPE's.
    private void addStackTraceToRequest(Request request, Throwable t) {
        
        request.addMessage(new Message(Type.Error, MessageKeys.ERR_EXCEPTION, Util.stackToString(t)));
    }

    // adds message to the task result for this child request.
    @SuppressWarnings("unused")
	private void addMessageToParitionedResult(Message message) throws RequestTemporaryException {
        
        try {
            TaskResult result = null;
            try {
                result = monitor.lockPartitionResult();
                result.addMessage(message);
                
            } finally {
                if (result != null) {
                    monitor.commitPartitionResult();
                }
            }
        } catch (GeneralException ex) {
            throw new RequestTemporaryException(ex);
        }
    }
    
    // adds message to master result
    private void addMessageToMasterResult(Message message) throws RequestTemporaryException {
        
        try {
            TaskResult result = null;
            try {
                result = monitor.lockMasterResult();
                result.addMessage(message);
            } finally {
                if (result != null) {
                    monitor.commitMasterResult();
                }
            }
        } catch (GeneralException ex) {
            throw new RequestTemporaryException(ex);
        }
    }
    

    // if there was a failure in any task result
    // save the status in cert group.
    private void saveErrorInCertGroup() throws GeneralException {

        String certGroupId = (String) currentPhaseExecutor.getRequest().getAttribute(ARG_CERT_GROUP_ID);
        if (certGroupId == null) {
            throw new IllegalStateException("Could not find certificationGroupId in request.");
        }
        if (log.isInfoEnabled()) {
            log.info("CertGroupId: " + certGroupId);
        }
        
        context.decache();
        CertificationGroup certGroup = ObjectUtil.transactionLock(context, CertificationGroup.class, certGroupId);
        if (certGroup == null) {
            throw new IllegalStateException("Could not lock certGroup");
        }

        certGroup.setStatus(Status.Error);
        
        copyMessagesToCertGroup(certGroup);
        
        context.saveObject(certGroup);
        context.commitTransaction();
    }

    private void copyMessagesToCertGroup(CertificationGroup certGroup) {
    
    	copyMessagesToCertGroup(certGroup, Type.Error);
    	copyMessagesToCertGroup(certGroup, Type.Warn);
    }
    
	private void copyMessagesToCertGroup(CertificationGroup certGroup, Type messageType) {
		
		List<Message> errorMessages = monitor.getTaskResult().getMessagesByType(messageType);
        if (!Util.isEmpty(errorMessages)) {
        	certGroup.addMessages(errorMessages);
        }
	}
    
    /**
     * Each phase has a separate executor.
     */
    public interface PhaseExecutor {
        Phase getPhase();
        Request getRequest();
        void setRequest(Request request);
        Attributes<String, Object> getArguments();
        void setArguments(Attributes<String, Object> args);
        void execute() throws GeneralException;
    }
    
    /**
     * This holds common methods for
     * different phases of cert generator.
     *
     */
    abstract class PhaseExecutorImpl implements PhaseExecutor {

        private Phase phase;
        private Request request;
        private Attributes<String, Object> args;
        
        protected Map<String, String> managerNameToCertificationIdMap = new HashMap<String, String>();
        
        public PhaseExecutorImpl(Phase phase) {
            this.phase = phase;
        }
        
        public Phase getPhase() {
            return phase;
        }
        
        public void setRequest(Request val) {
            this.request = val;
        }

        public void setArguments(Attributes<String, Object> val) {
            this.args = val;
        }
        
        public Request getRequest() {
            return request;
        }
        
        public Attributes<String, Object> getArguments() {
            return args;
        }
        
        /**
         * Looks at the PartitionResults to see
         * if any one of them failed.
         *
         * Note that this was the source of IIQPB-213 due to a bug
         * in TaskResult.getPartitionResults.  That has been fixed, but think
         * about making this a TaskDefinition option so the RequestProcessor
         * can handle it in a generic way.  Something like allMustSucceed='true'
         */
        protected boolean validatePhase(SailPointContext context) 
            throws GeneralException {
            
            boolean valid = true;
            
            List<TaskResult> results = getPartitionResults(context);
            for (TaskResult result : results) {
                if (result.getCompletionStatus() == CompletionStatus.Error || result.getCompletionStatus() == CompletionStatus.Terminated) {
                    valid = false;
                    // jsl - want to know if this happens
                    if (log.isInfoEnabled()) {
                        log.info("Certification phase validator found errors");
                    }
                    break;
                }
            }
            
            return valid;
        }
        
        /**
         * Gets a list of all TaskResults for all the partitions.
         */
        protected List<TaskResult> getPartitionResults(SailPointContext context) 
            throws GeneralException {

            Meter.enterByName("getPartitionResults");
            List<TaskResult> results = getRequest().getTaskResult().getPartitionResults(context);
            Meter.exitByName("getPartitionResults");
            return results;
        }
        
        protected Certification fetchCertification(String managerName, String certificationGroupId) throws GeneralException {

            Filter filter =  Filter.and(
                        Filter.eq("manager", managerName),
                        Filter.eq("certificationGroups.id", certificationGroupId)
                        );
            
            Meter.enterByName(getClass().getSimpleName() + ".getCertification");
            
            Certification certification = context.getUniqueObject(Certification.class, filter);
            
            Meter.exitByName(getClass().getSimpleName() + ".getCertification");

            return certification;
        }
        
        /**
         * Delelte all the certs in the partition. 
         * 
         */
        protected void deleteAllCerts(ManagerCertificationPartition partition) throws GeneralException {
            
            Terminator terminator = new Terminator(context);
            
            for (OneManagerInfo managerInfo : partition.getManagerInfos()) {
                Certification certification = fetchCertification(managerInfo.getManagerName(), partition.getCertificationGroupId());
                if (certification != null) {
                    terminator.deleteObject(certification);
                }
            }
        }

        /**
         * Delelte all the certs generated by the task.
         * 
         */
        protected void deleteAllCerts(String certificationGroupId) throws GeneralException {
            
            Terminator terminator = new Terminator(context);

            Filter filter =  Filter.eq("certificationGroups.id", certificationGroupId);

            List<Certification> certifications = context.getObjects(Certification.class, new QueryOptions(filter));
            for (Certification certification : certifications) {
                terminator.deleteObject(certification);
            }
        }
    }

    abstract class PhaseExecutorWithPartition extends PhaseExecutorImpl {
        
        protected ManagerCertificationPartition partition;

        public PhaseExecutorWithPartition(Phase phase) {
            super(phase);
        }

        protected ManagerCertificationPartition getPartitionFromRequest() {
            
            ManagerCertificationPartition partition = (ManagerCertificationPartition) getRequest().getAttribute(ARG_PARTITION);
            if (partition == null) {
                throw new IllegalStateException("Manager Partition expected.");
            }
            return partition;
        }
        
        protected void loadPartition() throws GeneralException {
            
            partition = getPartitionFromRequest();
        }
        
    }
    
    
    /**
     * This phase takes care of cert gen.
     * This will create a cert for each manager
     * but it won't create all certs for the hierarchy.
     *
     */
    public class GenerateCertsPhaseExecutor extends PhaseExecutorWithPartition {

        private CertificationDefinition definition;
        private Certificationer certificationer;
        
        // the current manager
        private Identity currentManager;
        
        public GenerateCertsPhaseExecutor(Phase phase) {
            super(phase);
        }

        // this will generate certs for all managers in a partition.
        public void execute() throws GeneralException {

            if (interceptor != null) {
                if (log.isInfoEnabled()) {
                    log.info("Intercepting..");
                }
                interceptor.intercept(this);
            }
            
            loadPartition();

            definition = context.getObjectById(CertificationDefinition.class, partition.getCertificationDefinitionId());
            if (definition == null) {
                throw new GeneralException("Could not find certificationDefinition with id: " + partition.getCertificationDefinitionId());
            } else {
                // Load the definition so that its contents are still available after a decache
                definition.load();
            }
            
            certificationer = new Certificationer(context);
            
            for (ManagerCertificationPartition.OneManagerInfo managerInfo : partition.getManagerInfos()) {
                if (!terminate) {
                    generateCertForOneManager(managerInfo);
                }
            }
        }

        // generates cert for 1 manager in the partition.
        private void generateCertForOneManager(ManagerCertificationPartition.OneManagerInfo managerInfo) throws GeneralException {

            if (log.isInfoEnabled()) {
                log.info("Host: " + Util.getHostName() + ": " +"GenerateCertsPhaseExecutor starting for manager: " + managerInfo.getManagerName());
            }

            currentManager = context.getObjectByName(Identity.class, managerInfo.getManagerName());
            if (currentManager == null) {
                throw new IllegalStateException("Manager with name: " + managerInfo.getManagerName() + " not found");
            }
            
            PartitionedManagerCertificationContext certificationContext = createCertificationContext();

            String requestorName = partition.getRequestorName();
            if (requestorName == null) {
                requestorName = Identity.ADMIN_NAME;
            }
            
            Meter.enterByName(getClass().getSimpleName() + ".coreGenerateCert");
            Identity requestor = context.getObjectByName(Identity.class, requestorName);
            Certification cert = certificationer.generateCertification(requestor, certificationContext);
            if (cert == null) {
                addMessageToMasterResult(new Message(Message.Type.Warn, MessageKeys.NOTHING_TO_CERTIFY, certificationContext.generateName(), MessageKeys.USERS_LCASE));
            }
            Meter.exitByName(getClass().getSimpleName() + ".coreGenerateCert");

            context.decache();
            
            if (log.isInfoEnabled()) {
                log.info("Host: " + Util.getHostName() + ": " +"GenerateCertsPhaseExecutor ending for manager: " + managerInfo.getManagerName());
            }
        }

        // creates CertificationContext from CertificationDefinition
        private PartitionedManagerCertificationContext createCertificationContext()
                throws GeneralException {

            ManagerCertificationBuilder builder = new ManagerCertificationBuilder(context, definition, null);
            PartitionedManagerCertificationContext certificationContext = builder.getPartitionedContext(currentManager);
            if (partition.getCertificationGroupId() != null) {
                certificationContext.setCertificationGroups(Arrays.asList(context.getObjectById(CertificationGroup.class, partition.getCertificationGroupId())));
            }
            return certificationContext;
        }
    }
    
    /**
     * This phase will create the cert hierarchy.
     * That is certification.getCertifications() will be
     * populated by this phase.
     *
     */
    public class CreateParentRelationshipsPhaseExecutor extends PhaseExecutorWithPartition {

        public CreateParentRelationshipsPhaseExecutor(Phase phase) {
            super(phase);
        }

        public void execute() throws GeneralException {

            if (interceptor != null) {
                if (log.isInfoEnabled()) {
                    log.info("Intercepting..");
                }
                interceptor.intercept(this);
            }
            
            loadPartition();
            
            if (!validatePhase(context)) {
                throw new GeneralException("Previous results failed. Aborting...");
            }
            
            createParentRelationships();
        }

        // creates parent relationships for all manager certs
        private void createParentRelationships() throws GeneralException {

            for (ManagerCertificationPartition.OneManagerInfo managerInfo : partition.getManagerInfos()) {
                if (log.isInfoEnabled()) {
                    log.info("processing managerInfo: " + managerInfo.getManagerName());
                }
                if (managerInfo.getParentManagerName() == null) {
                    if (log.isInfoEnabled()) {
                        log.info("No parent for manager: " + managerInfo.getManagerName());
                    }
                } else {
                    if (!terminate) {
                        handleOneManagerInfo(managerInfo);
                    }
                }
            }
        }

        // Handles creating relationship for one manager context.
        //
        // Proceed with caution... When updating relationships we need locking here.
        // Without locking the parent cert value may get overwritten by another thread.
        private void handleOneManagerInfo(ManagerCertificationPartition.OneManagerInfo oneManagerInfo)
                throws GeneralException {

            Meter.enterByName(getClass().getSimpleName() + ".handleOneManagerResult");
            
            Certification certification = fetchCertification(oneManagerInfo.getManagerName(), partition.getCertificationGroupId());
            if (certification != null) {
                Certification parentCertification = fetchCertification(oneManagerInfo.getParentManagerName(), partition.getCertificationGroupId());
                if (parentCertification != null) {
                    try {
                        context.decache();
                        certification = ObjectUtil.transactionLock(context, Certification.class, certification.getId());
                        if (certification == null) {
                            throw new GeneralException("Could not obtain transaction lock for certification for manager: " + oneManagerInfo.getManagerName());
                        } else {
                            context.decache();
                            parentCertification = ObjectUtil.transactionLock(context, Certification.class, parentCertification.getId());
                            if (parentCertification == null) {
                                throw new GeneralException("Could not obtain transaction lock for certification for manager: " + oneManagerInfo.getParentManagerName());
                            }
                        }
                        
                        if (log.isInfoEnabled()) {
                            log.info("adding parent manager: " + oneManagerInfo.getParentManagerName() + " to manager: " + oneManagerInfo.getManagerName());
                        }
                        Meter.enterByName(getClass().getSimpleName() + ".add");
                        parentCertification.add(certification);
                        Meter.exitByName(getClass().getSimpleName() + ".add");
    
                        Meter.enterByName(getClass().getSimpleName() + ".saveAndCommit");
                        context.saveObject(parentCertification);
                        context.commitTransaction();
                        Meter.exitByName(getClass().getSimpleName() + ".saveAndCommit");

                    } catch (Throwable t) {
                        context.rollbackTransaction();
                    }
                } else {
                    if (log.isInfoEnabled()) {
                        log.info("Certification for manager (parent): " + oneManagerInfo.getParentManagerName() + " not created");
                    }
                }
            } else {
                if (log.isInfoEnabled()) {
                    log.info("Certification for manager: " + oneManagerInfo.getManagerName() + " not created");
                }
            }
            
            context.decache();

            Meter.exitByName(getClass().getSimpleName() + ".handleOneManagerResult");
        }
    }

    /**
     * Starts the generated certs generating workitems etc.
     *
     */
    public class StartCertsPhaseExecutor extends PhaseExecutorWithPartition {

        private Certificationer certificationer;
        
        public StartCertsPhaseExecutor(Phase phase) {
            super(phase);
        }

        @Override
        public void execute() throws GeneralException {

            if (interceptor != null) {
                if (log.isInfoEnabled()) {
                    log.info("Intercepting..");
                }
                interceptor.intercept(this);
            }
            
            loadPartition();

            if (!validatePhase(context)) {
                throw new GeneralException("Previous results failed. Aborting...");
            }
            
            certificationer = new Certificationer(context);
            
            for (ManagerCertificationPartition.OneManagerInfo managerInfo : partition.getManagerInfos()) {
                if (!terminate) {
                    startCert(managerInfo);
                }
            }
        }
        
        private void startCert(ManagerCertificationPartition.OneManagerInfo managerInfo) throws GeneralException {

            Certification certification = fetchCertification(managerInfo.getManagerName(), partition.getCertificationGroupId());
            if (certification != null) {
                Meter.enterByName(getClass().getSimpleName() + ".startCert");
                certificationer.start(certification, new CertificationStartResults(), false);
                Meter.exitByName(getClass().getSimpleName() + ".startCert");
            } else {
                if (log.isInfoEnabled()) {
                    log.info("No certification was generated for manager: " + managerInfo.getManagerName());
                }
            }
            
            context.decache();
        }
    }
    
    /**
     * Updates statistics etc. after all certifications have been generated.  
     *
     */
    public class FinishPhaseExecutor extends PhaseExecutorImpl {

        public FinishPhaseExecutor(Phase phase) {
            super(phase);
        }

        @Override
        public void execute() throws GeneralException {

            monitor.updateProgress("Finishing Cert Gen...");
            
            if (interceptor != null) {
                if (log.isInfoEnabled()) {
                    log.info("Intercepting..");
                }
                interceptor.intercept(this);
            }

            if (!validatePhase(context)) {
                saveErrorInCertGroup();
                throw new GeneralException("Previous phase failed... aborting.");
            }
            
            updateCertificationGroup();
        }

        // updates CertificationGroup at the end of cert generation.
        private void updateCertificationGroup() throws GeneralException {

            String certGroupId = (String) getRequest().getAttribute(ARG_CERT_GROUP_ID);
            if (certGroupId == null) {
                return;
            }
            
            CertificationGroup certGroup = context.getObjectById(CertificationGroup.class, certGroupId);
            
            CertificationGroup.Status status = CertificationGroup.Status.Active;
            if (certGroup.getDefinition().isStagingEnabled()) {
                status = CertificationGroup.Status.Staged;
            }
            certGroup.setStatus(status);

            copyMessagesToCertGroup(certGroup);
            
            context.saveObject(certGroup);
            context.commitTransaction();

            Certificationer.refreshStatistics(context, certGroup);
        }
    }
}
