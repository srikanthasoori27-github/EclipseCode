/*
 * (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.object;

import java.util.Objects;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class ObjectClassification extends SailPointObject {

    /**
     * The hibernate id of the object that has access to this.
     *
     */
    private String _ownerId;

    /**
     * Type of object that has this target.
     * Class.simpleName of the SailPointObject
     */
    private String _ownerType;

    /**
     * Classification being referenced
     */
    private Classification _classification;

    /**
     * True if this classification is effective
     * Effective classifications can be promoted via Effective Access Index Task
     */
    private boolean _effective;

    /**
     * Source in which the classification was derived
     * UI/Aggregation/Rule/etc
     * TODO: Probably want a better name for this
     */
    private String _source;

    //TODO: Do we need an assignedDate, or will created work?


    @XMLProperty
    public String getOwnerId() {
        return _ownerId;
    }

    public void setOwnerId(String ownerId) {
        _ownerId = ownerId;
    }

    @XMLProperty
    public String getOwnerType() {
        return _ownerType;
    }

    public void setOwnerType(String ownerType) {
        _ownerType = ownerType;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE, xmlname="ReferencedClassification")
    public Classification getClassification() {
        return _classification;
    }

    public void setClassification(Classification classification) {
        _classification = classification;
    }

    @XMLProperty
    public boolean isEffective() {
        return _effective;
    }

    public void setEffective(boolean effective) {
        _effective = effective;
    }

    @XMLProperty
    public String getSource() {
        return _source;
    }

    public void setSource(String source) {
        _source = source;
    }

    /**
     * No Name for ObjectClassification
     * @return
     */
    @Override
    public boolean hasName() {
        return false;
    }

    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _ownerId, _ownerType, _classification, _effective, _source);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ObjectClassification that = (ObjectClassification) o;
        return _effective == that._effective &&
                Objects.equals(_ownerId, that._ownerId) &&
                Objects.equals(_ownerType, that._ownerType) &&
                _classification.equals(that._classification) &&
                Objects.equals(_source, that._source);
    }
}
