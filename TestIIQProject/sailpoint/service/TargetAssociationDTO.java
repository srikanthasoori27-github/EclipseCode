package sailpoint.service;

import sailpoint.object.ColumnConfig;

import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 4/8/16.
 */
public class TargetAssociationDTO extends BaseDTO {


    public TargetAssociationDTO(String id) {
        super(id);
    }


    private String _hierarchy;
    private String _type;
    private String _targetType;
    private String _targetName;
    private String _rights;
    private String _applicationName;

    private List<String> classificationNames;

    private String _owningObjectId;

    private String _ownerType;

    public TargetAssociationDTO(Map<String, Object> row, List<ColumnConfig> cols) {
        super(row, cols);
    }

    public String getOwningObjectId() {
        return _owningObjectId;
    }

    public void setOwningObjectId(String _owningObjectId) {
        this._owningObjectId = _owningObjectId;
    }

    public String getHierarchy() {
        return _hierarchy;
    }

    public void setHierarchy(String _hierarchy) {
        this._hierarchy = _hierarchy;
    }

    public String getType() {
        return _type;
    }

    public void setType(String _type) {
        this._type = _type;
    }

    public String getTargetType() {
        return _targetType;
    }

    public void setTargetType(String _targetType) {
        this._targetType = _targetType;
    }

    public String getTargetName() {
        return _targetName;
    }

    public void setTargetName(String _targetName) {
        this._targetName = _targetName;
    }

    public String getRights() {
        return _rights;
    }

    public void setRights(String _rights) {
        this._rights = _rights;
    }

    public String getApplicationName() { return _applicationName; }

    public void setApplicationName(String s) { _applicationName = s; }

    public List<String> getClassificationNames() {
        return classificationNames;
    }

    public void setClassificationNames(List<String> classificationNames) {
        this.classificationNames = classificationNames;
    }

    public String getOwnerType() {
        return _ownerType;
    }

    public void setOwnerType(String _ownerType) {
        this._ownerType = _ownerType;
    }
}
