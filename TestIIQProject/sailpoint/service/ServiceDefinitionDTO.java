/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import sailpoint.object.ServiceDefinition;

public class ServiceDefinitionDTO extends BaseDTO {

    String name;

    String description;

    String executor;

    Integer interval;

    String hosts;

    public ServiceDefinitionDTO(ServiceDefinition def) {
        super(def.getId());
        setAttributes(def.getAttributes());
        name = def.getName();
        executor = def.getExecutor();
        interval = def.getInterval();
        hosts = def.getHosts();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public Integer getInterval() {
        return interval;
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

}
