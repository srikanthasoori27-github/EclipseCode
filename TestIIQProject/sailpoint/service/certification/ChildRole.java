/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import sailpoint.object.Bundle;

/**
 * Class holding information about descendant roles for use in remediations
 */
public class ChildRole {

    private String relationshipType;
    private String name;
    private String displayableName;
    private String id;
    private boolean selected;
    //Do we need this here or should we add this when creating the plan? -rap
    private String assignmentId;


    public ChildRole(Bundle bundle, String relationshipType, boolean selected) {
        this.id = bundle.getId();
        this.name = bundle.getName();
        this.displayableName = bundle.getDisplayableName();
        this.selected = selected;
        this.relationshipType = relationshipType;
        this.assignmentId = bundle.getAssignmentId();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayableName() {
        return displayableName;
    }

    public void setDisplayableName(String displayableName) {
        this.displayableName = displayableName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }
}