/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.task;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationScheduler;
import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.CertificationNamer;
import sailpoint.api.certification.ManagerCertificationHelper;
import sailpoint.api.certification.ManagerCertificationPartitioner;
import sailpoint.api.certification.ManagerCertificationPartitioner.Input;
import sailpoint.api.certification.ManagerCertificationPartitioner.ManagerCertificationPartition;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.request.ManagerCertificationPartitionRequestExecutor;
import sailpoint.request.ManagerCertificationPartitionRequestExecutor.FinishPhaseExecutor;
import sailpoint.request.ManagerCertificationPartitionRequestExecutor.Phase;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * 
 * Will generate manager certification based on
 * partitions.
 * 
 * This is modeled after {@link PartitionedTaskSimulator}
 * 
 * @author Tapash Majumder
 *
 */
public class PartitionedManagerCertificationTaskExecutor extends AbstractTaskExecutor {

    private static final Log log = LogFactory.getLog(PartitionedManagerCertificationTaskExecutor.class);
    
    private SailPointContext context;
    private CertificationDefinition definition;
    private TaskResult result;
    // when set to true, means we are terminating so should not proceed further.
    private boolean terminate;
    
    private Attributes<String,Object> args;
    private TaskSchedule schedule;
    
    // person who kicked off the certification.
    private String requestorName;
    // the delegate class which handles how to partition.
    private ManagerCertificationPartitioner partitioner;
    // The requestdefinition for this task.
    private RequestDefinition requestDefinition;
    // The CertificationGroup for this cert.
    private CertificationGroup certificationGroup;
    // helper class 
    private ManagerCertificationHelper helper;
    
    public static final String REQUEST_DEFINITION_NAME = "Manager Certification Generation Partition";
    public static final String RET_CERT_GROUP_ID = "certGroupId";

