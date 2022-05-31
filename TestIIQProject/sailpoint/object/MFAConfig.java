/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.List;
import java.util.Objects;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

@SuppressWarnings("serial")
public class MFAConfig extends AbstractXmlObject {
    private boolean enabled;
    private Workflow workflow;
    private List<DynamicScope> populations;

    @XMLProperty
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE, xmlname="MFAWorkflow")
    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST, xmlname="MFAPopulations")
    public List<DynamicScope> getPopulations() {
        return populations;
    }

    public void setPopulations(List<DynamicScope> populations) {
        this.populations = populations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MFAConfig mfaConfig = (MFAConfig) o;
        return enabled == mfaConfig.enabled &&
                Util.nullSafeEq(workflow, mfaConfig.workflow, true) &&
                Util.nullSafeEq(populations, mfaConfig.populations, true);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, workflow, populations);
    }
}
