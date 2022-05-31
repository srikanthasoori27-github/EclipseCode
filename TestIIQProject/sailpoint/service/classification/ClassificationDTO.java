/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.classification;

import sailpoint.object.Classification;
import sailpoint.web.UserContext;

/**
 * DTO representation of {@link Classification} to be sent to the front end
 * @author brian.li
 *
 */
public class ClassificationDTO {

    // displayable name for a Classification to be used for the data table
    private String displayableName;
    // Localized description
    private String description;
    // Classification object name
    private String name;
    // Classification origin
    private String origin;

    public ClassificationDTO(Classification c, UserContext uc) {
        this.setDisplayableName(c.getDisplayableName());
        this.setDescription(c, uc);
        this.setName(c.getName());
        this.setOrigin(c.getOrigin());
    }

    public String getDisplayableName() {
        return this.displayableName;
    }

    public void setDisplayableName(String displayableName) {
        this.displayableName = displayableName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(Classification c, UserContext uc) {
        this.description = c.getDescription(uc.getLocale());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrigin() { return origin; }

    public void setOrigin(String origin) { this.origin = origin; }

}