    /**
     * This is the starting point. See {@link AbstractTaskExecutor#execute(SailPointContext, TaskSchedule, TaskResult, Attributes)}.
     */
    @Override
    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args) throws Exception {

        this.context = context;
        this.args = args;
        this.schedule = schedule;
        this.result = result;

        if (result.getId() == null) {
            throw new GeneralException("TaskResult not saved!");
        }
        
        if (null != this.schedule) {
            this.requestorName = this.schedule.getLauncher();
        }
        
        parseArguments();
        
        requestDefinition = context.getObjectByName(RequestDefinition.class, REQUEST_DEFINITION_NAME);
        if (requestDefinition == null) {
            throw new GeneralException("Could not find request definition: " + REQUEST_DEFINITION_NAME);
        }

        createCertificationGroup();
        
        result.setAttribute(RET_CERT_GROUP_ID, certificationGroup.getId());
        
        execute();
    }
    
    /**
     * See {@link AbstractTaskExecutor#terminate()}.
     */
    @Override
    public boolean terminate() {

        terminate = true;
        
        partitioner.getInput().setTerminate(true);
        
        return terminate;
    }

    /**
     * The public execute method above sets up all the parameters.
     * This method will proceed with creating and launching
     * the partitions.
     */
    private void execute() throws GeneralException {

        Meter.enterByName(getClass().getSimpleName() + ".execute");
        
        Input input = new Input();

        input.setContext(context);
        input.setCertificationDefinition(definition);
        input.setCertificationGroupId(certificationGroup.getId());
        input.setRequestorName(requestorName);
        input.setNumPartitions(calculateNumberOfPartitions());
        
        partitioner = new ManagerCertificationPartitioner(input);

        List<ManagerCertificationPartition> partitions = partitioner.createPartitions();
        
        Meter.enterByName(getClass().getSimpleName() + ".createRequests");
        
        List<Request> requests = createCertGeneratorRequests(partitions);
        
        requests.addAll(createParentRelationshipsRequests(partitions));
        
        requests.addAll(createStartCertsRequests(partitions));
        
        requests.add(createFinishRequest());
        
        result.setProgress("Launched " + Util.itoa(requests.size()) + " partitions.");
        
        if (log.isInfoEnabled()) {
            log.info("Launching " + Util.itoa(requests.size()) + " requests...");
        }

        Meter.exitByName(getClass().getSimpleName() + ".createRequests");

        if (!terminate) {
            launchPartitions(context, result, requests);
        }

        Meter.exitByName(getClass().getSimpleName() + ".execute");
    }

    // gets a unique name for the request
    private String getRequestName(Phase phase, int number) {

        return certificationGroup.getName() + "_" + phase.name() + "_Partition_" + number;
    }

    // gets a unique name for the request
    private String getRequestName(Phase phase) {

        return certificationGroup.getName() + "_" + phase.name();
    }
    
    // first it will look at the task arguments for numberOfPartitions,
    // then it will look at requestdefinition arguments.
    // failing that it will get a suggested partition count from the 
    // base class.
    private int calculateNumberOfPartitions() throws GeneralException {

        int numPartitions = args.getInt(ManagerCertificationPartitionRequestExecutor.ARG_NUM_PARTITIONS);
        if (numPartitions == 0) {
            numPartitions = requestDefinition.getInt(ManagerCertificationPartitionRequestExecutor.ARG_NUM_PARTITIONS);
        }
        if (numPartitions == 0) {
            numPartitions = getSuggestedPartitionCount(context, false, REQUEST_DEFINITION_NAME); 
        }
        return numPartitions;
    }
    
    /**
     * The first phase is to generate the certs.
     */
    private List<Request> createCertGeneratorRequests(List<ManagerCertificationPartition> partitions)
            throws GeneralException {

        List<Request> requests = new ArrayList<Request>();

        int number = 1;
        for (ManagerCertificationPartition partition : partitions) {
            Request request = new Request(requestDefinition);
            request.setName(getRequestName(Phase.GenerateCerts, number));
            request.setPhase(ManagerCertificationPartitionRequestExecutor.Phase.GenerateCerts.getNumber());
            copyArguments(request);
            request.put(ManagerCertificationPartitionRequestExecutor.ARG_PARTITION, partition);
            request.put(ManagerCertificationPartitionRequestExecutor.ARG_CERT_GROUP_ID, certificationGroup.getId());
            requests.add(request);
            ++number;
        }
        
        return requests;
    }

    private List<Request> createParentRelationshipsRequests(List<ManagerCertificationPartition> partitions)
            throws GeneralException {

        List<Request> requests = new ArrayList<Request>();

        int number = 1;
        for (ManagerCertificationPartition partition : partitions) {
            Request request = new Request(requestDefinition);
            request.setName(getRequestName(Phase.CreateParentRelationships, number));
            request.setPhase(ManagerCertificationPartitionRequestExecutor.Phase.CreateParentRelationships.getNumber());
            copyArguments(request);
            request.put(ManagerCertificationPartitionRequestExecutor.ARG_PARTITION, partition);
            request.put(ManagerCertificationPartitionRequestExecutor.ARG_CERT_GROUP_ID, certificationGroup.getId());
            requests.add(request);
            ++number;
        }
        
        return requests;
    }

    private List<Request> createStartCertsRequests(List<ManagerCertificationPartition> partitions)
        throws GeneralException {
    
        List<Request> requests = new ArrayList<Request>();
        
        int number = 1;
        for (ManagerCertificationPartition partition : partitions) {
            Request request = new Request(requestDefinition);
            request.setName(getRequestName(Phase.StartCerts, number));
            request.setPhase(ManagerCertificationPartitionRequestExecutor.Phase.StartCerts.getNumber());
            copyArguments(request);
            request.put(ManagerCertificationPartitionRequestExecutor.ARG_PARTITION, partition);
            request.put(ManagerCertificationPartitionRequestExecutor.ARG_CERT_GROUP_ID, certificationGroup.getId());
            requests.add(request);
            ++number;
        }
        
        return requests;
    }
    
    /**
     * The final phase updates statictics etc.
     * See {@link FinishPhaseExecutor} for more details.
     */
    private Request createFinishRequest() throws GeneralException {

        Request request = new Request(requestDefinition);
        request.setName(getRequestName(Phase.Finish));
        request.setPhase(ManagerCertificationPartitionRequestExecutor.Phase.Finish.getNumber());
        request.put(ManagerCertificationPartitionRequestExecutor.ARG_CERT_GROUP_ID, certificationGroup.getId());
        copyArguments(request);
        
        return request;
    }
    
    /**
     * parses arguments and creates CertificationDefinition
     * and others.
     */
    private void parseArguments()
            throws GeneralException {

        // This is usually on the definition.
        CertificationScheduler scheduler = new CertificationScheduler(context);

        try {
            definition = scheduler.getCertificationDefinition(schedule);
        } catch (IllegalArgumentException e) {
            // This allows having the definition ID specified in the
            // certification definition. We're supporting this only for the
            // scale data and demo data setup where we want to launch a task
            // immediately to create the certification without having to use
            // a schedule.
            if (null != args) {
                String defId = args.getString(CertificationSchedule.ARG_CERTIFICATION_DEFINITION_ID);
                if (null != defId) {
                    //TODO: This was named ID, but we now set it as name?! -rap
                    definition = context.getObjectByName(CertificationDefinition.class, defId);
                }
            }

            if (null == definition) {
                throw e;
            }

            if (definition.getCertificationNameTemplate() == null) {
                definition.setCertificationNameTemplate(definition.getName());
            }

            if (definition.getCertificationOwner() == null) {
                definition.setOwner(definition.getOwner());
            }
        }
    }
    
    /**
     * Copies arguments from taskdefinition and
     * requestdefinition to request.
     */
    private void copyArguments(Request request) {

        if (request.getAttributes() == null) {
            request.setAttributes(new Attributes<String, Object>());
        }
        request.getAttributes().putAll(args);
        
        request.getAttributes().putAll(requestDefinition.getArguments());
    }
    
    /**
     * Creates a CertificationGroup which is tie together all the certs generated
     * by this task.
     */
    private void createCertificationGroup() throws GeneralException{

        String name = new CertificationNamer(context).render(definition.getCertificationNameTemplate());
        
        ManagerCertificationHelper.Input input = new ManagerCertificationHelper.Input();
        input.setCertificationDefinition(definition);
        input.setContext(context);
        helper = new ManagerCertificationHelper(input);

        certificationGroup = helper.createCertificationGroup(name, schedule);
        context.saveObject(certificationGroup);
        context.commitTransaction();
    }
}
