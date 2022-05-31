package sailpoint.service.certification.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.object.Attributes;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Tag;
import sailpoint.tools.Util;

/**
 * Certification Definition DTO to represent the CertificationDefinition Object
 * This is contained in the CertificationScheduleDTO for now to mimic the behavior
 * of the first class object.
 *
 * @author brian.li
 *
 */
public class CertificationDefinitionDTO {

    /**
     * The name of the Certification Definition
     */
    private String name;

    /**
     * ID of the CertificationDefinition
     */
    private String id;

    /**
     * List of tag names
     */
    private List<String> tags;

    /**
     * Suggest representation of a selected assigned scope for the definition
     */
    private Map<String, Object> assignedScope;

    /**
     * Attributes map holding most of the settings for the definition
     */
    private Attributes<String, Object> attributes;

    public CertificationDefinitionDTO() {
        setAttributes(new Attributes<String, Object>());
    }
    
    /**
     * Constructor for CertificationDefinitionDTO object based on input map.
     * 
     * @param map Map<String, Object> containing data
     */
    @SuppressWarnings("unchecked")
    public CertificationDefinitionDTO(Map<String,Object> map) {
        setId(Util.getString(map, "id"));
        setName(Util.getString(map, "name"));
        setTags(Util.getStringList(map, "tags"));
        setAssignedScope((Map<String, Object>)Util.get(map, "assignedScope"));
        setAttributes(new Attributes<>((Map<String, Object>)Util.get(map, "attributes")));
    }

    /**
     * Construct for CertificationDefinitionDTO object based on CertificationDefinition object.
     * 
     * @param definition CertificationDefinition object
     */
    public CertificationDefinitionDTO(CertificationDefinition definition) {
        setName(definition.getName());
        setId(definition.getId());
        addTags(definition.getTags());
        setAttributes(new Attributes<>(definition.getAttributes()));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Attributes<String, Object> getAttributes() {
        return this.attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    public List<String> getTags() {
        return this.tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public void addTags(List<Tag> tags) {
        for (Tag tag : Util.iterate(tags)) {
            addTag(tag);
        }
    }

    public void addTag(Tag tag) {
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        this.tags.add(tag.getName());
    }

    /**
     * Suggest representation of a selected assigned scope for the definition
     */
    public Map<String, Object> getAssignedScope() {
        return assignedScope;
    }

    public void setAssignedScope(Map<String, Object> assignedScope) {
        this.assignedScope = assignedScope;
    }
}
