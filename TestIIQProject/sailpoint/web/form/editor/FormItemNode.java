/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.form.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.ButtonDTO;
import sailpoint.web.FieldDTO;
import sailpoint.web.extjs.TreeNode;
import sailpoint.web.extjs.TreeNodeList;

/**
 * Creates TreeNodes based upon FormItem type
 * @author alevi.dcosta
 */
public class FormItemNode extends TreeNode {

    public static final String FORMITEM_TYPE = "formItemType";
    public static final String FORMITEM_SECTION = "section";
    public static final String FORMITEM_ROW = "row";
    public static final String FORMITEM_FIELD = "field";
    public static final String FORMITEM_BUTTON = "button";

    private static final String FORMITEM_PROPERTIES = "properties";

    /**
     * Builds a treenode for a section
     */
    public FormItemNode(SectionDTO section) {
        super(null,
              Util.isNotNullOrEmpty(section.getLabel()) ? section.getLabel() : section.getName(),
              null, false, true, null);
        loadExtensions(section);
    }

    /**
     * Builds a treenode for a Row
     */
    public FormItemNode(RowDTO row) {
        super(null, row.getText(), null, false, true, null);
        loadExtensions(row);
    }

    /**
     * Builds a treenode for a field
     */
    public FormItemNode(FieldDTO field) {
        super(null,
              Util.isNotNullOrEmpty(field.getDisplayName()) ? field.getDisplayName() : field.getName(),
              null, true);
        loadExtensions(field);
    }

    /**
     * Builds a treenode for a button
     */
    public FormItemNode(ButtonDTO button) {
        super(null, button.getName(), null, true);
        loadExtensions(button);
    }

    /**
     * Adds additional info at Tree node level depending upon objectType 
     * @param dto object of type BaseDTO
     */
    private void loadExtensions(BaseDTO dto) {
        Map<String,Object> extensions = new HashMap<String,Object>();
        if(dto instanceof SectionDTO) {
            extensions.put(FORMITEM_TYPE, FORMITEM_SECTION);
            extensions.put(FORMITEM_PROPERTIES, ((SectionDTO)dto).getJSON());
        }
        else if (dto instanceof RowDTO) {
            extensions.put(FORMITEM_TYPE, FORMITEM_ROW);
            extensions.put(FORMITEM_PROPERTIES, ((RowDTO) dto).getJSON());
        }
        else if(dto instanceof FieldDTO) {
            extensions.put(FORMITEM_TYPE, FORMITEM_FIELD);
            extensions.put(FORMITEM_PROPERTIES, ((FieldDTO)dto).getJSON());
        }
        else if(dto instanceof ButtonDTO) {
            extensions.put(FORMITEM_TYPE, FORMITEM_BUTTON);
            extensions.put(FORMITEM_PROPERTIES, ((ButtonDTO)dto).getJSON());
        }
        this.setExtensions(extensions);
    }

    /**
     * Adds children to the current Tree node (Section)
     * @param dtos list of baseDTOs [RowDTOs / FieldDTOs]
     */
    public void addChildren(List<BaseDTO> dtos) {
        List<TreeNode> list = new ArrayList<TreeNode>();
        for (BaseDTO dto : Util.iterate(dtos)) {
            if (dto instanceof RowDTO) {
                RowDTO row = (RowDTO) dto;
                FormItemNode rowNode = new FormItemNode(row);

                // Add column fields as children of rows
                List<TreeNode> columns = new ArrayList<TreeNode>();
                for (FieldDTO field : Util.safeIterable(row.getFieldDTOs())) {
                    columns.add(new FormItemNode(field));
                }
                rowNode.addChildren(new TreeNodeList(columns));

                list.add(rowNode);
            } else if (dto instanceof FieldDTO) {
                list.add(new FormItemNode((FieldDTO) dto));
            }
        }
        addChildren(new TreeNodeList(list));
    }
}
