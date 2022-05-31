/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * A Comment represents a comment added to a work item.
 */
public class Comment extends AbstractXmlObject implements Localizable
{
    private String author;
    private String comment;
    private Date date;

    /**
     * Comparator that can be used to sort the comments by date.
     */
    public static final Comparator<Comment> SP_COMMENT_BY_DATE =
        new Comparator<Comment>() {
            public int compare(Comment comment1, Comment comment2) {
                Date date1 = comment1.getDate();
                if (date1 == null ) date1 = new Date();
                Date date2 = comment2.getDate();
                if (date2 == null ) date2 = new Date();
                return date1.compareTo(date2);
            }
        };

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public Comment()
    {
        super();
    }

    /**
     * Constructor.
     * 
     * @param  comment  The comment text.
     * @param  author   The name of the author.
     */
    public Comment(String comment, String author)
    {
        this();
        this.comment = comment;
        this.author = author;
        this.date = new Date();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    @XMLProperty
    public String getAuthor()
    {
        return author;
    }

    public void setAuthor(String author)
    {
        this.author = author;
    }

    @XMLProperty
    public String getComment()
    {
        return comment;
    }

    public void setComment(String comment)
    {
        this.comment = comment;
    }

    @XMLProperty
    public Date getDate()
    {
        return date;
    }

    public void setDate(Date date)
    {
        this.date = date;
    }

    public String toString() {
       return "From '" + this.author + "' on " + 
              this.date + " '" + this.comment + "'"; }

    public String getLocalizedMessage() {
        return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
    }

    public String getLocalizedMessage(Locale locale, TimeZone timezone) {
        Message msg = new Message(MessageKeys.COMMENT_TEXT, this.author, this.date, this.comment);
        return msg.getLocalizedMessage(locale, timezone);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // OBJECT OVERRIDES
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Comment))
            return false;
        if (this == o)
            return true;

        Comment c = (Comment) o;
        return new EqualsBuilder()
            .append(getAuthor(), c.getAuthor())
            .append(getComment(), c.getComment())
            .append(getDate(), c.getDate())
            .isEquals();
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder()
                      .append(getAuthor())
                      .append(getComment())
                      .append(getDate())
                      .toHashCode();
    }
}
