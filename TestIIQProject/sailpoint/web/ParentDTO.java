/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * BaseDTO extension that adds support for the maintenance
 * of a list of child DTOs.  
 * 
 * Author: Jeff
 * 
 * Many of our classes have at least one set of child components.  
 * For these we usually provide a UI paradign where you select a 
 * child for editing, make a copy of it, edit the copy, replace 
 * the original with the copy if you click Save.  This class 
 * provides the infrascture to do that.  You're not required to use 
 * it but you'll end up duplicating most of this.
 *
 * NOTE: For XML serialization the subclass still needs to define
 * XMLProperties to serialize the child lists.  Nothing down
 * here is serializble because the model is generic.
 * 
 */

package sailpoint.web;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class ParentDTO<E extends BaseDTO> extends BaseDTO
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ParentDTO.class);
    
    /**
     * List of child DTOs.
     */
    List<E> _children;

    /**
     * Uid of child DTO selected for editing.
     * This is typically posted by a grid component showing
     * all the children.
     */
    String _selectedChildId;

    /**
     * Copy of an object from the _children list being edited.
     * This is typically created by an "edit" action handler
     * after the _selectedChildId has been set.
     */
    E _childEdit;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ParentDTO() {
    }
    
    public ParentDTO(List<E> children) {
        _children = children;
    }

    /**
     * Subclasses are expected to provide their own XMLProperty accessors
     * for the child list with meaningful names.  Would be nice if we
     * could vary the XML wrapper element name in the subclass.
     */
    public List<E> getChildren() {
        return _children;
    }

    public void setChildren(List<E> children) {
        _children = children;
    }

    /**
     * This does have an XML serialization since the name isn't so important.
     */
    @XMLProperty
    public String getSelectedChildId() {
        return _selectedChildId;
    }

    public void setSelectedChildId(String id) {
        _selectedChildId = id;
    }

    /**
     * Subclasses are expected to provide their own XMLProperty accessors
     * for the child object with meaningful names.  Would be nice if we
     * could vary the XML wrapper element name in the subclass.
     */
    public E getChildEdit() {
        return _childEdit;
    }

    public void setChildEdit(E child) {
        _childEdit = child;
    }

    /**
     * Pseudo property for the UI grid.
     * Return the number of children in a property easy to use
     * from a Ext data source.
     */
    public int getChildCount() {
        return (_children != null) ? _children.size() : 0;
    }

    /**
     * Pseudo proeprty to resolve the selected child id to 
     * an actual child object.
     */
    public E getSelectedChild() {
        return find(_selectedChildId);
    }

    /**
     * Pseudo property to build a CSV of child display names.
     * The child subclass must overload getSummaryName if you
     * want to use this.
     */
    public String getChildSummary() {

        String summary = null;
        if (_children != null) {
            List<String> names = new ArrayList<String>();
            for (E child : _children) {
                String name = child.getUIName();
                if (name != null)
                    names.add(name);
            }
            summary = trim(Util.listToCsv(names));
        }
        return summary;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // List Maintenance
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Search for a child DTO by uid.
     */
    public E find(String uid) {
        E found = null;
        if (_children != null && uid != null) {
            for (E child : _children) {
                if (uid.equals(child.getUid())) {
                    found = child;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Search for a child DTO by UI name.
     * The children must overload the getUIName method.
     */
    public E findByName(String name) {
        E found = null;
        if (_children != null && name != null) {
            for (E child : _children) {
                if (name.equals(child.getUIName())) {
                    found = child;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Add an element to the child list.
     */
    public void add(E child) {
        if (child != null) {
            if (_children == null)
                _children = new ArrayList<E>();
            _children.add(child);
        }
    }

    /**
     * Remove an element from the child list.
     */
    public void remove(E child) {
        if (_children != null) {
            _children.remove(child);
        }
    }

    /**
     * Replace one child with another or add it to the
     * end of the list.  This is typically done when
     * commiting an edited object.
     */
    public void replace(E neu) {

        if (neu != null) {
            E old = null;
            if (_children != null) {
                for (int i = 0 ; i < _children.size() ; i++) {
                    E child = _children.get(i);
                    if (child.isClone(neu)) {
                        old = child;
                        _children.set(i, neu);
                        break;
                    }
                }
            }

            if (old == null)
                add(neu);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Begin an editing session for a selected child object.
     * Returns the _childEdit property to indiciate whether
     * one was selected.
     */
    public E editChildAction() {
        
        _childEdit = null;
        E child = getSelectedChild();
        if (child != null)
            _childEdit = xmlclone(child);
            
        return _childEdit;
    }

    /**
     * Cancel the editing of a child component.
     */
    public void cancelChildAction() {
        _childEdit = null;
    }

    /**
     * Commit an edited child component.
     */
    public void saveChildAction() {
        if (_childEdit != null) {
            replace(_childEdit);
            _childEdit = null;
        }
    }
    
    /**
     * Delete the selected child.
     */
    public void deleteChildAction() {
        E child = getSelectedChild();
        if (child != null)
            remove(child);
    }

}        
