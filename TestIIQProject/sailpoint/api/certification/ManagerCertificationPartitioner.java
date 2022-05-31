/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * This will take a manager cert definition and create a partition for each manager.
 * 
 * @author tapash.majumder
 *
 */
public class ManagerCertificationPartitioner  {

    private static Log log = LogFactory.getLog(ManagerCertificationPartitioner.class);

    // Encapsulates the input for the partitioner.
    private Input input;
   
    /**
     * The following are extracted from certification definition.
     */
    // should generate for the hierarchy?
    private boolean generateSubordinateCerts;
    // should flatten the hierarchy
    private boolean flatten;

    // list of partitions being created.
    private List<ManagerCertificationPartition> partitions;
    
    // The current partition that is being worked on
    private ManagerCertificationPartition currentPartition;
    
    // a master list of manager context information
    // each will turn into a certification.
    private List<ManagerCertificationPartition.OneManagerInfo> partitionManagerInfos;
    
    // calculated value depending on number of partitions.
    private int managersPerPartition;
    
    /**
     * Encapsulates the input required for the partitioner.
     *
     */
    public static class Input {
        
        private SailPointContext context;
        // the certification requestor
        private String requestorName;
        // the cert group
        private String certificationGroupId;
        // cert definition.
        private CertificationDefinition certificationDefinition;
        // managers per partition.
        // number of partitions for the managers.
        private int numPartitions;
        // when set to true means proceed no further
        private boolean terminate;
        
        public Input() {
        }
        
        public SailPointContext getContext() {
        
            return context;
        }
        
        public void setContext(SailPointContext context) {
        
            this.context = context;
        }
        
        public String getRequestorName() {
        
            return requestorName;
        }
        
        public void setRequestorName(String requestorName) {
        
            this.requestorName = requestorName;
        }
        
        public String getCertificationGroupId() {
        
            return certificationGroupId;
        }
        
        public void setCertificationGroupId(String certificationGroupId) {
        
            this.certificationGroupId = certificationGroupId;
        }
        
        public CertificationDefinition getCertificationDefinition() {
        
            return certificationDefinition;
        }
        
        public void setCertificationDefinition(CertificationDefinition certificationDefinition) {
        
            this.certificationDefinition = certificationDefinition;
        }

        public int getNumPartitions() {
        
            return numPartitions;
        }

        public void setNumPartitions(int numPartitions) {
        
            this.numPartitions = numPartitions;
        }
        
        public boolean isTerminate() {
            return terminate;
        }
        
        public void setTerminate(boolean val) {
            terminate = val;
        }
    }
    
    public ManagerCertificationPartitioner(Input input) throws GeneralException {
        
        if (log.isInfoEnabled()) {
            log.info("ManagerCertificationPartitioner");
        }
        
        this.input = input;
        
        init();
    }

    // initializes before creating partitions
    private void init() throws GeneralException {

        if (input.getCertificationDefinition() == null) {
            throw new IllegalStateException("input not set properly.");
        }
        
        generateSubordinateCerts = input.getCertificationDefinition().isSubordinateCertificationEnabled();
        flatten = input.getCertificationDefinition().isFlattenManagerCertificationHierarchy();
        
        if (log.isInfoEnabled()) {
            log.info("generateSubordinateCerts: " + generateSubordinateCerts);
            log.info("flatten: " + flatten);
        }
    }
    
    public Input getInput() {
        
        return input;
    }
    
    /**
     * Calling this method will invoke periodic decaches. 
     * @return A list of Partitions depending on the input set in the constructor.
     * 
     * @throws GeneralException
     */
    public List<ManagerCertificationPartition> createPartitions() throws GeneralException {
        
        Meter.enterByName(getClass().getSimpleName() +  ".createPartitions");
        
        createPartitionManagerInfos();
        
        calculateManagersPerPartition();
        
        partitions = new ArrayList<ManagerCertificationPartition>();
        
        for (ManagerCertificationPartition.OneManagerInfo info : partitionManagerInfos) {
            addManagerInfoToPartition(info);
        }
        
        Meter.exitByName(getClass().getSimpleName() +  ".createPartitions");

        return partitions;
    }

    // calculate managersPerPartition based on number of partitions.
    private void calculateManagersPerPartition() {

        if (input.getNumPartitions() == 0) {
            if (log.isInfoEnabled()) {
                log.info("numPartitions == 0, setting numPartitions to 1");
            }
            input.setNumPartitions(1);
        }

        managersPerPartition = partitionManagerInfos.size() / input.getNumPartitions();
        if ((partitionManagerInfos.size() % input.getNumPartitions()) > 0) {
            ++managersPerPartition;
        }
        if (managersPerPartition == 0) {
            managersPerPartition = 1;
        }
        if (log.isInfoEnabled()) {
            log.info("size: " + partitionManagerInfos.size() + ", managersPerPartition: " + managersPerPartition + ", numPartitions: " + input.getNumPartitions());
        }
    }

    /**
     * Will populate the partitionManagerInfos array with 
     * all managers information.
     * 
     */
    private void createPartitionManagerInfos() throws GeneralException {

        partitionManagerInfos = new ArrayList<ManagerCertificationPartition.OneManagerInfo>();
        
        ManagerCertificationHelper.Input helperInput = new ManagerCertificationHelper.Input();
        helperInput.setCertificationDefinition(input.getCertificationDefinition());
        helperInput.setContext(input.getContext());
        ManagerCertificationHelper helper = new ManagerCertificationHelper(helperInput);
        Iterator<String> managersNamesIterator = helper.fetchFirstLevelManagersIterator();
        // A running tally of Identities fetched. addPartitionManager will decache as it
        // increments
        int numFetched = 0;
        while (managersNamesIterator.hasNext() && !input.isTerminate()) {
            String theManagerName = managersNamesIterator.next();
            numFetched = addPartitionManagerInfoRecursive(theManagerName, null, numFetched);
        }
    }
    
