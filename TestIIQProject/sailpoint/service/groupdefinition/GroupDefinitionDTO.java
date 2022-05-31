/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.groupdefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.tools.InvalidParameterException;
import sailpoint.web.util.FilterConverter;

/**
 * Representation of a population (GroupDefinition).  This is intended to replace the
 * list of Maps returned by src.sailpoint.web.group.GroupDefinitionListBean
 *
 * @author bernie.margolis@sailpoint.com
 */
public class GroupDefinitionDTO {
    private static Log log = LogFactory.getLog(GroupDefinition.class);
    
    private String name;
    
    private String description;

    private int numMembers;

    private List<String> applications;
    
    private List<String> applicationIds;

    /**
     * Name of this pouplation
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * This population's description
     */
    public String getDescription() {
        return description;
    }


    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * Number of Identities who belong to this population
     */
    public int getNumIpopMembers() {
        return numMembers;
    }


    public void setNumIpopMembers(int numMembers) {
        this.numMembers = numMembers;
    }

    /**
     * List of names of applications containing accounts owned by one or 
     * more Identities in this population 
     */
    public List<String> getApplications() {
        return applications;
    }


    public void setApplications(List<String> applications) {
        this.applications = applications;
    }

    /**
     * List of IDs of applications containing accounts owned by one or 
     * more Identities in this population 
     */
    public List<String> getApplicationIds() {
        return applicationIds;
    }


    public void setApplicationIds(List<String> applicationIds) {
        this.applicationIds = applicationIds;
    }

    public GroupDefinitionDTO(GroupDefinition population, SailPointContext context) 
        throws InvalidParameterException {
        if (population == null) {
            throw new InvalidParameterException("Cannot create a GroupDefinitionDTO from a null GroupDefinition");
        }
        name = population.getName();
        description = population.getDescription();
        initializeNumMembers(population, context);
        initializeApplications(population, context);
    }
    
    
    private void initializeNumMembers(GroupDefinition population, SailPointContext context) {
        QueryOptions populationQuery = new QueryOptions(population.getFilter());
        populationQuery.setDistinct(true);
        try {
            numMembers = context.countObjects(Identity.class, populationQuery);
        } catch (Throwable t) {
            log.error("The Population named " + population.getName() + " generated an error when attempting to count its members.  No members can be detected for it as a result.", t);

        }
    }
    
    private void initializeApplications(GroupDefinition population, SailPointContext context) {
        applications = new ArrayList<String>();
        applicationIds = new ArrayList<String>();
        
        Filter populationFilter = population.getFilter();
        // Expand the Identity filter so that we can apply it to the Link search below
        populationFilter = FilterConverter.convertFilter(populationFilter, Identity.class, "identity");
        QueryOptions options = new QueryOptions(populationFilter);
        options.setDistinct(true);
        options.setOrderBy("application.name");
        Iterator<Object[]> appInfos;
        
        try {
            appInfos = context.search(Link.class, options, Arrays.asList("application.id", "application.name"));
        } catch (Throwable t) {
            log.error("The Population named " + population.getName() + " generated an error when attempting to query for applications associated with its members.  No applications can be detected for it as a result.", t);
            appInfos = null;
        }
        
        if (appInfos != null) {
            while (appInfos.hasNext()) {
                Object[] next = appInfos.next();
                String appId = (String) next[0];
                String appName = (String) next[1];
                // If our population consists entirely of Identity IQ users who do not have any accounts
                // outside of Identity IQ (i.e. spadmin), we can get nulls in our result set.  We have to
                // guard against that situation here even if though doesn't seem to make sense --Bernie
                if (appId != null && appName != null) {
                    applicationIds.add(appId);
                    applications.add(appName);
                }
            }                        
        }
    }
}
