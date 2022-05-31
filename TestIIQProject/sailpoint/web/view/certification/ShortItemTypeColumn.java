/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.view.certification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.object.CertificationItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;

/**
 * Gets the shorter version of localized text for the given item type.
 *
 * @author patrick.jeong
 */
public class ShortItemTypeColumn extends CertificationItemColumn {
    public static final String COL_TYPE = "type";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = new ArrayList<>();
        cols.add(COL_TYPE);
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        CertificationItem.Type type = (CertificationItem.Type)row.get(COL_TYPE);
        return type != null ? Internationalizer.getMessage(type.getShortMessageKey(), getLocale()) : "";
    }

}