    /**
     * Populates the partitionManagerInfos array recursively
     * for theManagerName
     */
    private int addPartitionManagerInfoRecursive(String theManagerName, String parentManagerName, int numFetched) throws GeneralException {
        
        partitionManagerInfos.add(new ManagerCertificationPartition.OneManagerInfo(theManagerName, parentManagerName));
        
        // now recurse through children looking for more partitions.
        Iterator<Identity> populationIterator = fetchPopulation(theManagerName);
        final int decacheInterval = 100;
        while (populationIterator.hasNext()) {
            Identity certEntityIdentity = populationIterator.next();
            ++numFetched;
            if (numFetched % decacheInterval == 0) {
                input.getContext().decache();
            }
                
            if (generateSubordinateCerts && certEntityIdentity.getManagerStatus()) {
                if (theManagerName.equals(certEntityIdentity.getName())) {
                    if (log.isWarnEnabled()) {
                        log.warn("Detected circular manager relationship between '" +
                             theManagerName + "' and '" + certEntityIdentity.getName() +
                             "'.  Not generating subordinate certification.");
                    }
                }
                else {
                    numFetched = addPartitionManagerInfoRecursive(certEntityIdentity.getName(), theManagerName, numFetched);
                }
            }
        }
        return numFetched;
    }
    
    // add one certification context to partition.
    // more than 1 can be added to a partition depending on number of partitions.
    private void addManagerInfoToPartition(ManagerCertificationPartition.OneManagerInfo info) throws GeneralException {

        if (currentPartition == null || currentPartition.getManagerInfos().size() == managersPerPartition) {
            // new partition
            currentPartition = new ManagerCertificationPartition();
            partitions.add(currentPartition);
            currentPartition.setRequestorName(input.getRequestorName());
            currentPartition.setCertificationGroupId(input.getCertificationGroupId());
            currentPartition.setCertificationDefinitionId(input.getCertificationDefinition().getId());
        }
        
        // sanity check
        if (currentPartition.getManagerInfos().size() > managersPerPartition) {
            throw new IllegalStateException("Partition size problem, managersInPartition: " + currentPartition.getManagerInfos().size() + ", managersPerPartition: " + managersPerPartition);
        }
        
        currentPartition.getManagerInfos().add(info);
        
        if (log.isInfoEnabled()) {
            log.info("Adding manager: " + info.getManagerName() + " to partition: " + partitions.size());
        }
    }
    
    // fetch all identities which are sub-managers for the manager.
    private Iterator<Identity> fetchPopulation(String theManagerName) throws GeneralException {
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("manager.name", theManagerName));
        ops.setOrderBy("name");

        return new IncrementalIdentityIterator(input.getContext(), ops, flatten);
    }
    
    /**
     * Holds information regarding a manager certification partition.
     * 
     */
    @SuppressWarnings("serial")
	@XMLClass(xmlname="ManagerPartition")
    public static class ManagerCertificationPartition extends AbstractXmlObject {

        /**
         * Holds information for 1 manager in a partition (there could be more than 1 manager per partition)
         */
        @XMLClass(xmlname="ManagerInfo")
        public static class OneManagerInfo extends AbstractXmlObject {
        
            private String managerName;
            private String parentManagerName;
            
            // constructor used only by xml serialization.
            public OneManagerInfo() {
            }
            
            // default constructor
            public OneManagerInfo(String managerName, String parentManagerName) {
                this.managerName = managerName;
                this.parentManagerName = parentManagerName;
            }
            
            @XMLProperty
            public String getManagerName() {
            
                return managerName;
            }
            
            public void setManagerName(String managerName) {
            
                this.managerName = managerName;
            }
            
            @XMLProperty
            public String getParentManagerName() {
            
                return parentManagerName;
            }
            
            public void setParentManagerName(String parentManagerName) {
            
                this.parentManagerName = parentManagerName;
            }
        }

        private String requestorName;
        private String certificationGroupId;
        private String certificationDefinitionId;
        
        private List<ManagerCertificationPartition.OneManagerInfo> managerInfos = new ArrayList<ManagerCertificationPartition.OneManagerInfo>();
        
        public ManagerCertificationPartition() {
        }
        
        @XMLProperty
        public String getRequestorName() {
            return requestorName;
        }
        
        public void setRequestorName(String val) {
            requestorName = val;
        }
        
        @XMLProperty
        public String getCertificationDefinitionId() {
        
            return certificationDefinitionId;
        }

        public void setCertificationDefinitionId(String val) {
            
            this.certificationDefinitionId = val;
        }
        
        @XMLProperty
        public String getCertificationGroupId() {
            return certificationGroupId;
        }
        
        public void setCertificationGroupId(String val) {
            certificationGroupId = val;
        }
        
        
        @XMLProperty(mode=SerializationMode.LIST, xmlname="ManagerInfos")
        public List<ManagerCertificationPartition.OneManagerInfo> getManagerInfos() {
            return managerInfos;
        }
        
        public void setManagerInfos(List<ManagerCertificationPartition.OneManagerInfo> val) {
            managerInfos = val;
        }
        
        @Override
        public String toString() {
        
            return ToStringBuilder.reflectionToString(this);
        }
    }
}
