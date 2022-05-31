/**
 * 
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.IXmlEqualable;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @author peter.holcomb
 * 
 */
@XMLClass
public class WorkflowRegistry extends SailPointObject {

    private static final long serialVersionUID = 4346966214501370655L;
    public static final String DEFAULT_WORKFLOW_REG = "Workflow Registry";

    public static final String ATTR_ICONS = "icons";
    public static final String ATTR_APPROVAL_MODES = "approvalModes";

    public static final String WORKFLOW_ICON_START = "Start";
    public static final String WORKFLOW_ICON_STOP = "Stop";
    public static final String WORKFLOW_ICON_CATCHES = "Catches";

    /**
     * A list of template steps that can be loaded in the BPE under the template
     * section
     **/
    List<Workflow.Step> templates;

    /**
     * A list of objects that describe methods in the workflow libraries that
     * can be called from steps. Used to decorate the listboxes that are
     * displayed on the BPE when a step uses a "call" option
     */
    List<Callable> callables;

    /**
     * A list of types of workflows that are defined in the drop-down on the
     * workflow page that helps declare how the workflow should be configured on
     * the bpe (what libaries are available)
     */
    List<WorkflowType> types;

    /**
     * A bag of attributes that allows stuff to be stored on the registry
     * as needed.
     **/
    Attributes<String, Object> attributes;

    @XMLClass(xmlname = "WorkflowCallableType")
    public static enum CallableType {
        Initialization, Action
    }

    @XMLClass(xmlname = "WorkflowCallableLibrary")
    public static enum Library {
        Identity, Role, PolicyViolation, Approval, Group
    }

    @XMLClass(xmlname = "WorkflowType")
    public static class WorkflowType extends AbstractXmlObject implements
            IXmlEqualable<WorkflowType> {

        private static final long serialVersionUID = 4165118049536592362L;

        String name;
        /**
         * Whether this workflow type is LCM
         * Please note that the plan for the future is
         * to get rid of LCM/non-LCM distinction. This will not be needed at that point.
         */
        boolean lcm;
        String displayNameKey;
        String helpKey;
        List<Library> libraries;
        /**
         * CSV of stepLibraries. This will contain a list of step library names
         * that should be shown when editing a workflow through the BPE
         */
        String stepLibraries;

        public String getName() {
            return name;
        }

        public boolean isLCM() {
            return lcm;
        }

        @XMLProperty(xmlname = "lcm")
        public void setLCM(boolean val) {
            lcm = val;
        }

        @XMLProperty
        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayNameKey() {
            return displayNameKey;
        }

        @XMLProperty
        public void setDisplayNameKey(String displayNameKey) {
            this.displayNameKey = displayNameKey;
        }

        public String getHelpKey() {
            return helpKey;
        }

        @XMLProperty
        public void setHelpKey(String helpKey) {
            this.helpKey = helpKey;
        }

        public List<Library> getLibraries() {
            return libraries;
        }

        @XMLProperty
        public void setLibraries(List<Library> libraries) {
            this.libraries = libraries;
        }

        public String getStepLibraries() {
            return stepLibraries;
        }

        @XMLProperty
        public void setStepLibraries(String stepLibraries) {
            this.stepLibraries = stepLibraries;
        }

        public boolean contentEquals(WorkflowType other) {
            return this.equals(other);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof WorkflowType))
                return false;
            if (this == o)
                return true;

            WorkflowType type = (WorkflowType) o;
            return new EqualsBuilder().append(getName(), type.getName())
                    .append(getDisplayNameKey(), type.getDisplayNameKey())
                    .append(getLibraries(), type.getLibraries())
                    .append(getHelpKey(), type.getHelpKey()).isEquals();
        }
    }

    @XMLClass(xmlname = "WorkflowCallable")
    public static class Callable extends AbstractXmlObject implements
            IXmlEqualable<Callable> {
        private static final long serialVersionUID = 5261774931764930444L;

        /** The name of the method in the workflow library that can be called **/
        String name;

        /** A short description of what the method does **/
        String descriptionKey;

        /**
         * The type of the method that is called. Helps only show
         * initialization calls for areas in the workflow that focus on
         * initialization
         */
        CallableType type;

        /** The workflow library that this call belongs to **/
        Library library;

        /** A list of the required arguments for the method that is being called **/
        List<Argument> requiredArguments;

        public Callable() {
        }

        public String getName() {
            return name;
        }

        @XMLProperty
        public void setName(String name) {
            this.name = name;
        }

        public String getDescriptionKey() {
            return descriptionKey;
        }

        @XMLProperty
        public void setDescriptionKey(String description) {
            this.descriptionKey = description;
        }

        public CallableType getType() {
            return type;
        }

        @XMLProperty
        public void setType(CallableType type) {
            this.type = type;
        }

        public List<Argument> getRequiredArguments() {
            return requiredArguments;
        }

        @XMLProperty
        public void setRequiredArguments(List<Argument> requiredArguments) {
            this.requiredArguments = requiredArguments;
        }

        public Library getLibrary() {
            return library;
        }

        @XMLProperty
        public void setLibrary(Library library) {
            this.library = library;
        }

        public boolean contentEquals(Callable other) {
            return this.equals(other);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Callable))
                return false;
            if (this == o)
                return true;

            Callable callable = (Callable) o;
            return new EqualsBuilder()
                    .append(getName(), callable.getName())
                    .append(getDescriptionKey(), callable.getDescriptionKey())
                    .append(getLibrary(), callable.getLibrary())
                    .append(getType(), callable.getType())
                    .append(getRequiredArguments(),
                            callable.getRequiredArguments()).isEquals();
        }
    }

    public static WorkflowRegistry getInstance(Resolver ctx)
            throws GeneralException {
        WorkflowRegistry reg = ctx.getObjectByName(WorkflowRegistry.class,
                DEFAULT_WORKFLOW_REG);
        if (null == reg)
            reg = new WorkflowRegistry();

        return reg;
    }

    @XMLProperty(mode = SerializationMode.LIST, xmlname = "WorkflowTemplates")
    public List<Workflow.Step> getTemplates() {
        return templates;
    }

    public void setTemplates(List<Workflow.Step> templates) {
        this.templates = templates;
    }

    @XMLProperty(mode = SerializationMode.LIST, xmlname = "WorkflowCallables")
    public List<Callable> getCallables() {
        return callables;
    }

    public void setCallables(List<Callable> callables) {
        this.callables = callables;
    }

    public List<Callable> getCallables(String typeString) {
        List<Callable> callables = new ArrayList<Callable>();

        if (typeString != null && !typeString.equals("")) {
            CallableType type = CallableType.valueOf(typeString);
            if (callables != null && type != null) {
                for (Callable callable : this.callables) {
                    if (callable.getType() != null
                            && callable.getType().equals(type)) {
                        callables.add(callable);
                    }
                }
                return callables;
            }
        }

        return this.callables;
    }

    @XMLProperty(mode = SerializationMode.LIST, xmlname = "WorkflowTypes")
    public List<WorkflowType> getTypes() {
        return types;
    }

    public void setTypes(List<WorkflowType> types) {
        this.types = types;
    }

    public static final Comparator<Callable> CALLABLE_COMPARATOR = new Comparator<Callable>() {
        public int compare(Callable a1, Callable a2) {
            return a1.getName().compareToIgnoreCase(a2.getName());
        }
    };

    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Object getAttribute(String attributeName) {
        return (attributes != null) ? attributes.get(attributeName) : null;
    }
}
