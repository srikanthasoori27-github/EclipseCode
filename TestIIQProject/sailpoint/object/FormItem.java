/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An object describing an item that may appear in a Form.
 *
 * Jeff
 *
 * This allows Form and Section to contain an ordered list of
 * both Field and Section objects.
 *
 * Currently there are issues rendering nested sections, so we won't
 * see those for awhile.  
 *
 * Currently we're not expected to have top-level Fields not
 * wrapped in a Section so we won't see those either.
 *
 * Although we son't see combination lists in practice yet I want
 * to keep the option open, so here we are.
 * 
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;

@XMLClass
public interface FormItem
{
    /**
     * Bring in any referenced Hibernate objects so the form can be
     * detached from the session.
     */
    public void load();

    /**
     * A number that influences the ordering of fields and sections when
     * assembled into a form for presentation. The default is zero
     * which means the form assembler is free to put it anywhere,
     * normally they will be appended to the parent section.
     *
     * If a number is set, the form assembler will put fields
     * with a lower number above those with a higher number.
     * 
     * Best practices are still being developed, but one
     * built-in rule is that the plan compiler will assign 
     * a default priority of 10 to any fields that come from an
     * account creation template. In the typical case this will
     * make creation fields appear above fields that came from
     * role templates which is usually what you want. 
     *
     * Start by considering the range 1-9 for special system use, 
     * the range 10-19 for account creation templates and 20+ 
     * for role templates. These are simply guidelines, templates
     * writers are free to violate them.
     */
    public int getPriority();

}
